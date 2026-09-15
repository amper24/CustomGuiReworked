package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public FunctionalBlockRegistry(CustomGuiReworked plugin) {
        this.plugin = plugin;
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
