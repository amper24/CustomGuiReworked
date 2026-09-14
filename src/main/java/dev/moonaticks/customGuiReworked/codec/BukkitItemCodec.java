package dev.moonaticks.customGuiReworked.codec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Запасной кодек без NBTAPI на базе
 * {@link org.bukkit.inventory.ItemFactory#serializeItem} /
 * {@link org.bukkit.inventory.ItemFactory#deserializeItem}.
 *
 * <p>Сохраняет стандартные данные предмета (тип, количество, lore,
 * зачарования, custom model data, damage, PDC и legacy-NBT, который
 * Paper сериализует в формате serializeItem).
 */
public final class BukkitItemCodec implements ItemCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Override
    public String tag() {
        return "b1";
    }

    @Override
    public String encode(ItemStack item) {
        try {
            Map<String, Object> map = Bukkit.getItemFactory().serializeItem(item);
            if (map == null || map.isEmpty()) {
                return "";
            }
            return GSON.toJson(map);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public ItemStack decode(String payload) {
        try {
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            ItemStack item = Bukkit.getItemFactory().deserializeItem(map);
            return item == null ? new ItemStack(Material.AIR) : item;
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }

    private static Object fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement item : array) {
                list.add(fromJson(item));
            }
            return list;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                Number number = element.getAsNumber();
                if (number instanceof Double d) {
                    return (d == Math.rint(d) && Math.abs(d) < 1e15) ? d.longValue() : d;
                }
                if (number instanceof Float f) {
                    return f.intValue();
                }
                return number;
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            return element.getAsString();
        }
        return null;
    }
}
