package dev.moonaticks.customGuiReworked.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кодек-читатель для раннего формата 2.1.x {@code b1} — JSON-карты,
 * которую когда-то производил удалённый {@code ItemFactory.serializeItem}.
 *
 * <p>В современном Paper этого фабричного метода больше нет, поэтому формат
 * переведён на бинарный {@link BukkitItemCodec} ({@code b2}). Класс оставлен
 * только для обратной совместимости: если на диске остались payload с тегом
 * {@code b1}, они конвертируются через {@link ItemStack#deserialize(Map)};
 * новые предметы этим кодеком не пишутся.
 */
public final class LegacyBukkitMapItemCodec implements ItemCodec {

    @Override
    public String tag() {
        return "b1";
    }

    @Override
    public String encode(ItemStack item) {
        // формат больше не используется для записи
        return "";
    }

    @Override
    public ItemStack decode(String payload) {
        try {
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            ItemStack item = ItemStack.deserialize(map);
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
