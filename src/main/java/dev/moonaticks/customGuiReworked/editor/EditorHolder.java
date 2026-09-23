package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.api.Gui;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder для экранов редактора.
 */
public class EditorHolder implements InventoryHolder {

    /** Экраны редактора. */
    public enum Screen {
        MAIN, SIZE, SKELETON, DESIGN, STORAGE, BLOCKS, CATEGORY
    }

    private final Gui gui;
    private final Screen screen;
    private Inventory inventory;
    private final Map<Integer, String> categories = new HashMap<>();

    public EditorHolder(Gui gui, Screen screen) {
        this.gui = gui;
        this.screen = screen;
    }

    public Gui gui() {
        return gui;
    }

    public Screen screen() {
        return screen;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void putCategory(int slot, String id) {
        categories.put(slot, id);
    }

    public String categoryAt(int slot) {
        return categories.get(slot);
    }
}
