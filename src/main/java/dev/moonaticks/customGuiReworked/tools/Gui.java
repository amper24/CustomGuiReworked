package dev.moonaticks.customGuiReworked.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;

import java.util.ArrayList;
import java.util.List;

public class Gui {
    private String file;
    private int slots = 27;
    private List<String> skeleton = new ArrayList<>();
    private JsonArray design = new JsonArray();
    private String title = "Change this";
    private int saveDataMethode = 5;
    private JsonArray commandExecutor = new JsonArray();
    private List<String> customBlockIDs = new ArrayList<>();

    public Gui(String name) {
        this.file = name;
    }
    public String getFile() {
            return file;
        }
    public int getSlots() {
            return slots;
        }
    public List<String> getSkeleton() {
            return skeleton;
        }
    public JsonArray getDesign() {
            return design;
        }
    public String getTitle() {
            return title;
        }
    public int getSaveDataMethode() {
            return saveDataMethode;
        }
    public JsonArray getCommandExecutor() {
            return commandExecutor;
        }
    public List<String> getCustomBlockIDs() {
        return customBlockIDs;
    }

    public void setFile(String file) {
        this.file = file;
    }
    public void setSlots(int slots) {
        this.slots = slots;
    }
    public void setSkeleton(List<String> skeleton) {
        this.skeleton = skeleton;
    }
    public void setDesign(JsonArray design) {
        this.design = design;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setSaveDataMethode(int saveDataMethode) {
        this.saveDataMethode = saveDataMethode;
    }
    public void setCommandExecutor(JsonArray commandExecutor) {
        this.commandExecutor = commandExecutor;
    }
    public void setCustomBlockIDs(List<String> customBlockIDs) {
        this.customBlockIDs = customBlockIDs;
    }

    //Дизайн логика
    public Inventory loadDesign(Player player, Inventory inventory) {
        int slotCount = this.slots;
        List<String> skeleton = this.skeleton;
        if (skeleton.isEmpty()) {
            player.sendMessage(CustomGuiReworked.languageMap.get("InterfaceBrokenSkeleton"));
            player.closeInventory();
            return inventory;
        }
        else if (slotCount > 54 || slotCount < 0) {
            player.sendMessage(CustomGuiReworked.languageMap.get("InterfaceBrokenCount"));
            player.closeInventory();
            return inventory;
        }
        else {
            JsonArray itemsDATA = this.design;
            if (itemsDATA.isEmpty()) {
                player.sendMessage(CustomGuiReworked.languageMap.get("InterfaceBrokenDesigns"));
                player.closeInventory();
            }
            else {
                for(int i = 0; i < slotCount; ++i) {
                    if (skeleton.get(i).toString().contains("design")) {
                        String ItemData = "";
                        if (i < itemsDATA.size() && itemsDATA.get(i) != null) {
                            ItemData = itemsDATA.get(i).toString();
                        }

                        ItemStack item = GetItemNBT.jsonToItemStack(ItemData);
                        int amount = item.getAmount();
                        ItemMeta itemMeta = item.getItemMeta();
                        if (itemMeta != null) {
                            itemMeta.setMaxStackSize(amount);
                            item.setItemMeta(itemMeta);
                        }

                        item.setItemMeta(itemMeta);
                        inventory.setItem(i, item);
                    }
                }

            }
            return inventory;
        }
    }
    //ресеттеры
    public void resetSkeleton() {
        skeleton.clear();
        for(int i = 0; i < this.slots; ++i) {
            skeleton.add("design" + i);
        }
    }
    public void resetDesign() {
        JsonArray designArray = new JsonArray();

        while(designArray.size() < this.slots) {
            designArray.add(GetItemNBT.itemStackToJson(new ItemStack(Material.AIR)));
        }

        Inventory inventory_copy = Bukkit.createInventory(null, this.slots, Component.text("temp"));

        for(int i = 0; i < this.slots; ++i) {
            ItemStack item = inventory_copy.getItem(i);
            String itemData = GetItemNBT.itemStackToJson(item);
            JsonElement element;
            if (itemData != null && !itemData.isEmpty() && !itemData.equals("{}")) {
                element = JsonParser.parseString(itemData);
            } else {
                element = new JsonObject();
            }

            designArray.set(i, element);
        }

        this.setDesign(designArray);
    }
}
