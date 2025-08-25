package dev.moonaticks.customGuiReworked.managers;

import com.google.gson.JsonArray;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.tools.EditorInventoryHolder;
import dev.moonaticks.customGuiReworked.tools.Gui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.moonaticks.customGuiReworked.tools.GetItemNBT;

import java.util.*;

public class EditorGUIs {
    public final Map<Key, Map<Integer, Inventory>> MainEditors = new HashMap<>();
    TableEditorManager editorManager;
    public EditorGUIs(TableEditorManager editorManager) {
        this.editorManager = editorManager;
    }

    //Функции создания
    private Inventory mainEditor(Gui gui) {
        String title = CustomGuiReworked.languageMap.getOrDefault("mainEditorTitle", "&8[ⒼⓊⓘ ⒺⒹⓘⓉⓄⓇ]: &f") + gui.getFile();
        Inventory inventory = Bukkit.createInventory(new EditorInventoryHolder("MAIN"), 27, Component.text(title));
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i <= 8; i++) {
            inventory.setItem(i, filler);
        }
        ItemStack filler2 = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 18; i <= 26; i++) {
            inventory.setItem(i, filler2);
        }
        // Добавляем основные элементы управления
        inventory.setItem(10, createItem(Material.CHEST, CustomGuiReworked.languageMap.getOrDefault("setSlotCount", "&eЗадать количество слотов")));
        inventory.setItem(11, createItem(Material.BONE, CustomGuiReworked.languageMap.getOrDefault("createSkeleton", "&eСоздать скелет интерфейса")));
        inventory.setItem(12, createItem(Material.PAINTING, CustomGuiReworked.languageMap.getOrDefault("createDesign", "&eСоздать дизайн интерфейса")));
        inventory.setItem(13, createItem(Material.CRAFTING_TABLE, CustomGuiReworked.languageMap.getOrDefault("interfaceSettings", "&eНастройка интерфейса")));
        inventory.setItem(14, createItem(Material.APPLE, CustomGuiReworked.languageMap.getOrDefault("saveDataSettingsButton", "&eНастройка сохранения данных")));
        inventory.setItem(15, createItem(Material.MAP, CustomGuiReworked.languageMap.getOrDefault("previewInterface", "&eПредпросмотр интерфейса")));
        inventory.setItem(16, createItem(Material.NOTE_BLOCK, CustomGuiReworked.languageMap.getOrDefault("configureItemsAdder", "&eНастроить блоки ItemsAdder")));

        return inventory;
    }
    private Inventory DesignGUI(Gui gui) {
        // Implementation to open the design GUI
        int slotCount = gui.getSlots();
        List<String> skeleton = gui.getSkeleton();
        if (skeleton.isEmpty()) {
            return Bukkit.createInventory(null, 9, Component.text(CustomGuiReworked.languageMap.getOrDefault("error", "&c&lERROR")));
        }
        if (slotCount == -1) {
            return Bukkit.createInventory(null, 9, Component.text(CustomGuiReworked.languageMap.getOrDefault("error", "&c&lERROR")));
        }
        String title = CustomGuiReworked.languageMap.getOrDefault("designEditorTitle", "&aДизайн GUI: &f") + gui.getFile();
        Inventory inventory = Bukkit.createInventory(new EditorInventoryHolder("DESIGN"), slotCount, Component.text(title));
        JsonArray itemsDATA = gui.getDesign();
        for (int i = 0; i <= slotCount - 1; i++) {
            if (!skeleton.get(i).contains("design")) {
                inventory.setItem(i, createItem(Material.BEDROCK, CustomGuiReworked.languageMap.getOrDefault("blockedSlot", "&eblocked")));
            }
            else {
                String ItemData = "";
                if(i < itemsDATA.size()) {
                    if(itemsDATA.get(i) != null) {
                        ItemData = itemsDATA.get(i).toString();
                    }
                }
                ItemStack item = GetItemNBT.jsonToItemStack(ItemData);
                inventory.setItem(i, item);
            }
        }
        return inventory;
    }
    private Inventory SkeletonEditorGUI(Gui gui) {
        int slotCount = gui.getSlots();
        if (slotCount < 9 || slotCount > 54) {
            return Bukkit.createInventory(new EditorInventoryHolder("ERROR"), slotCount, Component.text(CustomGuiReworked.languageMap.getOrDefault("error", "&c&lERROR")));
        }
        String title = CustomGuiReworked.languageMap.getOrDefault("skeletonEditorTitle", "&6Скелет GUI: &f") + gui.getFile();
        Inventory skeletonGui = Bukkit.createInventory(new EditorInventoryHolder("SKELETON"), slotCount, Component.text(title));
        // Заполняем инвентарь с нужными предметами
        List<String> _skeleton = gui.getSkeleton();
        for (int i = 0; i < slotCount; i++) {
            if (_skeleton.get(i).contains("design")) {
                skeletonGui.setItem(i, createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, CustomGuiReworked.languageMap.getOrDefault("designSlotLabel", "&aDesign Slot")));
            }
            if (_skeleton.get(i).contains("container")) {
                skeletonGui.setItem(i, createItem(Material.GREEN_STAINED_GLASS_PANE, CustomGuiReworked.languageMap.getOrDefault("containerSlotLabel", "&bContainer Slot")));
            }
            if (_skeleton.get(i).contains("result")) {
                skeletonGui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, CustomGuiReworked.languageMap.getOrDefault("resultSlotLabel", "&eResult Slot")));
            }
            if (_skeleton.get(i).contains("craft")) {
                skeletonGui.setItem(i, createItem(Material.ORANGE_STAINED_GLASS_PANE, CustomGuiReworked.languageMap.getOrDefault("craftSlotLabel", "&6Craft Slot")));
            }
            if (_skeleton.get(i).contains("fuel")) {
                skeletonGui.setItem(i, createItem(Material.RED_STAINED_GLASS_PANE, CustomGuiReworked.languageMap.getOrDefault("fuelSlotLabel", "&cFuel Slot")));
            }
        }
        return skeletonGui;
    }
    private Inventory SaveDataGUI(Gui gui) {
        String title = CustomGuiReworked.languageMap.getOrDefault("saveDataEditorTitle", "&bНастройка сохранения: &f") + gui.getFile();
        Inventory inventory = Bukkit.createInventory(new EditorInventoryHolder("SAVE_DATA"), 27, Component.text(title));
        for(int i = 0; i < 27; i++)
        {
            inventory.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, ""));
        }
        inventory.setItem(11, createItem(Material.CHEST, CustomGuiReworked.languageMap.getOrDefault("blockSaveLabel", "&eСохранение в блоке")));
        inventory.setItem(12, createItem(Material.PLAYER_HEAD, CustomGuiReworked.languageMap.getOrDefault("playerSaveLabel", "&eСохранение в игроке")));
        inventory.setItem(13, createItem(Material.ENDER_CHEST, CustomGuiReworked.languageMap.getOrDefault("globalSaveLabel", "&eГлобальное сохранение для игроков")));
        inventory.setItem( 14, createItem(Material.LIGHT_BLUE_WOOL, CustomGuiReworked.languageMap.getOrDefault("teamSaveLabel", "&eСохранение в команде")));
        inventory.setItem(15, createItem(Material.BARRIER, CustomGuiReworked.languageMap.getOrDefault("noSaveLabel", "&eБез сохранения")));

        // Соответствие цифрам:
        // 1 - Сохранение в блоке
        // 2 - Сохранение в игроке
        // 3 - Глобальное сохранение для игроков
        // 4 - Сохранение в команде
        // 5 - Без сохранения
        int dataMt = gui.getSaveDataMethode() + 10;
        ItemMeta meta;
        meta = Objects.requireNonNull(inventory.getItem(dataMt)).getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        Objects.requireNonNull(inventory.getItem(dataMt)).setItemMeta(meta);
        return inventory;
    }
    private Inventory SizeEditorGUI(Gui gui) {
        String title = CustomGuiReworked.languageMap.getOrDefault("sizeEditorTitle", "&eКоличество слотов: &f") + gui.getFile();
        Inventory sizeEditor = Bukkit.createInventory(new EditorInventoryHolder("SIZE"), 27, Component.text(title));
        ItemStack filler2 = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            sizeEditor.setItem(i, filler2);
        }
        // Заполняем слоты кнопками (например, 9, 18, 27, 36)
        sizeEditor.setItem(10,createSlotItem(CustomGuiReworked.languageMap.getOrDefault("slot9", "&79 слотов"), 9));
        sizeEditor.setItem(11,createSlotItem(CustomGuiReworked.languageMap.getOrDefault("slot18", "&718 слотов"), 18));
        sizeEditor.setItem(12,createSlotItem(CustomGuiReworked.languageMap.getOrDefault("slot27", "&727 слотов"), 27));
        sizeEditor.setItem(14,createSlotItem(CustomGuiReworked.languageMap.getOrDefault("slot36", "&736 слотов"), 36));
        sizeEditor.setItem(15,createSlotItem(CustomGuiReworked.languageMap.getOrDefault("slot45", "&745 слотов"), 45));
        sizeEditor.setItem(16,createSlotItem(CustomGuiReworked.languageMap.getOrDefault("slot54", "&754 слота"), 54));
        return sizeEditor;
    }
    public void openGuiWindow(Player player, Gui gui, int window) {
        editorManager.lastGui.put(player.getUniqueId(), gui);
        switch (window) {
            case 1 -> player.openInventory(SizeEditorGUI(gui));
            case 2 -> player.openInventory(SkeletonEditorGUI(gui));
            case 3 -> player.openInventory(DesignGUI(gui));
            case 4 -> player.openInventory(SaveDataGUI(gui));
            default -> Bukkit.createInventory(null, 9, Component.text("ERROR"));
        }
    }
    //функции открытия
    public void openMain(Player player, Gui gui) {
        // Создаем или получаем инвентарь редактора
        UUID uuid = player.getUniqueId();
        editorManager.lastGui.put(uuid, gui);
        Key key = new Key(uuid, gui);
        Map<Integer,Inventory> inventories = MainEditors.computeIfAbsent(key, k -> {
            Map<Integer, Inventory> newList = new HashMap<>(); // Создаем новый список
            newList.put(0, mainEditor(gui));
            newList.put(1, DesignGUI(gui));
            newList.put(2, SkeletonEditorGUI(gui));
            newList.put(3, SaveDataGUI(gui));
            newList.put(4, SizeEditorGUI(gui));
            return newList;
        });
        player.openInventory(inventories.get(0));
    }


    //прочее
    public ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name)); // Fixed deprecation warning
            item.setItemMeta(meta);
        }
        return item;
    }
    ItemStack createSlotItem(String name, int slotCount) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name)); // Fixed deprecation warning
            item.setItemMeta(meta);
            item.setAmount(slotCount);
        }
        return item;
    }

    public static class Key {
        UUID uuid;
        Gui gui;


        public Key(UUID uuid, Gui gui) {
            this.uuid = uuid;
            this.gui = gui;
        }
    }
}
