package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.StorageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Оптимизированный слой доступа к данным:
 *
 * <ul>
 *   <li><b>кэш в памяти</b> — одно представление на ключ; повторные
 *       открытия GUI не читают диск;</li>
 *   <li><b>асинхронная предзагрузка</b> ({@link #preload}) — диск читается
 *       на выделенном I/O-потоке, основной поток не ждёт файловую систему;</li>
 *   <li><b>один выделенный поток записи</b> — весь ввод-вывод последователен
 *       и не конфликтует с основным потоком;</li>
 *   <li><b>коалесинг</b> — всплеск изменений (серия кликов) приводит
 *       к одной записи, а не к N;</li>
 *   <li><b>повтор записи</b> — ошибка ввода-вывода не теряет данные,
 *       запись ретраится через несколько секунд, плюс автосейв и
 *       синхронный flush при остановке сервера;</li>
 *   <li><b>атомарные записи</b> — уникальный временный файл + ATOMIC_MOVE,
 *       параллельные flush не повреждают файлы.</li>
 * </ul>
 */
public class StorageService {

    private static final long RETRY_DELAY_MS = 5000;
    private static final long SHUTDOWN_AWAIT_SECONDS = 8;

    private final CustomGuiReworked plugin;
    private final Map<StorageKey, StorageView> views = new ConcurrentHashMap<>();
    private final Map<StorageKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private final ScheduledExecutorService io;
    private final long coalesceMs;

    private final BlockStorageBackend blockBackend;
    private final StorageBackend playerBackend;
    private final StorageBackend globalBackend;
    private final StorageBackend teamBackend;

    private final int maxCachedViews;
    private BukkitTask autosaveTask;
    private volatile boolean shutdown;

    public StorageService(CustomGuiReworked plugin) {
        this.plugin = plugin;
        this.io = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "CguiStorageIO");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.coalesceMs = Math.max(0L, plugin.getConfig().getLong("storage.coalesce-ms", 1000L));
        File dataFolder = new File(plugin.getDataFolder(), "data");
        this.blockBackend = new BlockStorageBackend(plugin, dataFolder, io);
        this.playerBackend = new SimpleStorageBackend(plugin, StorageType.PERSONAL, new File(dataFolder, "players"), true);
        this.globalBackend = new SimpleStorageBackend(plugin, StorageType.GLOBAL, new File(dataFolder, "globals"), false);
        this.teamBackend = new SimpleStorageBackend(plugin, StorageType.TEAM, new File(dataFolder, "teams"), true);
        this.maxCachedViews = Math.max(100, plugin.getConfig().getInt("storage.max-cached-views", 10000));
        startAutosave();
    }

    // ================= чтение =================

    /**
     * Загружает данные ключа (из кэша или с диска).
     * Может содержать синхронный I/O — для открытия GUI используйте
     * {@link #preload}.
     *
     * @return массив payloadов
     */
    public String[] load(StorageKey key) {
        StorageView view = views.get(key);
        if (view == null) {
            evictIfNeeded();
            view = loadIntoCache(key);
        }
        view.touch();
        return view.slots;
    }

    private StorageView loadIntoCache(StorageKey key) {
        String[] data = new String[0];
        if (!shutdown) {
            StorageBackend backend = backend(key.type());
            if (backend != null) {
                try {
                    data = backend.read(key);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to read storage " + key.stringKey() + ": " + e.getMessage());
                }
            }
        }
        StorageView created = new StorageView(key, data);
        StorageView existing = views.putIfAbsent(key, created);
        return existing != null ? existing : created;
    }

    /**
     * Асинхронно прогревает кэш ключа (чтение с диска — на I/O-потоке),
     * после чего выполняет {@code ready} на основном потоке сервера.
     * Если ключ уже в кэше — {@code ready} выполняется сразу.
     */
    public void preload(StorageKey key, Runnable ready) {
        if (key.type() == StorageType.TEMPORARY || views.containsKey(key)) {
            ready.run();
            return;
        }
        io.execute(() -> {
            loadIntoCache(key);
            if (shutdown) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, ready);
        });
    }

    // ================= изменения (основной поток) =================

    /**
     * Обновляет один слот данных (быстрый путь для одиночных кликов —
     * сериализуется только один предмет, а не весь инвентарь).
     */
    public void updateSlot(StorageKey key, int slot, String payload) {
        StorageView view = getOrCreate(key);
        synchronized (view) {
            String[] current = view.slots;
            if (slot < 0) {
                return;
            }
            if (slot >= current.length) {
                String[] bigger = new String[slot + 1];
                System.arraycopy(current, 0, bigger, 0, current.length);
                for (int i = current.length; i < bigger.length; i++) {
                    bigger[i] = "";
                }
                bigger[slot] = payload == null ? "" : payload;
                current = bigger;
            } else {
                current = current.clone();
                current[slot] = payload == null ? "" : payload;
            }
            view.slots = current;
            view.markDirty();
            view.touch();
        }
        scheduleSave(key);
    }

    /** Обновляет весь массив данных (запись через API {@code writeStorage}). */
    public void update(StorageKey key, String[] slots) {
        StorageView view = getOrCreate(key);
        synchronized (view) {
            view.slots = slots == null ? new String[0] : slots;
            view.markDirty();
            view.touch();
        }
        scheduleSave(key);
    }

    /**
     * Гарантирует запись без коалесинг-паузы (закрытие GUI, выход игрока):
     * отменяет отложенную задачу и ставит запись в начало I/O-очереди.
     */
    public void saveNow(StorageKey key) {
        StorageView view = views.get(key);
        if (view == null) {
            return;
        }
        boolean dirty;
        synchronized (view) {
            dirty = view.dirty;
        }
        if (!dirty) {
            return;
        }
        ScheduledFuture<?> future = pending.remove(key);
        if (future != null) {
            future.cancel(false);
        }
        if (!shutdown) {
            io.execute(() -> flushOne(key));
        }
    }

    /** Удаляет данные с диска и из кэша (асинхронно). */
    public void delete(StorageKey key) {
        views.remove(key);
        ScheduledFuture<?> future = pending.remove(key);
        if (future != null) {
            future.cancel(false);
        }
        if (shutdown) {
            return;
        }
        StorageBackend backend = backend(key.type());
        if (backend == null) {
            return;
        }
        io.execute(() -> {
            try {
                backend.remove(key);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to delete storage " + key.stringKey() + ": " + e.getMessage());
            }
        });
    }

    /** Отбрасывает кэшированные представления блока (после его разрушения). */
    public void removeBlockCache(String owner) {
        views.keySet().removeIf(key -> key.type() == StorageType.BLOCK && key.owner().equals(owner));
    }

    // ================= служебное =================

    public BlockStorageBackend blockBackend() {
        return blockBackend;
    }

    /** Сохраняет все личные данные игрока (при выходе). */
    public void saveAllForPlayer(Player player) {
        String name = player.getName();
        for (StorageKey key : views.keySet()) {
            if (key.type() == StorageType.PERSONAL && key.owner().equals(name)) {
                saveNow(key);
            }
        }
    }

    /**
     * Полный синхронный flush всех данных. Вызывается при выключении
     * плагина: дожидаемся текущей фоновой записи, затем дописываем
     * всё оставшееся на текущем потоке.
     */
    public void flushAll() {
        stopAutosave();
        shutdown = true;
        // Отменяем отложенные коалесинг-задачи — всё грязное будет
        // записано ниже синхронно.
        for (ScheduledFuture<?> future : pending.values()) {
            future.cancel(false);
        }
        pending.clear();
        io.shutdown();
        try {
            if (!io.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Storage I/O thread did not finish in "
                        + SHUTDOWN_AWAIT_SECONDS + "s — flushing synchronously");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        IOException backendFailure = null;
        for (StorageKey key : views.keySet()) {
            StorageView view = views.get(key);
            if (view == null) {
                continue;
            }
            String[] snapshot;
            synchronized (view) {
                if (!view.dirty) {
                    continue;
                }
                snapshot = view.slots;
            }
            StorageBackend backend = backend(key.type());
            try {
                if (backend == null) {
                    // TEMPORARY — писать некуда, это не ошибка
                    synchronized (view) {
                        view.dirty = false;
                    }
                    continue;
                }
                backend.write(key, snapshot);
                synchronized (view) {
                    view.dirty = false;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to flush storage " + key.stringKey() + ": " + e.getMessage());
                if (backendFailure == null && e instanceof IOException ioException) {
                    backendFailure = ioException;
                }
            }
        }
        for (StorageBackend backend : new StorageBackend[]{blockBackend, playerBackend, globalBackend, teamBackend}) {
            try {
                backend.flushAll();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to flush backend " + backend.type() + ": " + e.getMessage());
                if (backendFailure == null) {
                    backendFailure = e;
                }
            }
        }
        if (backendFailure != null) {
            plugin.getLogger().severe("Some storage data could not be saved during shutdown: "
                    + backendFailure.getMessage());
        }
    }

    private StorageView getOrCreate(StorageKey key) {
        return views.computeIfAbsent(key, k -> new StorageView(k, null));
    }

    /** Коалесинг: откладывает запись на {@code coalesce-ms}; повторные
     * изменения в пределах паузы схлопываются. */
    private void scheduleSave(StorageKey key) {
        if (shutdown) {
            return;
        }
        pending.computeIfAbsent(key, k -> io.schedule(() -> flushOne(k), coalesceMs, TimeUnit.MILLISECONDS));
    }

    /** Запись одного ключа на I/O-потоке (с повтором при изменении во время записи). */
    private void flushOne(StorageKey key) {
        pending.remove(key);
        StorageView view = views.get(key);
        if (view == null) {
            return;
        }
        StorageBackend backend = backend(key.type());
        int iterations = 0;
        while (true) {
            String[] snapshot;
            long version;
            synchronized (view) {
                if (!view.dirty) {
                    return;
                }
                snapshot = view.slots;
                version = view.version;
                view.dirty = false;
            }
            if (backend != null) {
                try {
                    backend.write(key, snapshot);
                } catch (Exception e) {
                    synchronized (view) {
                        view.dirty = true;
                    }
                    plugin.getLogger().severe("Failed to save storage " + key.stringKey() + ": " + e.getMessage()
                            + " (retry in " + (RETRY_DELAY_MS / 1000) + "s)");
                    if (!shutdown) {
                        pending.computeIfAbsent(key, k ->
                                io.schedule(() -> flushOne(k), RETRY_DELAY_MS, TimeUnit.MILLISECONDS));
                    }
                    return;
                }
            } else {
                // TEMPORARY не персистится — просто сбрасываем флаг
                synchronized (view) {
                    view.dirty = false;
                }
                return;
            }
            synchronized (view) {
                if (view.version == version || ++iterations >= 8) {
                    if (iterations >= 8 && view.dirty) {
                        scheduleSave(key);
                    }
                    return;
                }
                // Данные изменились во время записи — немедленный повтор
            }
        }
    }

    private void startAutosave() {
        int ticks = plugin.getConfig().getInt("storage.autosave-ticks", 600);
        if (ticks <= 0) {
            return;
        }
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (StorageKey key : views.keySet()) {
                StorageView view = views.get(key);
                if (view == null) {
                    continue;
                }
                boolean dirty;
                synchronized (view) {
                    dirty = view.dirty;
                }
                if (dirty) {
                    saveNow(key);
                }
            }
        }, ticks, ticks);
    }

    public void stopAutosave() {
        BukkitTask task = autosaveTask;
        if (task != null) {
            task.cancel();
            autosaveTask = null;
        }
    }

    private void evictIfNeeded() {
        if (views.size() < maxCachedViews) {
            return;
        }
        StorageView oldest = null;
        StorageKey oldestKey = null;
        for (Map.Entry<StorageKey, StorageView> entry : views.entrySet()) {
            StorageView view = entry.getValue();
            boolean dirty;
            synchronized (view) {
                dirty = view.dirty;
            }
            if (dirty) {
                continue;
            }
            if (oldest == null || view.lastUsed < oldest.lastUsed) {
                oldest = view;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            views.remove(oldestKey);
        }
    }

    private StorageBackend backend(StorageType type) {
        return switch (type) {
            case BLOCK -> blockBackend;
            case PERSONAL -> playerBackend;
            case GLOBAL -> globalBackend;
            case TEAM -> teamBackend;
            case TEMPORARY -> null;
        };
    }
}
