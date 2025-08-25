package dev.moonaticks.customGuiReworked.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class GetItemNBT {
    // Сериализация ItemStack в JSON
    public static String itemStackToJson(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() == 0) {
            return null; // Игнорируем AIR и пустые предметы
        }
        NBTItem nbtItem = new NBTItem(item);
        nbtItem.setItemStack("itemStack", item);
        return nbtItem.toString();
    }
    // Десериализация JSON в ItemStack
    public static ItemStack jsonToItemStack(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new ItemStack(Material.AIR); // Возвращаем AIR, если JSON пустой или некорректен
        }

        try {
            // Убедимся, что JSON начинается с '{'
            if (!json.trim().startsWith("{")) {
                throw new IllegalArgumentException("Invalid JSON: Expected '{' at position 0");
            }

            // Исправляем JSON, если это необходимо
            json = fixJsonNumbers(json);
            // Создаем NBTContainer
            NBTContainer container = new NBTContainer(json);
            ItemStack item = container.getItemStack("itemStack");

            // Проверяем, что ItemStack не равен null
            if (item == null) {
                throw new IllegalArgumentException("Невозможно создать ItemStack из JSON: " + json);
            }

            return item;
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ItemStack(Material.AIR); // Возвращаем AIR в случае ошибки
        }
    }
    public static String fixJsonNumbers(String json) {
        json = json.replaceAll("\"(-?\\d+)b\"", "$1");    // Целые числа с "b"
        json = json.replaceAll("\"(-?\\d+\\.\\d+)f\"", "$1");  // Числа с "f"
        json = json.replaceAll("\"(-?\\d+\\.\\d+)d\"", "$1");  // Числа с "d"
        return json;
    }
    public static JsonArray inventoryItemsToJsonArray(Inventory inventory, List<String> skeleton) {
        JsonArray array = new JsonArray();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!skeleton.get(slot).contains("design")) {
                ItemStack item = inventory.getItem(slot);
                // Сериализуем предмет в JSON
                String itemData = GetItemNBT.itemStackToJson(item);
                JsonElement element;
                if (itemData == null || itemData.isEmpty() || itemData.equals("{}")) {
                    element = new JsonObject(); // Создаём пустой JSON-объект
                } else {
                    element = JsonParser.parseString(itemData); // Парсим JSON-строку в JsonElement
                }
                array.add(element); // Добавляем JSON-объект в массив
            } else {
                // Добавляем пустой элемент для слотов с "design"
                array.add(new JsonObject());
            }
        }
        return array;
    }
    public static Inventory jsonArrayToInventory(JsonArray array, Inventory inventory, List<String> skeleton) {
        if (array == null || skeleton == null || inventory == null) {
            return inventory; // Возвращаем inventory, если входные данные некорректны
        }
        for (int slot = 0; slot < array.size(); slot++) {
            if (!skeleton.get(slot).contains("design")) {
                JsonElement itemJson = array.get(slot);
                if (itemJson == null || itemJson.isJsonNull()) {
                    continue; // Пропускаем пустые элементы
                }

                // Десериализуем JSON-объект в ItemStack
                ItemStack item = GetItemNBT.jsonToItemStack(itemJson.toString());
                if (item != null) {
                    inventory.setItem(slot, item); // Устанавливаем предмет в слот
                } else {
                    System.out.println("Не удалось загрузить предмет из JSON: " + itemJson);
                }
            }
        }
        return inventory;
    }
}