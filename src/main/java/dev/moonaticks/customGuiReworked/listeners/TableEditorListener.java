package dev.moonaticks.customGuiReworked.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.managers.EditorGUIs;
import dev.moonaticks.customGuiReworked.tools.Gui;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;
import dev.moonaticks.customGuiReworked.tools.TableGUI;
import dev.moonaticks.customGuiReworked.tools.GetItemNBT;
import dev.moonaticks.customGuiReworked.tools.EditorInventoryHolder;

import java.util.*;

public class TableEditorListener implements Listener {
    TableEditorManager manager;
    public EditorGUIs editorGUIs;
    TableGUI tableGUI;
    CustomGuiReworked plugin;
    public TableEditorListener(TableEditorManager manager, EditorGUIs editorGUIs, TableGUI tableGUI, CustomGuiReworked plugin) {
        this.manager = manager;
        this.editorGUIs = editorGUIs;
        this.tableGUI = tableGUI;
        this.plugin = plugin;
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        //определяем переменные
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getView().getTopInventory();
        InventoryHolder holder = event.getInventory().getHolder();
        // В зависимости от заголовка выполняем соответствующие действия
        Gui gui = manager.lastGui.get(player.getUniqueId());
        if (holder instanceof EditorInventoryHolder editorHolder) {
            String id = editorHolder.getId();
            switch (id) {
                case "MAIN" -> {
                    event.setCancelled(true);
                    int slot = event.getRawSlot();
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
                    switch (slot) {
                        case 10 -> editorGUIs.openGuiWindow(player, gui, 1);
                        case 11 -> editorGUIs.openGuiWindow(player, gui, 2);
                        case 12 -> editorGUIs.openGuiWindow(player, gui, 3);
                        case 13 -> OpenTitleEditor(player, gui);
                        case 14 -> editorGUIs.openGuiWindow(player, gui, 4);
                        case 15 -> {
                            //открытие
                            tableGUI.getCustomInventory(player, gui);
                        }
                        case 16 -> OpenItemsAdderBlock(player, gui);
                        default -> {}
                    }
                    manager.saveToFile(gui);
                }
                case "DESIGN" -> {
                    int slotCount = gui.getSlots();
                    List<String> skeleton = gui.getSkeleton();
                    int slot = event.getRawSlot();
                    if (slot >= 0 && slot < skeleton.size()) {
                        if (!skeleton.get(slot).contains("design")) {
                            event.setCancelled(true);
                        }
                        else {
                            // Запускаем задачу для сохранения данных в следующем тике
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                JsonArray designArray = new JsonArray();
                                Inventory inventory_copy = event.getInventory();

                                // Убедимся, что designArray имеет нужный размер
                                while (designArray.size() < slotCount) {
                                    designArray.add(GetItemNBT.itemStackToJson(new ItemStack(Material.AIR))); // Заполняем пустыми объектами
                                }

                                // Проходим по всем слотам инвентаря
                                for (int i = 0; i < slotCount; i++) {
                                    ItemStack item = inventory_copy.getItem(i); // Получаем предмет из слота

                                    // Сериализуем предмет в JSON
                                    String itemData = GetItemNBT.itemStackToJson(item);
                                    JsonElement element;
                                    if (itemData == null || itemData.isEmpty() || itemData.equals("{}")) {
                                        element = new JsonObject(); // Создаём пустой JSON-объект
                                    } else {
                                        element = JsonParser.parseString(itemData); // Парсим JSON-строку в JsonElement
                                    }
                                    designArray.set(i, element); // Обновляем слот в массиве
                                }

                                // Сохраняем массив в файл
                                gui.setDesign(designArray);
                                manager.saveToFile(gui);
                            }, 1L);// Задача будет выполнена через 1 тик (0.05 секунды)
                        }
                    }
                }
                case "SKELETON" -> {
                    //объявляем массив
                    event.setCancelled(true);
                    List<String> _skeleton = new ArrayList<>();
                    //Так, это у нас индексы слотов
                    int containerSlots = 0;
                    int resultSlots = 0;
                    int craftSlots = 0;
                    int fuelSlots = 0;
                    int designSlots = 0;
                    //формирование слотов
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                        return; // Прерываем выполнение, если слот пустой
                    }
                    int slot = event.getRawSlot();
                    switch (clickedItem.getType()) {
                        case LIGHT_BLUE_STAINED_GLASS_PANE -> inventory.setItem(slot, editorGUIs.createItem(Material.GREEN_STAINED_GLASS_PANE, "§eContainer Slot"));
                        case GREEN_STAINED_GLASS_PANE -> inventory.setItem(slot, editorGUIs.createItem(Material.BLACK_STAINED_GLASS_PANE, "§Result Slot"));
                        case BLACK_STAINED_GLASS_PANE -> inventory.setItem(slot, editorGUIs.createItem(Material.ORANGE_STAINED_GLASS_PANE, "§eCraft Slot"));
                        case ORANGE_STAINED_GLASS_PANE -> inventory.setItem(slot, editorGUIs.createItem(Material.RED_STAINED_GLASS_PANE, "§eFuel Slot"));
                        case RED_STAINED_GLASS_PANE -> inventory.setItem(slot, editorGUIs.createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§eDesign Slot"));
                        default -> throw new IllegalArgumentException("Unexpected value: " + clickedItem.getType());
                    }
                    JsonArray DesignSlots = new JsonArray();
                    // Сохраняем выбранное количество слотов в конфигурационный файл
                    for (int i = 0; i < inventory.getSize(); i++) {
                        ItemStack item = inventory.getItem(i);
                        assert item != null;
                        switch (item.getType()) {
                            case LIGHT_BLUE_STAINED_GLASS_PANE -> {
                                _skeleton.add("design" + "_" + designSlots);
                                designSlots++;
                            }
                            case GREEN_STAINED_GLASS_PANE -> {
                                _skeleton.add("container" + "_" + containerSlots);
                                containerSlots++;
                            }
                            case BLACK_STAINED_GLASS_PANE -> {
                                _skeleton.add("result" + "_" + resultSlots);
                                resultSlots++;
                            }
                            case ORANGE_STAINED_GLASS_PANE -> {
                                _skeleton.add("craft" + "_" + craftSlots);
                                craftSlots++;
                            }
                            case RED_STAINED_GLASS_PANE -> {
                                _skeleton.add("fuel" + "_" + fuelSlots);
                                fuelSlots++;
                            }
                            default -> throw new IllegalArgumentException("Unexpected value: " + item.getType());
                        }
                    }
                    for (int i = 0; i < inventory.getSize(); i++) {
                        if (!_skeleton.get(i).contains("design")) {
                            DesignSlots.add(Integer.toString(i));
                        }
                    }
                    gui.setSkeleton(_skeleton);
                    manager.saveToFile(gui);
                    editorGUIs.openGuiWindow(player, gui, 2);
                }
                case "SAVE_DATA" -> {
                    event.setCancelled(true);
                    ItemStack clickedItem = event.getCurrentItem();
                    int slot = event.getRawSlot();
                    if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
                    int DataMt = slot - 10;
                    gui.setSaveDataMethode(DataMt);
                    editorGUIs.openMain(player, gui);
                }
                case "SIZE" -> {
                    event.setCancelled(true);
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() ==  Material.GRAY_STAINED_GLASS_PANE) return;
                    int SlotCount = clickedItem.getAmount();
                    // Сохраняем выбранное количество слотов в конфигурационный файл
                    gui.setSlots(SlotCount);
                    gui.resetDesign();
                    gui.resetSkeleton();

                    manager.saveToFile(gui);
                    player.closeInventory();
                    editorGUIs.openMain(player, gui);
                }
                default -> {}
            }
        }
        //загрузка данных перед работой.
        manager.saveToFile(gui);
    }
    private final Map<UUID, Boolean> isTitle = new HashMap<>();
    private final  Map<UUID, Boolean> isItemsAdder = new HashMap<>();

    public void OpenTitleEditor(Player player, Gui gui) {
        player.closeInventory();
        Active.put(player.getUniqueId(), gui);
        isTitle.put(player.getUniqueId(), true);
        player.sendMessage(CustomGuiReworked.languageMap.get("SendTableTitle"));
    }
    public void OpenItemsAdderBlock(Player player, Gui gui) {
        player.closeInventory();
        Active.put(player.getUniqueId(), gui);
        isItemsAdder.put(player.getUniqueId(), true);
        player.sendMessage(CustomGuiReworked.languageMap.get("SendBlockId"));
    }
    private final Map<UUID, Gui> Active = new HashMap<>();
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String lastMessage;
        Gui gui = Active.get(event.getPlayer().getUniqueId());
        if(gui == null) return;
        Player player = event.getPlayer();
        Active.remove(event.getPlayer().getUniqueId());
        lastMessage = event.getMessage();
        if(isTitle.containsKey(player.getUniqueId())) {
            player.sendMessage(CustomGuiReworked.languageMap.get("TableTitleSet").replace("%s", event.getMessage()));
            if(lastMessage.contains(".")) {
                lastMessage = lastMessage.replace("|", "_");
            }
            gui.setTitle(lastMessage);
            isTitle.remove(player.getUniqueId());
        }
        else if(isItemsAdder.containsKey(player.getUniqueId())) {
            List<String> array = gui.getCustomBlockIDs();
            if(array.contains(lastMessage)) {
                player.sendMessage(CustomGuiReworked.languageMap.get("BlockIdRemoved").replace("%s", event.getMessage()));
                array.remove(lastMessage);
                gui.setCustomBlockIDs(array);
            }
            else {
                player.sendMessage(CustomGuiReworked.languageMap.get("BlockIdAdded").replace("%s", event.getMessage()));
                array.add(lastMessage);
                gui.setCustomBlockIDs(array);
            }
            manager.loadAllBlocks();
        }
        event.setCancelled(true);
        editorGUIs.openMain(player, gui);
    }
}
