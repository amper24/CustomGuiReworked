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
import java.util.UUID;

/**
 * Файловый бэкенд «одна таблица — один файл» (player/global/team).
 *
 * <p>Формат файла — JSON-массив строк (подписанные payloadы предметов).
 * Путь файла совпадает со старым форматом плагина, поэтому данные 1.x
 * читаются без переноса; элементы старого формата (объекты NBTAPI)
 * мигрируются при чтении.
 *
 * <p>Все вызовы выполняются на выделенном I/O-потоке {@link StorageService}
 * (или синхронно при остановке сервера) и не должны вызываться из
 * других потоков параллельно.
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
        sweepStaleTmp(folder);
    }

    /** Удаляет осиротевшие временные файлы после жёсткого краха сервера. */
    public static void sweepStaleTmp(File folder) {
        File[] leftovers = folder.listFiles((dir, name) -> name.contains(".tmp-"));
        if (leftovers == null) {
            return;
        }
        for (File leftover : leftovers) {
            if (!leftover.delete()) {
                leftover.deleteOnExit();
            }
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
    public String[] read(StorageKey key) throws IOException {
        File file = fileFor(key);
        if (!file.exists()) {
            return new String[0];
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return new String[0];
        }
        try {
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonArray()) {
                plugin.getLogger().warning("Storage file is not a JSON array, ignoring: " + file);
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
                    String before = element.getAsString();
                    payload = LegacyPayloads.migrate(before);
                    if (!payload.equals(before)) {
                        migrated = true;
                    }
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
        } catch (RuntimeException e) {
            // Битый JSON и т.п. — не теряем содержимое файла, только логируем
            if (plugin != null) {
                plugin.getLogger().severe("Failed to parse storage file " + file + ": " + e.getMessage());
            }
            return new String[0];
        }
    }

    @Override
    public void write(StorageKey key, String[] slots) throws IOException {
        File file = fileFor(key);
        if (isEmpty(slots)) {
            if (file.exists() && !file.delete()) {
                throw new IOException("Could not delete " + file);
            }
        } else {
            writeFile(file, slots == null ? new String[0] : slots);
        }
    }

    private static boolean isEmpty(String[] slots) {
        if (slots == null) {
            return true;
        }
        for (String slot : slots) {
            if (slot != null && !slot.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Атомарная запись: уникальный временный файл + ATOMIC_MOVE. */
    private void writeFile(File file, String[] slots) throws IOException {
        JsonArray array = new JsonArray();
        for (String slot : slots) {
            array.add(slot == null ? "" : slot);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        Path target = file.toPath();
        // Уникальное имя tmp — параллельные записи (flush + автосейв)
        // не затирают временные файлы друг друга.
        Path tmp = target.resolveSibling(file.getName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(tmp, GSON.toJson(array), StandardCharsets.UTF_8);
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

    @Override
    public void remove(StorageKey key) throws IOException {
        File file = fileFor(key);
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + file);
        }
    }

    @Override
    public void flushAll() {
        // Внутренних буферов нет — всё пишется сразу
    }
}
