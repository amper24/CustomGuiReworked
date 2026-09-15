package dev.moonaticks.customGuiReworked.manager;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Владелец инвентаря меню управления.
 *
 * <p>Карта «слот → имя GUI» фиксируется при отрисовке, чтобы обработчик
 * кликов не зависел от состояния в момент события.
 */
public class ManagerHolder implements InventoryHolder {

    public enum Screen {
        LIST,
        OPTIONS
    }

    private Inventory inventory;
    private final Screen screen;
    private final String optionGui;
    private final Map<Integer, String> guiBySlot = new HashMap<>();

    public ManagerHolder(Screen screen, String optionGui) {
        this.screen = screen;
        this.optionGui = optionGui;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Какой экран открыт. */
    public Screen screen() {
        return screen;
    }

    /** GUI, для которого открыт экран опций (для LIST — null). */
    public String optionGui() {
        return optionGui;
    }

    public void put(int slot, String guiName) {
        guiBySlot.put(slot, guiName);
    }

    /** GUI, лежащий в данном слоте (или null). */
    public String guiAt(int slot) {
        return guiBySlot.get(slot);
    }
}
