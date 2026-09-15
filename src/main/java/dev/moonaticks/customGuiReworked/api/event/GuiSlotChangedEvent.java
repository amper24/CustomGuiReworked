package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Вызывается на следующий тик ПОСЛЕ того, как действия игрока
 * (клик, shift-клик, драг, double-click, number-key) либо программные
 * изменения ({@code produceResult}/{@code consumeFuel}) применились
 * к инвентарю — по каждому слоту, чьё содержимое реально изменилось
 * (диф против baseline сессии).
 *
 * <p>В отличие от {@link GuiSlotClickEvent} (внутри него инвентарь ещё
 * содержит СТАРЫЕ предметы), здесь {@link #getOldItem()} и
 * {@link #getNewItem()} — фактические предметы «было/стало», поэтому
 * это точка для запуска механик без ручной диф-логики:
 * <pre>{@code
 * @EventHandler
 * public void onSlotChanged(GuiSlotChangedEvent e) {
 *     if (!"furnace".equals(e.getGui().name())) return;
 *     if (e.getSlotType() == SlotType.CRAFT) {
 *         startOrUpdateCraft(e.getInventory());      // заложили/убрали ингредиент
 *     }
 *     if (e.getSlotType() == SlotType.RESULT
 *             && e.getOldItem() != null && e.getNewItem() == null) {
 *         consumeFuelAndIngredients(e.getInventory()); // результат забрали
 *     }
 * }
 * }</pre>
 *
 * <p>Для функциональных блоков есть готовый колбэк
 * {@code FunctionalBlockHandler#onItemChanged} (вызывается ДО внешних
 * слушателей).
 *
 * <p>Слоты DESIGN не отслеживаются (дизайн — не действие игрока;
 * локальные оверрайды/анимации результат-слота не генерируют события —
 * baseline синхронизируется вместе с ними). Событие не отменяется:
 * изменения уже применены.
 */
public class GuiSlotChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Gui gui;
    private final Inventory inventory;
    private final int slot;
    private final SlotType slotType;
    private final ItemStack oldItem;
    private final ItemStack newItem;

    /**
     * @param player    игрок сессии (может быть null, если игрок уже
     *                  отключился в окне коалесинга)
     * @param gui       GUI
     * @param inventory инвентарь GUI
     * @param slot      индекс слота
     * @param slotType  тип слота
     * @param oldItem   предмет «до» (null — слот был пуст)
     * @param newItem   предмет «после» (null — слот стал пуст)
     */
    public GuiSlotChangedEvent(Player player, Gui gui, Inventory inventory, int slot,
                               SlotType slotType, ItemStack oldItem, ItemStack newItem) {
        this.player = player;
        this.gui = gui;
        this.inventory = inventory;
        this.slot = slot;
        this.slotType = slotType;
        this.oldItem = oldItem;
        this.newItem = newItem;
    }

    /** Игрок сессии (может быть null). */
    public Player getPlayer() {
        return player;
    }

    /** GUI, в котором произошло изменение. */
    public Gui getGui() {
        return gui;
    }

    /** Инвентарь GUI (с актуальным содержимым). */
    public Inventory getInventory() {
        return inventory;
    }

    /** Индекс изменившегося слота. */
    public int getSlot() {
        return slot;
    }

    /** Тип слота (CONTAINER/CRAFT/FUEL/RESULT). */
    public SlotType getSlotType() {
        return slotType;
    }

    /** Предмет «до» изменения (null — слот был пуст). */
    public ItemStack getOldItem() {
        return oldItem;
    }

    /** Предмет «после» изменения (null — слот стал пуст). */
    public ItemStack getNewItem() {
        return newItem;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
