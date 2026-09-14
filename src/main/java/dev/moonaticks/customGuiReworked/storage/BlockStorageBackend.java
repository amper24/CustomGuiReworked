package dev.moonaticks.customGuiReworked.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.codec.LegacyPayloads;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Блок-хранилище: файлы регионов
 * {@code <мир>/CustomGuiReworked/blocks/<x/16>_<z/16>.json}.
 *
 * <p>Новый формат (несколько таблиц могут сосуществовать в одном блоке):
 * <pre>
 * { "world:x,y,z": { "tables": { "shop.yml": ["n1:{...}", ""] } } }
 * </pre>
 *
 * <p>Старый формат {@code { "world:x,y,z": { "table": "...", "storage": [...] } }}
 * мигрируется при чтении (ключ таблицы сохраняется без изменений).
 *
 * <p>Регионы кешируются в памяти; доступ к региону сериализован локом;
 * запись — атомарная (временный файл + move).
 */
public class BlockStorageBackend implements StorageBackend {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int REGION_SHIFT = 4; // регион 16x16

    private final CustomGuiReworked plugin;
    private final File dataFolder;
    private final Map<String, RegionCache> regions = new ConcurrentHashMap<>();

    private static final class RegionCache {
        final Object lock = new Object();
        final File file;
        JsonObject root = new JsonObject();
        boolean dirty;

        RegionCache(File file) {
            this.file = file;
        }
    }

    public BlockStorageBackend(CustomGuiReworked plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
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
        return regions.computeIfAbsent(file.getAbsolutePath(), path -> {
            RegionCache cache = new RegionCache(file);
            loadRegion(cache);
            return cache;
        });
    }

    private void loadRegion(RegionCache cache) {
        synchronized (cache.lock) {
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
                            array.set(i, new com.google.gson.JsonPrimitive(migrated));
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
    public String[] read(StorageKey key) {
        org.bukkit.Location location = StorageKey.blockLocation(key.owner());
        if (location == null) {
            return new String[0];
        }
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            JsonObject block = cache.root.getAsJsonObject(key.owner());
            if (block == null) {
                return new String[0];
            }
            JsonObject tables = block.has("tables") ? block.getAsJsonObject("tables") : null;
            if (tables == null) {
                return new String[0];
            }
            JsonArray array = tables.getAsJsonArray(key.table());
            if (array == null) {
                return new String[0];
            }
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
    public void write(StorageKey key, String[] slots) {
        org.bukkit.Location location = StorageKey.blockLocation(key.owner());
        if (location == null) {
            plugin.getLogger().warning("Cannot write block storage: world is not loaded (" + key.owner() + ")");
            return;
        }
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            JsonObject block = cache.root.getAsJsonObject(key.owner());
            if (block == null) {
                block = new JsonObject();
                cache.root.add(key.owner(), block);
            }
            JsonObject tables;
            if (block.has("tables") && block.get("tables").isJsonObject()) {
                tables = block.getAsJsonObject("tables");
            } else {
                tables = new JsonObject();
                block.add("tables", tables);
            }
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
            }
            cache.dirty = true;
        }
        scheduleRegionWrite(cache);
    }

    @Override
    public void remove(StorageKey key) {
        org.bukkit.Location location = StorageKey.blockLocation(key.owner());
        if (location == null) {
            return;
        }
        RegionCache cache = regionFor(location);
        synchronized (cache.lock) {
            JsonObject block = cache.root.getAsJsonObject(key.owner());
            if (block != null) {
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
        }
        scheduleRegionWrite(cache);
    }

    /** Все payloadы всех таблиц блока (для дропа предметов при разрушении). */
    public List<String> allPayloads(org.bukkit.Location location) {
        RegionCache cache = regionFor(location);
        List<String> out = new ArrayList<>();
        synchronized (cache.lock) {
            JsonObject block = cache.root.getAsJsonObject(ownerKey(location));
            if (block != null && block.has("tables") && block.get("tables").isJsonObject()) {
                for (Map.Entry<String, JsonElement> tableEntry : block.getAsJsonObject("tables").entrySet()) {
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

    private void scheduleRegionWrite(RegionCache cache) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (cache.lock) {
                if (!cache.dirty) {
                    return;
                }
                cache.dirty = false;
                try {
                    writeRegionFile(cache);
                } catch (Exception e) {
                    cache.dirty = true;
                    plugin.getLogger().severe("Failed to write block region " + cache.file + ": " + e.getMessage());
                }
            }
        });
    }

    /** Сериализует и пишет регион (вызывается под cache.lock). */
    private void writeRegionFile(RegionCache cache) throws IOException {
        File worldFolder = cache.file.getParentFile();
        if (worldFolder != null && !worldFolder.exists() && !worldFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create folder " + worldFolder);
        }
        Path target = cache.file.toPath();
        Path tmp = target.resolveSibling(cache.file.getName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(cache.root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void flushAll() {
        for (RegionCache cache : regions.values()) {
            synchronized (cache.lock) {
                if (!cache.dirty) {
                    continue;
                }
                cache.dirty = false;
                try {
                    writeRegionFile(cache);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to flush block region " + cache.file + ": " + e.getMessage());
                }
            }
        }
    }
}
