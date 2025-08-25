package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.tools.Gui;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

/**
 * Статический API класс для CustomGuiReworked
 * Предоставляет простой доступ к функциональности плагина
 */
public class CustomGuiAPI {
    private static CustomGuiReworked plugin;
    private static ApiManager apiManager;

    /**
     * Инициализация API (вызывается автоматически плагином)
     */
    public static void initialize(CustomGuiReworked mainPlugin) {
        plugin = mainPlugin;
        apiManager = mainPlugin.apiManager;
    }

    /**
     * Проверить, инициализирован ли API
     */
    public static boolean isInitialized() {
        return plugin != null && apiManager != null;
    }

    /**
     * Получить экземпляр плагина
     */
    public static CustomGuiReworked getPlugin() {
        return plugin;
    }

    /**
     * Получить API менеджер
     */
    public static ApiManager getApiManager() {
        return apiManager;
    }

    // ========== GUI УПРАВЛЕНИЕ ==========

    /**
     * Проверить, существует ли GUI
     */
    public static boolean guiExists(String guiName) {
        checkInitialization();
        return apiManager.guiExists(guiName);
    }

    /**
     * Получить GUI по имени
     */
    public static Gui getGui(String guiName) {
        checkInitialization();
        return apiManager.getGui(guiName);
    }

    /**
     * Получить список всех GUI
     */
    public static List<Gui> getAllGuis() {
        checkInitialization();
        return apiManager.getAllGuis();
    }

    /**
     * Создать новый GUI
     */
    public static Gui createGui(String guiName) {
        checkInitialization();
        return apiManager.createGui(guiName);
    }

    /**
     * Удалить GUI
     */
    public static boolean deleteGui(String guiName) {
        checkInitialization();
        return apiManager.deleteGui(guiName);
    }

    /**
     * Сохранить GUI
     */
    public static void saveGui(Gui gui) {
        checkInitialization();
        apiManager.saveGui(gui);
    }

    // ========== ОТКРЫТИЕ GUI ==========

    /**
     * Открыть GUI для игрока
     */
    public static void openGui(Player player, String guiName) {
        checkInitialization();
        apiManager.openGui(player, guiName);
    }

    /**
     * Открыть GUI для игрока с указанием блока
     */
    public static void openGui(Player player, String guiName, Location blockLocation) {
        checkInitialization();
        apiManager.openGui(player, guiName, blockLocation);
    }

    // ========== ИГРОКИ ==========

    /**
     * Получить последний GUI игрока
     */
    public static Gui getLastGui(Player player) {
        checkInitialization();
        return apiManager.getLastGui(player);
    }

    /**
     * Получить последний GUI по UUID
     */
    public static Gui getLastGui(UUID playerUUID) {
        checkInitialization();
        return apiManager.getLastGui(playerUUID);
    }

    // ========== БЛОКИ ==========

    /**
     * Установить GUI для блока
     */
    public static void setBlockGui(Location location, String guiName) {
        checkInitialization();
        apiManager.setBlockGui(location, guiName);
    }

    /**
     * Получить GUI блока
     */
    public static Gui getBlockGui(Location location) {
        checkInitialization();
        return apiManager.getBlockGui(location);
    }

    /**
     * Удалить GUI блока
     */
    public static void removeBlockGui(Location location) {
        checkInitialization();
        apiManager.removeBlockGui(location);
    }

    /**
     * Проверить, есть ли у блока GUI
     */
    public static boolean hasBlockGui(Location location) {
        checkInitialization();
        return apiManager.hasBlockGui(location);
    }

    // ========== ИНВЕНТАРИ ==========

    /**
     * Получить общий инвентарь
     */
    public static Inventory getSharedInventory(String key) {
        checkInitialization();
        return apiManager.getSharedInventory(key);
    }

    /**
     * Добавить общий инвентарь
     */
    public static void addSharedInventory(String key, Inventory inventory) {
        checkInitialization();
        apiManager.addSharedInventory(key, inventory);
    }

    /**
     * Удалить общий инвентарь
     */
    public static void removeSharedInventory(String key) {
        checkInitialization();
        apiManager.removeSharedInventory(key);
    }

    /**
     * Получить ключ инвентаря
     */
    public static String getInventoryKey(Inventory inventory) {
        checkInitialization();
        return apiManager.getInventoryKey(inventory);
    }

    // ========== УТИЛИТЫ ==========

    /**
     * Перезагрузить все GUI
     */
    public static void reloadAllGuis() {
        checkInitialization();
        apiManager.reloadAllGuis();
    }

    /**
     * Проверить инициализацию API
     */
    private static void checkInitialization() {
        if (!isInitialized()) {
            throw new IllegalStateException("CustomGuiAPI не инициализирован! Убедитесь, что плагин CustomGuiReworked загружен.");
        }
    }

    // ========== УДОБНЫЕ МЕТОДЫ ==========

    /**
     * Быстрое создание и открытие GUI
     */
    public static void createAndOpenGui(Player player, String guiName) {
        checkInitialization();
        Gui gui = createGui(guiName);
        if (gui != null) {
            openGui(player, guiName);
        }
    }

    /**
     * Быстрое создание и открытие GUI с блоком
     */
    public static void createAndOpenGui(Player player, String guiName, Location blockLocation) {
        checkInitialization();
        Gui gui = createGui(guiName);
        if (gui != null) {
            openGui(player, guiName, blockLocation);
        }
    }

    /**
     * Проверить, открыт ли у игрока GUI
     */
    public static boolean hasOpenGui(Player player) {
        return player.getOpenInventory() != null && 
               player.getOpenInventory().getTopInventory() != null;
    }

    /**
     * Получить количество созданных GUI
     */
    public static int getGuiCount() {
        checkInitialization();
        return apiManager.getAllGuis().size();
    }

    /**
     * Получить список имен всех GUI
     */
    public static List<String> getGuiNames() {
        checkInitialization();
        return apiManager.getAllGuis().stream()
                .map(gui -> gui.getFile().replace(".yml", ""))
                .toList();
    }
}
