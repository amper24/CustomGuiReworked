package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

/**
 * Вызывается при клике игрока по слоту GUI (верхний инвентарь).
 *
 * <p>Отмена события <b>не</b> отменяет сам клик Minecraft — она
 * запрещает выполнение команд, привязанных к слоту.
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
    private boolean cancelled;

    public GuiSlotClickEvent(Player player, Gui gui, Inventory inventory, int slot,
                             SlotType slotType, boolean topInventory, ClickType click) {
        this.player = player;
        this.gui = gui;
        this.inventory = inventory;
        this.slot = slot;
        this.slotType = slotType;
        this.topInventory = topInventory;
        this.click = click;
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

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

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
