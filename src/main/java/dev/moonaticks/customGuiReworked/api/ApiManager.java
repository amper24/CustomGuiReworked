package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.tools.Gui;
import dev.moonaticks.customGuiReworked.tools.TableGUI;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

/**
 * API Manager для CustomGuiReworked плагина
 * Предоставляет простой интерфейс для других плагинов
 */
public class ApiManager {
    private final CustomGuiReworked plugin;
    private final TableEditorManager tableEditorManager;
    private final TableGUI tableGUI;

    public ApiManager(CustomGuiReworked plugin) {
        this.plugin = plugin;
        this.tableEditorManager = plugin.manager;
        this.tableGUI = plugin.tableGUI;
    }

    /**
     * Инициализация API
     */
    public void initialize() {
        plugin.getLogger().info("CustomGuiReworked API инициализирован");
    }

    /**
     * Получить экземпляр плагина
     */
    public CustomGuiReworked getPlugin() {
        return plugin;
    }

    /**
     * Получить менеджер таблиц
     */
    public TableEditorManager getTableEditorManager() {
        return tableEditorManager;
    }

    /**
     * Получить GUI менеджер
     */
    public TableGUI getTableGUI() {
        return tableGUI;
    }

    /**
     * Проверить, существует ли GUI с указанным именем
     */
    public boolean guiExists(String guiName) {
        return tableEditorManager.getGuiByFile(guiName + ".yml") != null;
    }

    /**
     * Получить GUI по имени
     */
    public Gui getGui(String guiName) {
        return tableEditorManager.getGuiByFile(guiName + ".yml");
    }

    /**
     * Получить список всех доступных GUI
     */
    public List<Gui> getAllGuis() {
        return tableEditorManager.guis;
    }

    /**
     * Открыть GUI для игрока
     */
    public void openGui(Player player, String guiName) {
        Gui gui = getGui(guiName);
        if (gui != null) {
            tableGUI.getCustomInventory(player, gui);
        }
    }

    /**
     * Открыть GUI для игрока с указанием локации блока
     */
    public void openGui(Player player, String guiName, Location blockLocation) {
        Gui gui = getGui(guiName);
        if (gui != null) {
            tableGUI.getCustomInventory(player, gui, blockLocation);
        }
    }

    /**
     * Создать новый GUI
     */
    public Gui createGui(String guiName) {
        if (guiExists(guiName)) {
            return getGui(guiName);
        }
        
        Gui gui = tableEditorManager.createFile(guiName + ".yml");
        gui.resetSkeleton();
        gui.resetDesign();
        tableEditorManager.saveToFile(gui);
        return gui;
    }

    /**
     * Удалить GUI
     */
    public boolean deleteGui(String guiName) {
        Gui gui = getGui(guiName);
        if (gui != null) {
            tableEditorManager.removeGui(gui);
            return true;
        }
        return false;
    }

    /**
     * Сохранить GUI
     */
    public void saveGui(Gui gui) {
        tableEditorManager.saveToFile(gui);
    }

    /**
     * Получить последний открытый GUI игрока
     */
    public Gui getLastGui(Player player) {
        return tableEditorManager.lastGui.get(player.getUniqueId());
    }

    /**
     * Получить последний открытый GUI по UUID
     */
    public Gui getLastGui(UUID playerUUID) {
        return tableEditorManager.lastGui.get(playerUUID);
    }

    /**
     * Установить GUI для блока
     */
    public void setBlockGui(Location location, String guiName) {
        Gui gui = getGui(guiName);
        if (gui != null) {
            tableEditorManager.blocksToGui.put(locationToString(location), gui);
        }
    }

    /**
     * Получить GUI блока
     */
    public Gui getBlockGui(Location location) {
        return tableEditorManager.blocksToGui.get(locationToString(location));
    }

    /**
     * Удалить GUI блока
     */
    public void removeBlockGui(Location location) {
        tableEditorManager.blocksToGui.remove(locationToString(location));
    }

    /**
     * Проверить, есть ли у блока GUI
     */
    public boolean hasBlockGui(Location location) {
        return tableEditorManager.blocksToGui.containsKey(locationToString(location));
    }

    /**
     * Получить общий инвентарь по ключу
     */
    public Inventory getSharedInventory(String key) {
        return tableGUI.sharedInventories.get(key);
    }

    /**
     * Добавить общий инвентарь
     */
    public void addSharedInventory(String key, Inventory inventory) {
        tableGUI.addInventory(key, inventory);
    }

    /**
     * Удалить общий инвентарь
     */
    public void removeSharedInventory(String key) {
        tableGUI.removeInventory(key);
    }

    /**
     * Получить ключ инвентаря
     */
    public String getInventoryKey(Inventory inventory) {
        return tableGUI.getKeyByInventory(inventory);
    }

    /**
     * Перезагрузить все GUI
     */
    public void reloadAllGuis() {
        tableEditorManager.loadAllGuis();
    }

    /**
     * Конвертировать локацию в строку
     */
    private String locationToString(Location location) {
        return location.getWorld().getName() + "," + 
               location.getBlockX() + "," + 
               location.getBlockY() + "," + 
               location.getBlockZ();
    }
}
