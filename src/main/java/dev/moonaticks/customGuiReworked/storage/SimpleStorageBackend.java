package dev.moonaticks.customGuiReworked.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

/**
 * Файловый бэкенд «одна таблица — один файл» (player/global/team).
 *
 * <p>Формат файла — JSON-массив строк (подписанные payloadы предметов).
 * Путь файла совпадает со старым форматом плагина, поэтому данные 1.x
 * читаются без переноса; элементы старого формата (объекты NBTAPI)
 * мигрируются при чтении.
 */
public class SimpleStorageBackend implements StorageBackend {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final CustomGuiReworked plugin;
    private final StorageType type;
    private final File folder;
    private final boolean ownerInName;

    public SimpleStorageBackend(CustomGuiReworked plugin, StorageType type, File folder, boolean ownerInName) {
        this.plugin = plugin;
        this.type = type;
        this.folder = folder;
        this.ownerInName = ownerInName;
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create folder " + folder);
        }
    }

    @Override
    public StorageType type() {
        return type;
    }

    private File fileFor(StorageKey key) {
        String base = ownerInName ? key.owner() + "_" + key.table() : key.table();
        return new File(folder, base.replaceAll("[^a-zA-Z0-9_.\\-]", "_"));
    }

    @Override
    public String[] read(StorageKey key) {
        File file = fileFor(key);
        if (!file.exists()) {
            return new String[0];
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return new String[0];
            }
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonArray()) {
                return new String[0];
            }
            JsonArray array = root.getAsJsonArray();
            String[] result = new String[array.size()];
            boolean migrated = false;
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                String payload;
                if (element == null || element.isJsonNull()
                        || (element.isJsonPrimitive() && element.getAsString().isBlank())) {
                    payload = "";
                } else if (element.isJsonPrimitive()) {
                    payload = LegacyPayloads.migrate(element.getAsString());
                } else {
                    // Старый формат: элемент — объект NBTAPI
                    payload = LegacyPayloads.migrate(element.toString());
                    migrated = true;
                }
                result[i] = payload;
            }
            if (migrated) {
                writeFile(file, result); // фиксируем мигрированный формат
            }
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to read storage file " + file + ": " + e.getMessage());
            return new String[0];
        }
    }

    @Override
    public void write(StorageKey key, String[] slots) {
        File file = fileFor(key);
        boolean allEmpty = slots == null;
        if (slots != null) {
            for (String slot : slots) {
                if (slot != null && !slot.isBlank()) {
                    allEmpty = false;
                    break;
                }
            }
        }
        try {
            if (allEmpty) {
                if (file.exists() && !file.delete()) {
                    plugin.getLogger().warning("Could not delete " + file);
                }
            } else {
                writeFile(file, slots);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to write storage file " + file + ": " + e.getMessage());
        }
    }

    /** Атомарная запись: временный файл + move. */
    private void writeFile(File file, String[] slots) throws IOException {
        JsonArray array = new JsonArray();
        for (String slot : slots) {
            array.add(slot == null ? "" : slot);
        }
        Path target = file.toPath();
        Path tmp = target.resolveSibling(file.getName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(array), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void remove(StorageKey key) {
        File file = fileFor(key);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file);
        }
    }

    @Override
    public void flushAll() {
        // Внутренних буферов нет — всё пишется сразу
    }
}
