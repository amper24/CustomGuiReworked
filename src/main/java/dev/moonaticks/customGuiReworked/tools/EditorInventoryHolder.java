package dev.moonaticks.customGuiReworked.tools;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class EditorInventoryHolder implements InventoryHolder {
    private final String id;

    public EditorInventoryHolder(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public Inventory getInventory() {
        return null; // Не нужен
    }
}

