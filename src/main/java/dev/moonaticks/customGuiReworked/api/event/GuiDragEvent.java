package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.List;

/**
 * Вызывается, когда игрок растаскивает предмет курсором по слотам
 * верхнего инвентаря GUI.
 *
 * <p>Перетаскивание по дизайн/result-слотам уже заблокировано плагином
 * (событие в этом случае не вызывается). Отмена события отменяет
 * исходный {@link InventoryDragEvent} целиком.
 */
public class GuiDragEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Gui gui;
    private final Inventory inventory;
    /** Слоты верхнего инвентаря, затронутые драгом. */
    private final List<Integer> topSlots;
    private final InventoryDragEvent handle;
    private boolean cancelled;

    public GuiDragEvent(Player player, Gui gui, Inventory inventory,
                        List<Integer> topSlots, InventoryDragEvent handle) {
        this.player = player;
        this.gui = gui;
        this.inventory = inventory;
        this.topSlots = topSlots == null ? List.of() : Collections.unmodifiableList(topSlots);
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

    /** Индексы затронутых слотов верхнего инвентаря GUI. */
    public List<Integer> getTopSlots() {
        return topSlots;
    }

    public InventoryDragEvent getHandle() {
        return handle;
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
