package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.api.Gui;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder для экранов редактора.
 */
public class EditorHolder implements InventoryHolder {

    /** Экраны редактора. */
    public enum Screen {
        MAIN, SIZE, SKELETON, DESIGN, STORAGE, BLOCKS
    }

    private final Gui gui;
    private final Screen screen;
    private Inventory inventory;

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
}
