package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.Map;

/**
 * Обработчик функционального блока (печь, верстак, бочка с жидкостью,
 * генератор и т.п.) — блок, у которого GUI ведёт собственную логику:
 * локальные title/design per-плеер, крафты, топливо, анимации прогресса.
 *
 * <p>Регистрируется на ID кастомного блока через
 * {@link FunctionalBlockRegistry#registerHandler(String, FunctionalBlockHandler)}
 * или удобнее — через builder:
 * <pre>{@code
 * FunctionalBlock.builder("my_furnace")
 *     .gui("furnace")
 *     .onOpen((player, block, inv) ->
 *             CustomGuiAPI.setLocalTitle(player, "§6Фурна " + block.getBlockX()))
 *     .onTick((block, inv) -> updateProgress(block, inv))
 *     .register();
 * }</pre>
 *
 * <p>Все колбэки вызываются на основном потоке; исключение внутри
 * колбэка не ломает сервер (логируется и проглатывается).
 */
public interface FunctionalBlockHandler {

    /**
     * Имя GUI, которое открывает этот блок.
     *
     * <p>При регистрации ID блока автоматически привязывается к этому
     * GUI (правый клик по блоку откроет его).
     */
    String getGuiName();

    /**
     * Проверка доступа перед открытием (ПКМ по блоку).
     *
     * @return false — открыть GUI нельзя (клик «съедается»,
     *         игрок получает сообщение); true по умолчанию
     */
    default boolean canOpen(Player player, Location block) {
        return true;
    }

    /**
     * Блок открыт: GUI собран, событие {@code GuiOpenEvent} отработало,
     * окно ещё не показано игроку — сюда можно сетаить локальный
     * title/design (он применится до {@code openInventory}).
     *
     * <pre>{@code
     * .onOpen((player, block, inv) -> {
     *     CustomGuiAPI.setLocalTitle(player, "§6Фурна " + block.getBlockX());
     *     CustomGuiAPI.setLocalDesign(player, 4, progressItem(0));
     * })}
     * </pre>
     */
    default void onOpen(Player player, Location block, Inventory inv) {
    }

    /**
     * Клик игрока по любому слоту этого GUI (включая DESIGN-кнопки).
     * Вызывается ДО внешних слушателей {@code GuiSlotClickEvent}:
     * можно вызвать {@code event.setInteractionCancelled(true)}
     * и ванильный клик будет отменён.
     *
     * @param slot индекс слота
     * @param type тип слота ({@link SlotType})
     * @param event событие клика (уже сформировано)
     */
    default void onClick(Player player, Location block, int slot, SlotType type, GuiSlotClickEvent event) {
    }

    /**
     * GUI блока закрыт (игрок вышел из окна). Хранилище уже сохранено/
     * обработано; локальные оверрайды уже очищены.
     */
    default void onClose(Player player, Location block) {
    }

    /**
     * Тик для анимаций и прогресса: вызывается каждые 5 тиков
     * для каждой открытой сессии этого типа (т.е. per-зритель).
     *
     * <pre>{@code
     * .onTick((block, inv) -> {
     *     int progress = getProgress(block);
     *     for (Player viewer : CustomGuiAPI.getViewers(block)) {
     *         CustomGuiAPI.setLocalDesign(viewer, 4, progressItem(progress));
     *     }
     * })}
     * </pre>
     *
     * <p>Обычно достаточно обновлять {@code setLocalDesign} для слотов
     * прогресса — это не трогает файл GUI и других игроков/блоков.
     */
    default void onTick(Location block, Inventory inv) {
    }

    /**
     * Блок разрушен (предметы уже выброшены, GUI зрителей закрываются).
     * Хорошее место, чтобы остановить анимации и убрать состояние
     * прогресса/топлива для этой локации.
     */
    default void onBlockBroken(Location block) {
    }

    /** Рецепт крафта блока (null — блок без рецепта). */
    default CraftingRecipe getCraftingRecipe() {
        return null;
    }

    /**
     * Расход топлива на один крафт: FUEL-слот → количество предметов.
     * Пустая карта по умолчанию.
     */
    default Map<Integer, Integer> getFuelConsumption() {
        return Collections.emptyMap();
    }
}
