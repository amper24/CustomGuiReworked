package dev.moonaticks.customGuiReworked.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.codec.LegacyPayloads;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Блок-хранилище: файлы регионов
 * {@code <мир>/CustomGuiReworked/blocks/<x/16>_<z/16>.json}.
 *
 * <p>Новый формат (несколько таблиц могут сосуществовать в одном блоке):
 * <pre>
 * { "world:x,y,z": { "tables": { "shop.yml": ["n1:{...}", ""] } } }
 * </pre>
 *
 * <p>Старый формат {@code { "world:x,y,z": { "table": "...", "storage": [...] }}}
 * мигрируется при чтении (ключ таблицы сохраняется без изменений).
 *
 * <p>Регионы кешируются в памяти; доступ к региону сериализован локом;
 * запись регионов выполняется одним выделенным потоком с коалесингом
 * (серия изменений региона схлопывается в один файловый write через
 * {@link #REGION_DELAY_MS} мс) и атомарна (уникальный tmp + ATOMIC_MOVE).
 */
public class BlockStorageBackend implements StorageBackend, Listener {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int REGION_SHIFT = 4; // регион 16x16
    /** Задержка коалесинга записи региона (мс). */
    private static final long REGION_DELAY_MS = 500;
    /** Повторная попытка записи после ошибки ввода-вывода (мс). */
    private static final long RETRY_DELAY_MS = 5000;

    private final CustomGuiReworked plugin;
    private final File dataFolder;
    private final ScheduledExecutorService io;
    private final Map<String, RegionCache> regions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> regionWrites = new ConcurrentHashMap<>();

    private static final class RegionCache {
        final String key;
        final Object lock = new Object();
        final File file;
        JsonObject root = new JsonObject();
        boolean dirty;

        RegionCache(String key, File file) {
            this.key = key;
            this.file = file;
        }
    }

    public BlockStorageBackend(CustomGuiReworked plugin, File dataFolder, ScheduledExecutorService io) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.io = io;
    }

    @Override
    public StorageType type() {
        return StorageType.BLOCK;
    }

    /** Owner-строка для Location («world:x,y,z»). */
    public static String ownerKey(org.bukkit.Location location) {
        return location.getWorld().getName()
                + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private File regionFile(org.bukkit.Location location) {
        File worldFolder = new File(location.getWorld().getWorldFolder(), "CustomGuiReworked/blocks");
        int xr = location.getBlockX() >> REGION_SHIFT;
        int zr = location.getBlockZ() >> REGION_SHIFT;
        return new File(worldFolder, xr + "_" + zr + ".json");
    }

    private RegionCache regionFor(org.bukkit.Location location) {
        File file = regionFile(location);
        RegionCache cache = regions.get(file.getAbsolutePath());
        if (cache != null) {
            return cache;
        }
        cache = new RegionCache(file.getAbsolutePath(), file);
        loadRegion(cache);
        RegionCache existing = regions.putIfAbsent(file.getAbsolutePath(), cache);
        return existing != null ? existing : cache;
    }

    private void loadRegion(RegionCache cache) {
        synchronized (cache.lock) {
            // Подметаем осиротевшие tmp-файлы этого региона после краха.
            File parent = cache.file.getParentFile();
            if (parent != null) {
                String prefix = cache.file.getName() + ".tmp-";
                File[] leftovers = parent.listFiles((dir, name) -> name.startsWith(prefix));
                if (leftovers != null) {
                    for (File leftover : leftovers) {
                        if (!leftover.delete()) {
                            leftover.deleteOnExit();
                        }
                    }
                }
            }
            if (!cache.file.exists()) {
                cache.root = new JsonObject();
                return;
            }
            try {
                String content = Files.readString(cache.file.toPath(), StandardCharsets.UTF_8);
                cache.root = content.isBlank()
                        ? new JsonObject()
                        : JsonParser.parseString(content).getAsJsonObject();
                if (migrateRegion(cache.root)) {
                    cache.dirty = true;
                    scheduleRegionWrite(cache);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to read block region " + cache.file + ": " + e.getMessage());
                cache.root = new JsonObject();
            }
        }
    }

    /** Миграция старого формата региона. Возвращает true, если что-то изменилось. */
    private boolean migrateRegion(JsonObject root) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(root.entrySet())) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject block = entry.getValue().getAsJsonObject();
            boolean legacy = block.has("table")
                    || (block.has("storage") && block.get("storage").isJsonArray());
            if (legacy) {
                JsonObject tables = new JsonObject();
                String table = block.has("table") && block.get("table").isJsonPrimitive()
                        ? block.get("table").getAsString()
                        : "unknown";
                JsonArray storage = block.has("storage") && block.get("storage").isJsonArray()
                        ? block.getAsJsonArray("storage")
                        : null;
                tables.add(table, migrateArray(storage));
                block.remove("table");
                block.remove("storage");
                block.add("tables", tables);
                changed = true;
            } else if (block.has("tables") && block.get("tables").isJsonObject()) {
                JsonObject tables = block.getAsJsonObject("tables");
                for (Map.Entry<String, JsonElement> tableEntry : new ArrayList<>(tables.entrySet())) {
                    if (!tableEntry.getValue().isJsonArray()) {
                        continue;
                    }
                    JsonArray array = tableEntry.getValue().getAsJsonArray();
                    for (int i = 0; i < array.size(); i++) {
                        JsonElement element = array.get(i);
                        String original = (element != null && element.isJsonPrimitive() && !element.isJsonNull())
                                ? element.getAsString()
                                : null;
                        String migrated;
                        if (original == null || original.isBlank()) {
                            migrated = "";
                        } else if (element.isJsonPrimitive()) {
                            migrated = LegacyPayloads.migrate(original);
                        } else {
                            migrated = LegacyPayloads.migrate(element.toString());
                        }
                        if (!migrated.equals(original)) {
                            array.set(i, new JsonPrimitive(migrated));
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private JsonArray migrateArray(JsonArray storage) {
        JsonArray out = new JsonArray();
        if (storage == null) {
            return out;
        }
        for (JsonElement element : storage) {
            String payload;
            if (element == null || element.isJsonNull()
                    || (element.isJsonPrimitive() && element.getAsString().isBlank())) {
                payload = "";
            } else if (element.isJsonPrimitive()) {
                payload = LegacyPayloads.migrate(element.getAsString());
            } else {
                payload = LegacyPayloads.migrate(element.toString());
            }
            out.add(payload);
        }
        return out;
    }

    @Override
    public String[] read(StorageKey key) throws IOException {
        org.bukkit.Location location = StorageKey.blockLocation(key.owner());
        if (location == null) {
            throw new IOException("Block world is not loaded or invalid owner: " + key.owner());
        }
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            JsonElement blockElement = cache.root.get(key.owner());
            if (blockElement == null || !blockElement.isJsonObject()) {
                return new String[0];
            }
            JsonObject block = blockElement.getAsJsonObject();
            JsonElement tablesElement = block.get("tables");
            if (tablesElement == null || !tablesElement.isJsonObject()) {
                return new String[0];
            }
            JsonElement arrayElement = tablesElement.getAsJsonObject().get(key.table());
            if (arrayElement == null || !arrayElement.isJsonArray()) {
                return new String[0];
            }
            JsonArray array = arrayElement.getAsJsonArray();
            String[] result = new String[array.size()];
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                result[i] = (element != null && element.isJsonPrimitive() && !element.isJsonNull())
                        ? element.getAsString()
                        : "";
            }
            return result;
        }
    }

    @Override
    public void write(StorageKey key, String[] slots) throws IOException {
        org.bukkit.Location location = StorageKey.blockLocation(key.owner());
        if (location == null) {
            // Мир не загружен — данные НЕ должны теряться: бросаем исключение,
            // StorageService пометит запись как грязную и повторит позже.
            throw new IOException("Cannot write block storage: world is not loaded (" + key.owner() + ")");
        }
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            JsonElement blockElement = cache.root.get(key.owner());
            JsonObject block = blockElement != null && blockElement.isJsonObject()
                    ? blockElement.getAsJsonObject()
                    : new JsonObject();
            JsonElement tablesElement = block.get("tables");
            JsonObject tables = tablesElement != null && tablesElement.isJsonObject()
                    ? tablesElement.getAsJsonObject()
                    : new JsonObject();
            boolean allEmpty = slots == null;
            JsonArray array = new JsonArray();
            if (slots != null) {
                for (String slot : slots) {
                    array.add(slot == null ? "" : slot);
                    if (slot != null && !slot.isBlank()) {
                        allEmpty = false;
                    }
                }
            }
            if (allEmpty) {
                tables.remove(key.table());
            } else {
                tables.add(key.table(), array);
            }
            if (tables.size() == 0) {
                cache.root.remove(key.owner());
            } else {
                block.add("tables", tables);
                cache.root.add(key.owner(), block);
            }
            cache.dirty = true;
        }
        scheduleRegionWrite(cache);
    }

    @Override
    public void remove(StorageKey key) throws IOException {
        org.bukkit.Location location = StorageKey.blockLocation(key.owner());
        if (location == null) {
            throw new IOException("Block world is not loaded or invalid owner: " + key.owner());
        }
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            JsonElement blockElement = cache.root.get(key.owner());
            if (blockElement == null || !blockElement.isJsonObject()) {
                return;
            }
            JsonObject block = blockElement.getAsJsonObject();
            if (block.has("tables") && block.get("tables").isJsonObject()) {
                JsonObject tables = block.getAsJsonObject("tables");
                tables.remove(key.table());
                if (tables.size() == 0) {
                    cache.root.remove(key.owner());
                }
            } else {
                cache.root.remove(key.owner());
            }
            cache.dirty = true;
        }
        scheduleRegionWrite(cache);
    }

    /** Все payloadы всех таблиц блока (для дропа предметов при разрушении). */
    public List<String> allPayloads(org.bukkit.Location location) {
        RegionCache cache = regionFor(location);
        List<String> out = new ArrayList<>();
        synchronized (cache.lock) {
            JsonElement blockElement = cache.root.get(ownerKey(location));
            if (blockElement == null || !blockElement.isJsonObject()) {
                return out;
            }
            JsonObject block = blockElement.getAsJsonObject();
            JsonElement tablesElement = block.get("tables");
            if (tablesElement == null || !tablesElement.isJsonObject()) {
                return out;
            }
            for (Map.Entry<String, JsonElement> tableEntry : tablesElement.getAsJsonObject().entrySet()) {
                if (!tableEntry.getValue().isJsonArray()) {
                    continue;
                }
                for (JsonElement element : tableEntry.getValue().getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive() && !element.isJsonNull()
                            && !element.getAsString().isBlank()) {
                        out.add(element.getAsString());
                    }
                }
            }
        }
        return out;
    }

    /** Удаляет все данные блока (все таблицы). */
    public void removeBlock(org.bukkit.Location location) {
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            if (cache.root.remove(ownerKey(location)) != null) {
                cache.dirty = true;
            }
        }
        scheduleRegionWrite(cache);
    }

    // ================= запись региона (коалесинг + повтор) =================

    private void scheduleRegionWrite(RegionCache cache) {
        try {
            regionWrites.computeIfAbsent(cache.key,
                    k -> io.schedule(() -> runRegionWrite(cache, k), REGION_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // I/O-поток уже остановлен (shutdown): синхронный flushAll
            // сам запишет dirty-регион.
        }
    }

    private void scheduleRegionRetry(RegionCache cache) {
        try {
            regionWrites.computeIfAbsent(cache.key,
                    k -> io.schedule(() -> runRegionWrite(cache, k), RETRY_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // см. scheduleRegionWrite
        }
    }

    private void runRegionWrite(RegionCache cache, String key) {
        regionWrites.remove(key);
        synchronized (cache.lock) {
            if (!cache.dirty) {
                return;
            }
            cache.dirty = false;
            try {
                writeRegionFile(cache);
            } catch (Exception e) {
                cache.dirty = true;
                plugin.getLogger().severe("Failed to write block region " + cache.file + ": " + e.getMessage()
                        + " (retry in " + (RETRY_DELAY_MS / 1000) + "s)");
                scheduleRegionRetry(cache);
            }
        }
    }

    /** Сериализует и пишет регион (вызывается под cache.lock). */
    private void writeRegionFile(RegionCache cache) throws IOException {
        File worldFolder = cache.file.getParentFile();
        if (worldFolder != null && !worldFolder.exists() && !worldFolder.mkdirs()) {
            throw new IOException("Could not create folder " + worldFolder);
        }
        Path target = cache.file.toPath();
        Path tmp = target.resolveSibling(cache.file.getName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(tmp, GSON.toJson(cache.root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw e;
        }
    }

    /**
     * При выгрузке мира флашит и выгружает его регионы из кэша,
     * чтобы данные не висели в памяти и были на диске до повторной загрузки.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        unloadWorld(event.getWorld());
    }

    /**
     * Флашит и выгружает регионы выгружаемого мира
     * (обработчик {@link org.bukkit.event.world.WorldUnloadEvent}).
     */
    public void unloadWorld(World world) {
        if (world == null) {
            return;
        }
        String prefix = new File(world.getWorldFolder(), "CustomGuiReworked/blocks").getAbsolutePath()
                + File.separator;
        for (Map.Entry<String, RegionCache> entry : new ArrayList<>(regions.entrySet())) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            RegionCache cache = entry.getValue();
            ScheduledFuture<?> future = regionWrites.remove(cache.key);
            if (future != null) {
                future.cancel(false);
            }
            synchronized (cache.lock) {
                if (cache.dirty) {
                    cache.dirty = false;
                    try {
                        writeRegionFile(cache);
                    } catch (Exception e) {
                        cache.dirty = true;
                        plugin.getLogger().severe("Failed to flush block region on world unload "
                                + cache.file + ": " + e.getMessage());
                    }
                }
            }
            regions.remove(cache.key, cache);
        }
    }

    @Override
    public void flushAll() throws IOException {
        IOException firstFailure = null;
        for (RegionCache cache : regions.values()) {
            ScheduledFuture<?> future = regionWrites.remove(cache.key);
            if (future != null) {
                future.cancel(false);
            }
            synchronized (cache.lock) {
                if (!cache.dirty) {
                    continue;
                }
                cache.dirty = false;
                try {
                    writeRegionFile(cache);
                } catch (IOException e) {
                    cache.dirty = true; // не выдаём потерю за успешную запись
                    plugin.getLogger().severe("Failed to flush block region " + cache.file + ": " + e.getMessage());
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
