package dev.moonaticks.customGuiReworked.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotCommand;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.codec.LegacyPayloads;
import dev.moonaticks.customGuiReworked.storage.SimpleStorageBackend;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр GUI: загрузка/сохранение файлов и O(1)-индексы.
 *
 * <p>Две папки:
 * <ul>
 *   <li>{@code tables/} — GUI, созданные в редакторе (и унаследованные от 1.x);</li>
 *   <li>{@code custom/} — GUI, зарегистрированные другими плагинами через API
 *       ({@link Gui.Source#CUSTOM}). Путь к данным хранилища совпадает —
 *       данные «переехавших» GUI сохраняются.</li>
 * </ul>
 *
 * <p>Читает как новый, так и старый формат файлов (поле «saveDataMethod»,
 * «commandExecutor», «customBlockIDs», legacy-дизайн без тега кодека).
 */
public class GuiRegistry {

    private final CustomGuiReworked plugin;
    private final File tableDir;
    private final File customDir;
    private final Map<String, Gui> guis = new ConcurrentHashMap<>();
    private final Map<String, Gui> blockIndex = new ConcurrentHashMap<>();

    public GuiRegistry(CustomGuiReworked plugin) {
        this(plugin, plugin.getDataFolder());
    }

    /** Тестовый конструктор с произвольной папкой данных (plugin может быть null,
     *  если тестируемые пути не пишут в лог). */
    GuiRegistry(CustomGuiReworked plugin, File dataFolder) {
        this.plugin = plugin;
        this.tableDir = new File(dataFolder, "tables");
        this.customDir = new File(dataFolder, "custom");
    }

    public File tableDirectory() {
        return tableDir;
    }

    public File customDirectory() {
        return customDir;
    }

    /** Папка, в которую сохраняется GUI с данным происхождением. */
    private File directoryFor(Gui gui) {
        return gui.source() == Gui.Source.CUSTOM ? customDir : tableDir;
    }

    /** Загружает (или перечитывает) все GUI из папок tables/ и custom/. */
    public synchronized void loadAll() {
        guis.clear();
        blockIndex.clear();
        // Осиротевшие tmp после жёсткого краха не должны накапливаться.
        SimpleStorageBackend.sweepStaleTmp(tableDir);
        SimpleStorageBackend.sweepStaleTmp(customDir);
        int loaded = 0;
        // tables/ загружаются первыми, custom/ (зарегистрированные
        // другими плагинами) имеют приоритет при совпадении имён —
        // конфликт всегда логируется, а не молча проглатывается.
        for (File dir : new File[]{tableDir, customDir}) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                try {
                    Gui gui = loadFromFile(file);
                    if (gui == null) {
                        continue;
                    }
                    Gui previous = guis.put(gui.name(), gui);
                    if (previous != null) {
                        blockIndex.values().removeIf(g -> g == previous);
                        plugin.getLogger().warning("Duplicate GUI name '" + gui.name()
                                + "' in " + file.getName() + " (overrides "
                                + (previous.source() == Gui.Source.CUSTOM ? "custom" : "tables") + " copy)");
                    }
                    reindexBlocks(gui);
                    loaded++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load GUI " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " GUI(s) from " + tableDir + " and " + customDir);
    }

    public Gui get(String name) {
        if (name == null) {
            return null;
        }
        return guis.get(Gui.normalizeName(name));
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(guis.keySet());
    }

    /** Все GUI (неизменяемый список). */
    public List<Gui> all() {
        return new ArrayList<>(guis.values());
    }

    /** Происхождение GUI: table / custom / runtime / none. */
    public String sourceOf(String name) {
        Gui gui = get(name);
        return gui == null ? "none" : gui.source().name().toLowerCase(Locale.ROOT);
    }

    /** Создаёт GUI с параметрами по умолчанию (папка tables/, если ещё не существует). */
    public Gui create(String name) {
        String normalized = Gui.normalizeName(name);
        Gui existing = guis.get(normalized);
        if (existing != null) {
            return existing;
        }
        Gui gui = new Gui(normalized);
        gui.title(normalized);
        gui.source(Gui.Source.TABLE);
        guis.put(normalized, gui);
        save(gui);
        return gui;
    }

    /**
     * Регистрирует GUI, созданный другим плагином (API).
     *
     * <p>Если GUI с таким именем уже существует — он заменяется
     * (файл старого происхождения удаляется, пишется новый).
     *
     * @param gui     GUI (имя нормализуется не нужно — конструктор сам)
     * @param persist true — сохранить в {@code custom/} (переживёт рестарт),
     *                false — только в памяти (потеряется после перезагрузки)
     */
    public synchronized Gui register(Gui gui, boolean persist) {
        if (gui == null) {
            return null;
        }
        // Экземпляр мог быть зарегистрирован раньше под другим именем
        // (Gui.rename + повторный register) — снимаем старую привязку,
        // иначе в карте остались бы два ключа на один GUI и осиротевший файл.
        String oldName = detachIdentity(gui);
        Gui.Source previousSource = gui.source();
        Gui sameName = guis.get(gui.name());
        if (sameName != null && sameName != gui) {
            blockIndex.values().removeIf(g -> g == sameName);
            guis.remove(sameName.name());
            deleteFileOf(sameName);
        }
        gui.source(persist ? Gui.Source.CUSTOM : Gui.Source.RUNTIME);
        // Убираем осиротевший файл под прежним именем/статусом.
        // Каталог старого файла определяется ПРЕЖНИМ source (после смены
        // source directoryFor(gui) указывал бы уже на новую папку).
        if (oldName != null && previousSource != Gui.Source.RUNTIME) {
            boolean renamed = !oldName.equals(gui.name());
            boolean becameTransient = previousSource == Gui.Source.CUSTOM && !persist;
            if (renamed || becameTransient) {
                File oldDir = previousSource == Gui.Source.CUSTOM ? customDir : tableDir;
                File staleFile = new File(oldDir, oldName + ".yml");
                if (staleFile.exists() && !staleFile.delete()) {
                    plugin.getLogger().warning("Could not delete stale GUI file " + staleFile);
                }
            }
        }
        guis.put(gui.name(), gui);
        reindexBlocks(gui);
        if (persist) {
            writeToFile(gui);
        }
        return gui;
    }

    /**
     * Снимает все ранее существовавшие привязки того же самого экземпляра
     * (ключ в карте имён и block-индекс), вызванные переименованием.
     *
     * @return прежнее имя экземпляра в карте или null
     */
    private String detachIdentity(Gui gui) {
        String oldName = null;
        for (Map.Entry<String, Gui> entry : guis.entrySet()) {
            if (entry.getValue() == gui) {
                oldName = entry.getKey();
                if (!oldName.equals(gui.name())) {
                    guis.remove(oldName);
                }
                break;
            }
        }
        blockIndex.values().removeIf(g -> g == gui);
        return oldName;
    }

    /**
     * Снимает GUI, зарегистрированный через API.
     *
     * @param deleteFile удалить ли файл (актуально для {@link Gui.Source#CUSTOM})
     * @return true, если GUI существовал
     */
    public synchronized boolean unregister(String name, boolean deleteFile) {
        Gui gui = get(name);
        if (gui == null) {
            return false;
        }
        guis.remove(gui.name());
        blockIndex.values().removeIf(g -> g == gui);
        if (deleteFile) {
            deleteFileOf(gui);
        }
        return true;
    }

    /**
     * Удаляет GUI и его файл (редактор / команда /gui delete).
     * Данные хранилища (data/, блоки) не удаляются.
     *
     * @return true, если GUI существовал
     */
    public boolean delete(String name) {
        Gui gui = get(name);
        if (gui == null) {
            return false;
        }
        guis.remove(gui.name());
        blockIndex.values().removeIf(g -> g == gui);
        deleteFileOf(gui);
        return true;
    }

    /** Сохраняет GUI в файл (по папке происхождения) и обновляет индексы. */
    public synchronized void save(Gui gui) {
        if (gui == null) {
            return;
        }
        String oldName = detachIdentity(gui);
        if (gui.source() == Gui.Source.RUNTIME) {
            guis.put(gui.name(), gui);
            reindexBlocks(gui);
            return;
        }
        if (oldName != null && !oldName.equals(gui.name())) {
            File oldFile = new File(directoryFor(gui), oldName + ".yml");
            if (oldFile.exists() && !oldFile.delete()) {
                plugin.getLogger().warning("Could not delete stale GUI file " + oldFile);
            }
        }
        writeToFile(gui);
        guis.put(gui.name(), gui);
        reindexBlocks(gui);
    }

    private void writeToFile(Gui gui) {
        File dir = directoryFor(gui);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create folder " + dir);
            return;
        }
        File target = new File(dir, gui.fileName());
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", gui.name());
        config.set("title", gui.title());
        config.set("slots", gui.slots());
        config.set("storage", gui.storage().id());
        config.set("category", gui.category());
        config.set("source", gui.source().name().toLowerCase(Locale.ROOT));
        List<String> skeleton = new ArrayList<>(gui.slots());
        for (SlotType type : gui.skeleton()) {
            skeleton.add(type.id());
        }
        config.set("skeleton", skeleton);
        config.set("design", new ArrayList<>(gui.design()));
        List<Map<String, Object>> commands = new ArrayList<>();
        for (SlotCommand command : gui.commands()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", command.slot());
            map.put("command", command.command());
            map.put("delay", command.delay());
            commands.add(map);
        }
        config.set("commands", commands);
        config.set("blockIds", new ArrayList<>(gui.blockIds()));
        try {
            atomicSave(config, target);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save GUI " + gui.name() + ": " + e.getMessage());
        }
    }

    /**
     * Атомарное сохранение YAML: пишем в уникальный tmp и делаем ATOMIC_MOVE,
     * чтобы падение сервера не оставило полу-записанный файл GUI.
     */
    private void atomicSave(YamlConfiguration config, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        String data = config.saveToString();
        Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(tmp, data, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw e;
        }
    }

    private void deleteFileOf(Gui gui) {
        if (gui.source() == Gui.Source.RUNTIME) {
            return;
        }
        File file = new File(directoryFor(gui), gui.fileName());
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file);
        }
    }

    /** Перечитывает один GUI из файла. */
    public Gui reload(String name) {
        Gui gui = get(name);
        if (gui == null || gui.source() == Gui.Source.RUNTIME) {
            return null;
        }
        File file = new File(directoryFor(gui), gui.fileName());
        if (!file.exists()) {
            return null;
        }
        try {
            Gui loaded = loadFromFile(file);
            if (loaded == null) {
                return null;
            }
            // Снимаем привязки СТАРОГО экземпляра (он другой объект —
            // обычный reindexBlocks их бы не убрал и оставил «призраки»).
            blockIndex.values().removeIf(g -> g == gui);
            guis.put(loaded.name(), loaded);
            reindexBlocks(loaded);
            return loaded;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload GUI " + gui.name() + ": " + e.getMessage());
            return null;
        }
    }

    private void reindexBlocks(Gui gui) {
        blockIndex.values().removeIf(g -> g == gui);
        for (String id : gui.blockIds()) {
            String key = id.toLowerCase(Locale.ROOT);
            Gui existing = blockIndex.get(key);
            if (existing != null && existing != gui) {
                plugin.getLogger().warning("Block id '" + id + "' is bound to both '"
                        + existing.name() + "' and '" + gui.name() + "' — '"
                        + gui.name() + "' wins; unbind it from one of the GUIs to avoid ambiguity");
            }
            blockIndex.put(key, gui);
        }
    }

    /**
     * Поиск GUI по ID кастомного блока (без учёта регистра,
     * с fallback'ом на суффикс после «:»).
     */
    public Gui getByBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        Gui gui = blockIndex.get(blockId.toLowerCase(Locale.ROOT));
        if (gui != null) {
            return gui;
        }
        int idx = blockId.lastIndexOf(':');
        if (idx >= 0 && idx < blockId.length() - 1) {
            return blockIndex.get(blockId.substring(idx + 1).toLowerCase(Locale.ROOT));
        }
        return null;
    }

    // ================= чтение файлов =================

    private Gui loadFromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String name = Gui.normalizeName(config.getString("name", file.getName()));
        Gui gui = new Gui(name);
        gui.title(config.getString("title", name));
        gui.slots(config.getInt("slots", 27));
        try {
            gui.category(config.getString("category"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid category in " + file.getName() + ": " + e.getMessage());
        }
        gui.source(sourceOf(file, config));

        if (config.isString("storage")) {
            gui.storage(StorageType.fromId(config.getString("storage")));
        } else {
            gui.storage(StorageType.fromLegacyNumber(config.getInt("saveDataMethod", 5)));
        }

        List<String> skeletonRaw = config.getStringList("skeleton");
        if (skeletonRaw.size() == gui.slots()) {
            List<SlotType> types = new ArrayList<>(gui.slots());
            for (String value : skeletonRaw) {
                types.add(SlotType.fromLegacy(value));
            }
            gui.replaceSkeleton(types);
        } else {
            if (!skeletonRaw.isEmpty()) {
                plugin.getLogger().warning("Skeleton size mismatch in " + file.getName() + " — rebuilt as design");
            }
            gui.resetSkeleton();
        }

        List<String> designRaw = config.getStringList("design");
        List<String> design = new ArrayList<>(gui.slots());
        for (int i = 0; i < gui.slots(); i++) {
            design.add(i < designRaw.size() ? LegacyPayloads.migrate(designRaw.get(i)) : "");
        }
        gui.replaceDesign(design);

        // Команды: новый формат (список карт) или legacy («commandExecutor» — JSON-строки)
        List<Object> rawCommands = new ArrayList<>();
        if (config.isList("commands")) {
            rawCommands.addAll(config.getList("commands"));
        }
        if (rawCommands.isEmpty() && config.isList("commandExecutor")) {
            rawCommands.addAll(config.getStringList("commandExecutor"));
        }
        List<SlotCommand> commands = new ArrayList<>();
        for (Object raw : rawCommands) {
            try {
                if (raw instanceof Map<?, ?> map) {
                    Object command = map.get("command");
                    if (command == null || command.toString().isBlank()) {
                        continue;
                    }
                    commands.add(new SlotCommand(
                            intValue(map.get("slot"), -1),
                            command.toString(),
                            intValue(map.get("delay"), 0)));
                } else if (raw instanceof String s && !s.isBlank()) {
                    JsonObject obj = JsonParser.parseString(s).getAsJsonObject();
                    String command = obj.has("command") ? obj.get("command").getAsString() : "";
                    if (command.isBlank()) {
                        continue;
                    }
                    int slot = obj.has("slot") ? obj.get("slot").getAsInt() : -1;
                    int delay = obj.has("delay") ? obj.get("delay").getAsInt() : 0;
                    if (slot >= 0) {
                        commands.add(new SlotCommand(slot, command, delay));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping malformed command entry in " + file.getName());
            }
        }
        for (SlotCommand command : commands) {
            gui.addCommand(command);
        }

        List<String> blockIds = config.getStringList("blockIds");
        if (blockIds.isEmpty()) {
            blockIds = config.getStringList("customBlockIDs");
        }
        for (String id : blockIds) {
            gui.addBlockId(id);
        }
        return gui;
    }

    private Gui.Source sourceOf(File file, YamlConfiguration config) {
        String explicit = config.getString("source", "").toLowerCase(Locale.ROOT);
        if ("custom".equals(explicit)) {
            return Gui.Source.CUSTOM;
        }
        if ("table".equals(explicit)) {
            return Gui.Source.TABLE;
        }
        // Фолбэк — по папке
        File parent = file.getParentFile();
        return parent != null && parent.equals(customDir) ? Gui.Source.CUSTOM : Gui.Source.TABLE;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return defaultValue;
    }
}
