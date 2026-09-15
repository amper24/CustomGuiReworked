package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Вызывается при клике игрока по слоту GUI (верхний инвентарь),
 * включая клики по дизайн-слотам (кнопкам).
 *
 * <p>Два вида отмены:
 * <ul>
 *   <li>{@link #setCancelled(boolean)} (как у обычного Cancellable) —
 *       запрещает выполнение команд, привязанных к слоту, но сам клик
 *       Minecraft проходит как обычно (предмет можно взять/положить);</li>
 *   <li>{@link #setInteractionCancelled(boolean)} — дополнительно
 *       отменяет исходный {@link InventoryClickEvent}: предметы не
 *       двигаются. Удобно для кнопок с кастомной логикой.</li>
 * </ul>
 */
public class GuiSlotClickEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Gui gui;
    private final Inventory inventory;
    private final int slot;
    private final SlotType slotType;
    private final boolean topInventory;
    private final ClickType click;
    private final InventoryAction action;
    private final ItemStack currentItem;
    private final ItemStack cursor;
    private final int hotbarButton;
    private final InventoryClickEvent handle;
    private boolean cancelled;
    private boolean interactionCancelled;

    public GuiSlotClickEvent(Player player, Gui gui, Inventory inventory, int slot,
                             SlotType slotType, boolean topInventory, ClickType click,
                             InventoryClickEvent handle) {
        this.player = player;
        this.gui = gui;
        this.inventory = inventory;
        this.slot = slot;
        this.slotType = slotType;
        this.topInventory = topInventory;
        this.click = click;
        this.action = handle == null ? null : handle.getAction();
        this.currentItem = handle == null ? null : handle.getCurrentItem();
        this.cursor = handle == null ? null : handle.getCursor();
        this.hotbarButton = handle == null ? -1 : handle.getHotbarButton();
        this.handle = handle;
    }

    public Player getPlayer() {
        return player;
    }

    public Gui getGui() {
        return gui;
    }

    public Inventory getInventory() {
        return inventory;
    }

    /** Индекс кликнутого слота (в пределах верхнего инвентаря). */
    public int getSlot() {
        return slot;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    /** True, если клик состоялся по верхнему инвентарю GUI. */
    public boolean isTopInventory() {
        return topInventory;
    }

    public ClickType getClick() {
        return click;
    }

    /** Исходное действие клика (PICKUP, MOVE_TO_OTHER_INVENTORY, ...). */
    public InventoryAction getAction() {
        return action;
    }

    /** Предмет, находившийся в кликнутом слоте на момент клика. */
    public ItemStack getCurrentItem() {
        return currentItem == null ? null : currentItem.clone();
    }

    /** Предмет, который игрок держал на курсоре на момент клика. */
    public ItemStack getCursor() {
        return cursor == null ? null : cursor.clone();
    }

    /** Номер слота хотбара при NUMBER_KEY (0..8), иначе -1. */
    public int getHotbarButton() {
        return hotbarButton;
    }

    /** Исходное событие Bukkit (может быть {@code null} в тестах). */
    public InventoryClickEvent getHandle() {
        return handle;
    }

    /** Отменить ли сам клик (движение предметов), а не только команды. */
    public boolean isInteractionCancelled() {
        return interactionCancelled;
    }

    /**
     * Отменяет исходный клик Minecraft для этого слота
     * (предметы не перемещаются; команды при этом тоже не выполняются).
     */
    public void setInteractionCancelled(boolean interactionCancelled) {
        this.interactionCancelled = interactionCancelled;
        if (interactionCancelled) {
            this.cancelled = true;
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Отменяет выполнение привязанных к слоту команд (сам клик не трогает). */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
