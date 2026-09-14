package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder для открытых инвентарей GUI.
 *
 * <p>Через него из любого события однозначно определяется,
 * к какому GUI и к какому хранилищу относится инвентарь —
 * без обратных карт и парсинга ключей (устранив старые
 * «reverseLookupMap» и «lastGui»).
 */
public class GuiHolder implements InventoryHolder {

    private final Gui gui;
    private final StorageKey key;
    private Inventory inventory;

    public GuiHolder(Gui gui, StorageKey key) {
        this.gui = gui;
        this.key = key;
    }

    public Gui gui() {
        return gui;
    }

    public StorageKey key() {
        return key;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
