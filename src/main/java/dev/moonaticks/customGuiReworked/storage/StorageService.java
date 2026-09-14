package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.StorageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оптимизированный слой доступа к данным:
 *
 * <ul>
 *   <li><b>кэш в памяти</b> — одно представление на ключ; повторные
 *       открытия GUI не читают диск;</li>
 *   <li><b>асинхронные записи</b> — весь ввод-вывод происходит в
 *       фоновом потоке, основной поток только снимает снапшот;</li>
 *   <li><b>коалесинг</b> — всплеск изменений (серия кликов) приводит
 *       к одной записи, а не к N;</li>
 *   <li><b>периодический автосейв</b> грязных данных + надёжное
 *       сохранение при закрытии/выходе/остановке сервера;</li>
 *   <li><b>атомарные записи</b> — временный файл + move, данные не
 *       повреждаются при обрыве питания.</li>
 * </ul>
 */
public class StorageService {

    private final CustomGuiReworked plugin;
    private final Map<StorageKey, StorageView> views = new ConcurrentHashMap<>();
    private final Set<StorageKey> scheduled = ConcurrentHashMap.newKeySet();

    private final StorageBackend blockBackend;
    private final StorageBackend playerBackend;
    private final StorageBackend globalBackend;
    private final StorageBackend teamBackend;

    private final int maxCachedViews;
    private BukkitTask autosaveTask;

    public StorageService(CustomGuiReworked plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        this.blockBackend = new BlockStorageBackend(plugin, dataFolder);
        this.playerBackend = new SimpleStorageBackend(plugin, StorageType.PERSONAL, new File(dataFolder, "players"), true);
        this.globalBackend = new SimpleStorageBackend(plugin, StorageType.GLOBAL, new File(dataFolder, "globals"), false);
        this.teamBackend = new SimpleStorageBackend(plugin, StorageType.TEAM, new File(dataFolder, "teams"), true);
        this.maxCachedViews = Math.max(100, plugin.getConfig().getInt("storage.max-cached-views", 10000));
        startAutosave();
    }

    // ================= чтение =================

    /**
     * Загружает данные ключа (из кэша или с диска).
     *
     * @return массив payloadов
     */
    public String[] load(StorageKey key) {
        StorageView view = views.get(key);
        if (view == null) {
            evictIfNeeded();
            StorageBackend backend = backend(key.type());
            String[] data = backend == null ? new String[0] : backend.read(key);
            view = new StorageView(key, data);
            views.putIfAbsent(key, view);
            view = views.get(key);
        }
        view.touch();
        return view.slots;
    }

    // ================= изменения (основной поток) =================

    /**
     * Обновляет один слот данных (быстрый путь для одиночных кликов —
     * сериализуется только один предмет, а не весь инвентарь).
     */
    public void updateSlot(StorageKey key, int slot, String payload) {
        StorageView view = getOrCreate(key);
        String[] current = view.slots.clone();
        if (slot >= current.length) {
            String[] bigger = new String[slot + 1];
            System.arraycopy(current, 0, bigger, 0, current.length);
            for (int i = current.length; i < bigger.length; i++) {
                bigger[i] = "";
            }
            bigger[slot] = payload == null ? "" : payload;
            current = bigger;
        } else {
            current[slot] = payload == null ? "" : payload;
        }
        view.slots = current;
        view.markDirty();
        view.touch();
        scheduleSave(key);
    }

    /**
     * Обновляет весь массив данных (закрытие GUI, полный снапшот).
     */
    public void update(StorageKey key, String[] slots) {
        StorageView view = getOrCreate(key);
        view.slots = slots == null ? new String[0] : slots;
        view.markDirty();
        view.touch();
        scheduleSave(key);
    }

    /**
     * Гарантирует, что актуальные данные будут записаны
     * (используется при закрытии GUI и выходе игрока).
     */
    public void saveNow(StorageKey key) {
        StorageView view = views.get(key);
        if (view == null || !view.dirty) {
            return;
        }
        scheduleSave(key);
    }

    /** Удаляет данные с диска и из кэша. */
    public void delete(StorageKey key) {
        StorageBackend backend = backend(key.type());
        if (backend != null) {
            backend.remove(key);
        }
        views.remove(key);
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

    /** Синхронный flush всех dirty-данных (только при выключении сервера). */
    public void flushAll() {
        stopAutosave();
        for (StorageKey key : views.keySet()) {
            StorageView view = views.get(key);
            if (view == null || !view.dirty) {
                continue;
            }
            view.dirty = false;
            StorageBackend backend = backend(key.type());
            if (backend != null) {
                try {
                    backend.write(key, view.slots);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to flush storage " + key.stringKey() + ": " + e.getMessage());
                }
            }
        }
        blockBackend.flushAll();
        playerBackend.flushAll();
        globalBackend.flushAll();
        teamBackend.flushAll();
    }

    private StorageView getOrCreate(StorageKey key) {
        return views.computeIfAbsent(key, k -> new StorageView(k, null));
    }

    private void scheduleSave(StorageKey key) {
        if (!scheduled.add(key)) {
            return; // запись уже запланирована — коалесинг
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                flushOne(key);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save storage " + key.stringKey() + ": " + e.getMessage());
            } finally {
                scheduled.remove(key);
            }
        });
    }

    /** Асинхронная запись одного ключа (снимок снимается в фоновом потоке). */
    private void flushOne(StorageKey key) {
        StorageView view = views.get(key);
        if (view == null) {
            return;
        }
        long version = view.version;
        if (!view.dirty) {
            return;
        }
        view.dirty = false;
        StorageBackend backend = backend(key.type());
        if (backend != null) {
            backend.write(key, view.slots);
        }
        // Данные изменились во время записи — повторяем запись с новыми данными
        if (view.version != version) {
            view.dirty = true;
            scheduled.remove(key);
            scheduleSave(key);
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
                if (view != null && view.dirty) {
                    scheduleSave(key);
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
            if (view.dirty) {
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
