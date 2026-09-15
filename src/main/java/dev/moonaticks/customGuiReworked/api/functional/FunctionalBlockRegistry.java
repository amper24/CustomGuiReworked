package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр функциональных блоков: ID кастомного блока → {@link FunctionalBlockHandler}.
 *
 * <p>При регистрации:
 * <ul>
 *   <li>ID блока запоминается для диспетчеризации
 *       (canOpen/open/click/close/tick/broken — см. {@link FunctionalBlockHandler});</li>
 *   <li>GUI обработчика автоматически привязывается к ID блока
 *       (правый клик по блоку откроет этот GUI) — если GUI ещё не
 *       существует, привязка повторится при первом открытии.</li>
 * </ul>
 *
 * <p>Создаётся плагином при включении; получение из внешнего кода —
 * через {@link dev.moonaticks.customGuiReworked.api.GuiService#getFunctionalBlocks()}.
 */
public class FunctionalBlockRegistry {

    private final CustomGuiReworked plugin;
    /** ID блока (в нижнем регистре) → обработчик. */
    private final Map<String, FunctionalBlockHandler> byBlockId = new ConcurrentHashMap<>();
    /** blockId → owner-ключ («world:x,y,z») → данные. */
    private final Map<String, Map<String, Map<String, String>>> dataFiles = new ConcurrentHashMap<>();
    /** blockId → наборы owner-ключей «работающих» блоков. */
    private final Map<String, Set<String>> workingByBlockId = new ConcurrentHashMap<>();
    /** Файлы, изменившиеся с последнего сохранения. */
    private final Set<String> dirtyFiles = ConcurrentHashMap.newKeySet();
    /** Корень файлов данных (data/functional); null — загрузка не выполнялась. */
    private File dataRoot;

    public FunctionalBlockRegistry(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    // ==================== данные блока (FunctionalBlockData) ====================

    /**
     * Данные блока (персистентный KV-стейт: прогресс, флаги, произвольные
     * значения). Возвращаемый объект живёт, пока блок существует;
     * {@link #removeBlockData} удаляет его.
     *
     * @param blockId ID блока (в любом регистре)
     * @param block   блок
     * @return данные (пустые, если их ещё не было); null, если blockId не
     *         зарегистрирован или локация невалидна
     */
    public FunctionalBlockData data(String blockId, Location block) {
        if (blockId == null || block == null || block.getWorld() == null) {
            return null;
        }
        String id = normalize(blockId);
        if (!byBlockId.containsKey(id)) {
            return null;
        }
        String owner = dev.moonaticks.customGuiReworked.storage.BlockStorageBackend.ownerKey(block);
        Map<String, String> values = dataFiles
                .computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(owner, k -> new ConcurrentHashMap<>());
        return new FunctionalBlockData(values, () -> dirtyFiles.add(id));
    }

    /**
     * Загружает данные блоков из {@code dataRoot} (вызывается плагином
     * при включении): восстанавливает значения и набор «работающих»
     * блоков — работа продолжается после перезагрузки сервера.
     */
    public void loadData(File dataDir) {
        this.dataRoot = dataDir;
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            return;
        }
        File[] files = dataDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection blocks = y.getConfigurationSection("blocks");
            if (blocks == null) {
                continue;
            }
            for (String owner : blocks.getKeys(false)) {
                ConfigurationSection sec = blocks.getConfigurationSection(owner);
                if (sec == null) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                for (String key : sec.getKeys(false)) {
                    values.put(key, sec.getString(key, ""));
                }
                if (values.isEmpty()) {
                    continue;
                }
                dataFiles.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(owner, values);
                if (Boolean.parseBoolean(values.getOrDefault(
                        FunctionalBlockData.WORKING_KEY, "false"))) {
                    workingByBlockId.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
                            .add(owner);
                }
            }
        }
    }

    /**
     * Сохраняет изменившиеся файлы данных (вызывается плагином при
     * выключении; по мере изменений — фоном каждые 200 тиков тикером).
     */
    public synchronized void saveData() {
        if (dataRoot == null) {
            return;
        }
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            return;
        }
        for (String id : new HashSet<>(dirtyFiles)) {
            dirtyFiles.remove(id);
            Map<String, Map<String, String>> blocks = dataFiles.get(id);
            YamlConfiguration y = new YamlConfiguration();
            if (blocks != null) {
                for (Map.Entry<String, Map<String, String>> entry : blocks.entrySet()) {
                    if (entry.getValue().isEmpty()) {
                        continue;
                    }
                    for (Map.Entry<String, String> kv : entry.getValue().entrySet()) {
                        y.set("blocks." + entry.getKey() + "." + kv.getKey(), kv.getValue());
                    }
                }
            }
            try {
                y.save(new File(dataRoot, id + ".yml"));
            } catch (IOException e) {
                if (plugin != null) {
                    plugin.getLogger().warning("FunctionalBlockData save failed for '" + id + "': "
                            + e.getMessage());
                }
            }
        }
    }

    /**
     * Удаляет данные и «рабочий» флаг блока (вызывается при разрушении
     * кастомного блока) и сразу сохраняет изменившиеся файлы.
     */
    public void removeBlockData(Location block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        String owner = dev.moonaticks.customGuiReworked.storage.BlockStorageBackend.ownerKey(block);
        Set<String> changedFiles = new HashSet<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> file : dataFiles.entrySet()) {
            Map<String, String> values = file.getValue().remove(owner);
            if (values != null && !values.isEmpty()) {
                changedFiles.add(file.getKey());
            }
            Set<String> working = workingByBlockId.get(file.getKey());
            if (working != null && working.remove(owner)) {
                changedFiles.add(file.getKey());
            }
        }
        if (!changedFiles.isEmpty()) {
            dirtyFiles.addAll(changedFiles);
            if (plugin != null) {
                plugin.getLogger().info("Functional block data removed at " + owner);
            }
            saveData();
        }
    }

    /**
     * Тикает данные: сохраняет изменившиеся файлы (вызывается тикером
     * функциональных блоков).
     */
    public void tickDataSave() {
        if (!dirtyFiles.isEmpty()) {
            saveData();
        }
    }

    // ==================== «работа» блока (onBlockTick) ====================

    /**
     * Включает/выключает «работу» блока: пока включена,
     * {@link FunctionalBlockHandler#onBlockTick(Location, FunctionalBlockData)}
     * вызывается каждые 5 тиков <b>даже когда GUI закрыт</b>.
     * Флаг персистится — работа продолжается после перезагрузки сервера.
     *
     * @param blockId ID блока (в любом регистре)
     * @param block   блок
     * @param working true — запустить работу, false — остановить
     * @return false, если blockId не зарегистрирован
     */
    public boolean setWorking(String blockId, Location block, boolean working) {
        if (blockId == null || block == null || block.getWorld() == null) {
            return false;
        }
        String id = normalize(blockId);
        if (!byBlockId.containsKey(id)) {
            return false;
        }
        String owner = dev.moonaticks.customGuiReworked.storage.BlockStorageBackend.ownerKey(block);
        Set<String> working = workingByBlockId.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        boolean changed;
        if (working) {
            changed = working.add(owner);
        } else {
            changed = working.remove(owner);
        }
        FunctionalBlockData data = data(id, block);
        if (data != null) {
            if (working) {
                data.setBoolean(FunctionalBlockData.WORKING_KEY, true);
            } else {
                data.remove(FunctionalBlockData.WORKING_KEY);
            }
        }
        return changed;
    }

    /** true, если блок «работает» (см. {@link #setWorking}). */
    public boolean isWorking(String blockId, Location block) {
        if (blockId == null || block == null || block.getWorld() == null) {
            return false;
        }
        Set<String> working = workingByBlockId.get(normalize(blockId));
        if (working == null) {
            return false;
        }
        return working.contains(
                dev.moonaticks.customGuiReworked.storage.BlockStorageBackend.ownerKey(block));
    }

    /**
     * Пары «blockId → owner-ключи» для всех рабочих блоков (снимок —
     * используется тикером, безопасно итерировать).
     */
    public Map<String, Set<String>> workingEntries() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : workingByBlockId.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                out.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        return out;
    }

    // ==================== разрешение ID блока по Location ====================

    /**
     * Обработчик блока по его локации: ID кастомного блока определяется
     * через CraftEngine (отражённо, без жёсткой зависимости), затем
     * ищется в реестре.
     *
     * @return обработчик либо null (обычный блок / CE не установлен)
     */
    public FunctionalBlockHandler handlerForBlock(Location block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        String id = resolveCustomBlockId(block);
        return id == null ? null : getHandler(id);
    }

    /**
     * ID кастомного блока («ns:value») по его Location либо null
     * (обычный блок / CraftEngine не установлен). Определение —
     * через CraftEngine отражённо, без жёсткой зависимости.
     */
    public static String resolveCustomBlockId(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        try {
            Block block = location.getBlock();
            if (block == null) {
                return null;
            }
            Class<?> ceBlocks = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            Object state = ceBlocks.getMethod("getCustomBlockState", Block.class).invoke(null, block);
            if (state == null) {
                return null;
            }
            Object definition = state.getClass().getMethod("definition").invoke(state);
            if (definition == null) {
                return null;
            }
            Object key = definition.getClass().getMethod("id").invoke(definition);
            if (key == null) {
                return null;
            }
            Object namespace = key.getClass().getMethod("namespace").invoke(key);
            Object value = key.getClass().getMethod("value").invoke(key);
            if (namespace == null || value == null) {
                return null;
            }
            return (namespace + ":" + value).toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Регистрирует обработчик на ID кастомного блока.
     * Если обработчик уже был — заменяется.
     *
     * @param blockId ID блока (itemsadder:custom_block / craftengine:custom_block)
     * @param handler обработчик
     */
    public void registerHandler(String blockId, FunctionalBlockHandler handler) {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must not be blank");
        }
        if (handler == null || handler.getGuiName() == null || handler.getGuiName().isBlank()) {
            throw new IllegalArgumentException("handler and its gui name must not be null/blank");
        }
        byBlockId.put(normalize(blockId), handler);
        bindGui(blockId, handler.getGuiName());
    }

    /**
     * Снимает обработчик с ID блока (привязка GUI к блоку
     * в файлах остаётся — её можно убрать через
     * {@code GuiService#unregisterBlockGui}).
     */
    public void unregisterHandler(String blockId) {
        if (blockId == null) {
            return;
        }
        byBlockId.remove(normalize(blockId));
    }

    /** Обработчик ID блока (без учёта регистра), либо null. */
    public FunctionalBlockHandler getHandler(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        return byBlockId.get(normalize(blockId));
    }

    /** Все зарегистрированные обработчики (неизменяемая копия). */
    public List<FunctionalBlockHandler> getHandlers() {
        return Collections.unmodifiableList(new ArrayList<>(byBlockId.values()));
    }

    /**
     * Обработчики, работающие с данным GUI (по имени, без учёта регистра).
     * Используется для диспетчеризации открытой сессии, когда ID блока
     * неизвестен (GUI открыт программно).
     */
    public List<FunctionalBlockHandler> handlersForGui(String guiName) {
        List<FunctionalBlockHandler> out = new ArrayList<>();
        if (guiName == null || guiName.isBlank()) {
            return out;
        }
        String normalized = guiName.toLowerCase(Locale.ROOT);
        for (FunctionalBlockHandler handler : byBlockId.values()) {
            if (normalized.equals(handler.getGuiName().toLowerCase(Locale.ROOT))) {
                out.add(handler);
            }
        }
        return out;
    }

    /**
     * Привязывает ID блока к GUI в реестре GUI (и в файле).
     * Если GUI ещё не существует — молча пропускается
     * (привязка повторится при первом открытии блока).
     */
    public void bindGui(String blockId, String guiName) {
        if (blockId == null || blockId.isBlank() || guiName == null || guiName.isBlank() || plugin == null) {
            return;
        }
        Gui gui = plugin.registry().get(guiName);
        if (gui == null) {
            return;
        }
        if (!gui.blockIds().contains(blockId)) {
            gui.addBlockId(blockId);
            plugin.registry().save(gui);
        }
    }

    private static String normalize(String blockId) {
        return blockId.trim().toLowerCase(Locale.ROOT);
    }
}
