package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;

/**
 * Вызывается перед открытием GUI. Может быть отменён
 * (инвентарь не откроется).
 */
public class GuiOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Gui gui;
    private final Inventory inventory;
    private final StorageKey storageKey;
    private boolean cancelled;

    public GuiOpenEvent(Player player, Gui gui, Inventory inventory) {
        this(player, gui, inventory, null);
    }

    public GuiOpenEvent(Player player, Gui gui, Inventory inventory, StorageKey storageKey) {
        this.player = player;
        this.gui = gui;
        this.inventory = inventory;
        this.storageKey = storageKey;
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

    /** Ключ хранилища, с которым GUI открыт (включая storage override). */
    public StorageKey getStorageKey() {
        return storageKey;
    }

    /** Фактический тип хранения при открытии (override учитывается). */
    public StorageType getStorageType() {
        return storageKey == null ? null : storageKey.type();
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
