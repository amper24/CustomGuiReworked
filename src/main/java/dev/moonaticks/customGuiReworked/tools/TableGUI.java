package dev.moonaticks.customGuiReworked.tools;

import com.google.gson.JsonArray;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.managers.DatabaseManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;

import java.util.*;

public class TableGUI {
    TableEditorManager manager;
    CustomGuiReworked plugin;
    DatabaseManager databaseManager;
    // Хранилище общих инвентарей
    public final Map<String, Inventory> sharedInventories = new HashMap<>();
    private final Map<Inventory, String> reverseLookupMap = new HashMap<>(); // Для быстрого поиска

    public void addInventory(String key, Inventory inventory) {
        sharedInventories.put(key, inventory);
        reverseLookupMap.put(inventory, key);
    }

    // При удалении инвентаря:
    public void removeInventory(String key) {
        Inventory inventory = sharedInventories.remove(key);
        reverseLookupMap.remove(inventory);
    }

    // Быстрый поиск ключа:
    public String getKeyByInventory(Inventory inventory) {
        return reverseLookupMap.get(inventory);
    }

    public TableGUI(CustomGuiReworked plugin, TableEditorManager manager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.databaseManager = databaseManager;
    }
    //open and create
    public void getCustomInventory(Player player, Gui gui, Location blockLocation) {

        int saveDataMethod = gui.getSaveDataMethode();

        switch (saveDataMethod) {
            case 1 -> openBlockInventory(player, gui.getFile(), blockLocation); // Инвентарь блока
            case 2 -> openPersonalInventory(player, gui.getFile()); // Личный инвентарь
            case 3 -> openGlobalInventory(player, gui.getFile()); // Глобальный инвентарь
            case 4 -> openTeamInventory(player, gui.getFile()); // Командный инвентарь
            case 5 -> openTemporaryInventory(player, gui.getFile()); // Временный инвентарь
            default -> {
                String message = CustomGuiReworked.languageMap.getOrDefault("InvalidSaveDataMethod", "Неизвестный метод загрузки данных: %s");
                throw new IllegalArgumentException(String.format(message, saveDataMethod));
            }
        }
    }
    public void getCustomInventory(Player player, Gui gui) {
        int saveDataMethod = gui.getSaveDataMethode();

        switch (saveDataMethod) {
            case 1 -> player.sendMessage(CustomGuiReworked.languageMap.getOrDefault("BlockInventoryError", "&cИнвентарь блока не может быть открыт командой")); // Инвентарь блока
            case 2 -> openPersonalInventory(player, gui.getFile()); // Личный инвентарь
            case 3 -> openGlobalInventory(player, gui.getFile()); // Глобальный инвентарь
            case 4 -> openTeamInventory(player, gui.getFile()); // Командный инвентарь
            case 5 -> openTemporaryInventory(player, gui.getFile()); // Временный инвентарь
            default -> {
                String message = CustomGuiReworked.languageMap.getOrDefault("InvalidSaveDataMethod", "Неизвестный метод загрузки данных: %s");
                throw new IllegalArgumentException(String.format(message, saveDataMethod));
            }
        }
    }
    public Inventory openBlockInventory(Player player, String tableName, Location blockLocation) {
        if (blockLocation == null) {
            player.sendMessage(CustomGuiReworked.languageMap.getOrDefault("NoBlockSelected", "&cВы не выбрали блок!"));
            return null;
        }

        // Уникальный ключ для инвентаря блока
        String key = "block|" + blockLocation.getWorld().getName() + "|" +
                blockLocation.getBlockX() + "|" +
                blockLocation.getBlockY() + "|" +
                blockLocation.getBlockZ() + "|" +
                tableName;

        // Создаем или получаем общий инвентарь
        Inventory inventory = sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromBlock(blockLocation, tableName);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });

        player.openInventory(inventory);
        return inventory;
    }
    public Inventory openPersonalInventory(Player player, String tableName) {
        // Уникальный ключ для личного инвентаря
        String key = "personal|" + player.getUniqueId() + "|" + tableName;

        // Создаем или получаем личный инвентарь
        Inventory inventory = sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromPlayer(player, tableName);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });

        player.openInventory(inventory);
        return inventory;
    }
    public Inventory openPersonalInventory(Player player, String tableName, Player target) {
        // Уникальный ключ для личного инвентаря
        String key = "personal|" + target.getUniqueId() + "|" + tableName;

        // Создаем или получаем личный инвентарь
        Inventory inventory = sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromPlayer(player, tableName);
            Inventory inventory1 = createInventory(target, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });

        player.openInventory(inventory);
        return inventory;
    }
    public Inventory openGlobalInventory(Player player, String tableName) {
        // Уникальный ключ для глобального инвентаря
        String key = "global|" + tableName;

        // Создаем или получаем глобальный инвентарь
        Inventory inventory = sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadGlobally(tableName);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });

        player.openInventory(inventory);
        return inventory;
    }
    public Inventory openTeamInventory(Player player, String tableName) {
        // Уникальный ключ для командного инвентаря
        String team = player.getScoreboard().getPlayerTeam(player) != null
                ? Objects.requireNonNull(player.getScoreboard().getPlayerTeam(player)).getName()
                : "defaultTeam_GUARD265";
        String key = "team|" + team + "|" + tableName;

        // Создаем или получаем командный инвентарь
        Inventory inventory = sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromTeam(player, tableName);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });

        player.openInventory(inventory);
        return inventory;
    }
    public Inventory openTeamInventory(Player player, String tableName, String team) {
        // Уникальный ключ для командного инвентаря
        String key = "team|" + team + "|" + tableName;

        // Создаем или получаем командный инвентарь
        Inventory inventory = sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromTeam(player, team);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });

        player.openInventory(inventory);
        return inventory;
    }
    public Inventory openTemporaryInventory(Player player, String tableName) {
        Inventory inventory = createInventory(player, new JsonArray(), tableName);
        String key = "temporary|" + player.getUniqueId() + "|" + System.currentTimeMillis() + "|" + tableName;
        sharedInventories.put(key, inventory);
        reverseLookupMap.put(inventory, key);
        player.openInventory(inventory);
        return inventory;
    }


    //createros
    //По взгляду игрока
    public Inventory createOrGetBlockInventory(String tableName, Location blockLocation) {
        if (blockLocation == null) {
            return null;
        }

        // Уникальный ключ для инвентаря блока
        String key = "block|" + blockLocation.getWorld().getName() + "|" +
                blockLocation.getBlockX() + "|" +
                blockLocation.getBlockY() + "|" +
                blockLocation.getBlockZ() + "|" +
                tableName;

        // Создаем или получаем общий инвентарь
        return sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromBlock(blockLocation, tableName);
            Inventory inventory1 = createInventory(null, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });
    }
    public Inventory createOrGetPersonalInventory(Player player, String tableName) {
        // Уникальный ключ для личного инвентаря
        String key = "personal|" + player.getUniqueId() + "|" + tableName;

        // Создаем или получаем личный инвентарь
        return sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromPlayer(player, tableName);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });
    }
    public Inventory createOrGetGlobalInventory(String tableName) {
        // Уникальный ключ для глобального инвентаря
        String key = "global|" + tableName;

        // Создаем или получаем глобальный инвентарь
        return sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadGlobally(tableName);
            Inventory inventory1 = createInventory(null, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });
    }
    public Inventory createOrGetTeamInventory(Player player, String tableName) {
        // Уникальный ключ для командного инвентаря
        String team = player.getScoreboard().getPlayerTeam(player) != null
                ? Objects.requireNonNull(player.getScoreboard().getPlayerTeam(player)).getName()
                : "defaultTeam_GUARD265";
        String key = "team|" + team + "|" + tableName;

        // Создаем или получаем командный инвентарь
        return sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromTeam(player, tableName);
            Inventory inventory1 = createInventory(player, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });
    }
    public Inventory createOrGetTeamInventory(String tableName, String team) {
        // Уникальный ключ для командного инвентаря
        String key = "team|" + team + "|" + tableName;

        // Создаем или получаем командный инвентарь
        return sharedInventories.computeIfAbsent(key, k -> {
            JsonArray storage = databaseManager.loadFromTeam(null, tableName);
            Inventory inventory1 = createInventory(null, storage, tableName);
            reverseLookupMap.put(inventory1, key);
            return inventory1;
        });
    }


    public void removeTemporaryInventory(Inventory inventory) {
        if (inventory == null) {
            return; // или выбросьте исключение, если это необходимо
        }
        // Удаляем инвентарь из sharedInventories
        sharedInventories.entrySet().removeIf(entry -> entry.getValue().equals(inventory));
        reverseLookupMap.remove(inventory);
    }
    private Inventory createInventory(Player player, JsonArray storage, String tableName) {
        Gui gui = manager.getGuiByFile(tableName);
        // Загрузка данных
        int slotCount = gui.getSlots();
        String title = gui.getTitle();
        List<String> skeleton = gui.getSkeleton();

        // Создаем инвентарь
        Inventory inventory = Bukkit.createInventory(player, slotCount, Component.text(title));

        // Загружаем дизайн
        inventory = gui.loadDesign(player, inventory);

        // Преобразуем JSON в инвентарь
        return GetItemNBT.jsonArrayToInventory(storage, inventory, skeleton);
    }
    public void removeInventoryByLocation(Location location) {
        if (location == null) {
            plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("LocationCannotBeNull", "Локация не может быть null!"));
            return;
        }

        // Формируем часть ключа, связанную с локацией
        String locationKeyPart = "block|" + location.getWorld().getName() + "|" +
                location.getBlockX() + "|" +
                location.getBlockY() + "|" +
                location.getBlockZ() + "|";

        // Ищем ключи, которые начинаются с locationKeyPart
        sharedInventories.keySet().removeIf(key -> key.startsWith(locationKeyPart));
    }
    public void closeAllInventories() {
        for (Inventory inventory : sharedInventories.values()) {
            for (HumanEntity viewer : inventory.getViewers()) {
                viewer.closeInventory();
            }
        }
    }

    public void saveTo(Player player, Gui gui, Inventory inventory) {
        int saveType = gui.getSaveDataMethode();
        JsonArray array = GetItemNBT.inventoryItemsToJsonArray(inventory, gui.getSkeleton());
        switch (saveType) {
            case 1 -> {
                String key = reverseLookupMap.get(inventory);
                Location loc = parseBlockKey(key);
                databaseManager.saveToBlock(loc, gui.getFile(), array);
            }
            case 2 -> databaseManager.saveToPlayer(player, gui.getFile(), array);
            case 3 -> databaseManager.saveGlobally(gui.getFile(), array);
            case 4 -> databaseManager.saveToTeam(player, gui.getFile(), array);
        }
    }
    /**
     * Преобразует ключ инвентаря блока в Location и tableName
     * @param key Ключ в формате "block.<world>.<x>.<y>.<z>.<tableName>"
     * @return Pair<Location, String>, где Location - позиция блока, String - название таблицы
     * @throws IllegalArgumentException если ключ невалиден
     */
    public static Location parseBlockKey(String key) {
        if (key == null || !key.startsWith("block|")) {
            String message = CustomGuiReworked.languageMap.getOrDefault("InvalidBlockKey", "Некорректный ключ блока: %s");
            throw new IllegalArgumentException(String.format(message, key));
        }

        String[] parts = key.split("|");
        if (parts.length != 6) {
            String message = CustomGuiReworked.languageMap.getOrDefault("BlockKeyMustHave6Parts", "Ключ должен содержать 6 частей: %s");
            throw new IllegalArgumentException(String.format(message, key));
        }

        try {
            // Парсинг координат
            String worldName = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int z = Integer.parseInt(parts[4]);

            // Получаем мир (если он загружен)
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                String message = CustomGuiReworked.languageMap.getOrDefault("WorldNotLoaded", "Мир '%s' не загружен");
                throw new IllegalArgumentException(String.format(message, worldName));
            }

            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            String message = CustomGuiReworked.languageMap.getOrDefault("CoordinatesMustBeNumbers", "Координаты в ключе должны быть числами: %s");
            throw new IllegalArgumentException(String.format(message, key), e);
        }
    }
    public String getFileNameByInventory(Inventory inventory) {
        for (Map.Entry<String, Inventory> entry : sharedInventories.entrySet()) {
            if (entry.getValue().equals(inventory)) {
                String key = entry.getKey();
                // Извлекаем имя файла с расширением из ключа
                int lastPipeIndex = key.lastIndexOf('|');
                if (lastPipeIndex != -1) {
                    return key.substring(lastPipeIndex + 1);
                }
            }
        }
        return null;
    }
    public Gui getGuiByInventory(Inventory inventory) {
        String fileName = getFileNameByInventory(inventory);
        return manager.getGuiByFile(fileName);
    }
}