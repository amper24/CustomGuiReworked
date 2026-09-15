package dev.moonaticks.customGuiReworked.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Миграция данных старого формата плагина (1.x).
 *
 * <p>Старый формат — «голый» NBTAPI-JSON без тега кодировщика,
 * в котором числа сериализованы с суффиксами типов («5b», «1.0f»),
 * что не является валидным JSON для стандартных парсеров.
 */
public final class LegacyPayloads {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();
    private static volatile boolean legacyWarned;

    private LegacyPayloads() {
    }

    /**
     * Приводит payload к текущему подписанному формату.
     *
     * @return подписанный payload или пустая строка для пустых данных
     */
    public static String migrate(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("{}")) {
            return "";
        }
        if (raw.startsWith("n1:") || raw.startsWith("b1:") || raw.startsWith("b2:")) {
            return raw;
        }
        ItemStack item = decodeLegacy(raw);
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        // Подписывается активным кодеком
        return Codecs.encode(item);
    }

    /**
     * Декодирует legacy-payload. Сначала пробует NBTAPI
     * (класс загружается рефлективно — только если плагин установлен),
     * затем — упрощённый разбор без NBTAPI.
     */
    public static ItemStack decodeLegacy(String raw) {
        try {
            Class<?> clazz = Class.forName("dev.moonaticks.customGuiReworked.codec.NbtApiItemCodec");
            NbtApiItemCodec codec = (NbtApiItemCodec) clazz.getDeclaredConstructor().newInstance();
            ItemStack item = codec.decode(sanitize(raw));
            if (item != null && item.getType() != Material.AIR) {
                return item;
            }
        } catch (Throwable ignored) {
            // NBTAPI отсутствует — переходим к упрощённому разбору
        }
        return parseMinimal(raw);
    }

    /**
     * NBTAPI пишет числа с суффиксами типов («5b», «-3s», «1.5f», «2.0d») —
     * для стандартного JSON-парсера это невалидные токены.
     * Обрабатываются как кавычки-строки, так и «голые» значения
     * (lookahead защищает строковые значения от повреждения).
     */
    static String sanitize(String json) {
        // Lookahead: символ, на котором может заканчиваться JSON-значение
        // (запятая, закрывающая скобка, конец ввода, пробел/перевод строки).
        // Обязательно символьный класс [..], а не последовательность литералов.
        String value = "[,\\}\\]\\s]|$";
        return json
                .replaceAll("\"(-?\\d+)b\"", "$1")
                .replaceAll("\"(-?\\d+)s\"", "$1")
                .replaceAll("\"(-?\\d+)i\"", "$1")
                .replaceAll("\"(-?\\d+(?:\\.\\d+)?)f\"", "$1")
                .replaceAll("\"(-?\\d+(?:\\.\\d+)?)d\"", "$1")
                .replaceAll("(-?\\d+)b(?=" + value + ")", "$1")
                .replaceAll("(-?\\d+)s(?=" + value + ")", "$1")
                .replaceAll("(-?\\d+)i(?=" + value + ")", "$1")
                .replaceAll("(-?\\d+(?:\\.\\d+)?)f(?=" + value + ")", "$1")
                .replaceAll("(-?\\d+(?:\\.\\d+)?)d(?=" + value + ")", "$1");
    }

    /**
     * Упрощённый разбор legacy-данных без NBTAPI.
     * Сохраняет: тип, количество, damage, название, lore, custom model data.
     * Возможны потери: зачарования, PDC, сложные compound-теги.
     */
    private static ItemStack parseMinimal(String raw) {
        try {
            JsonObject root = JsonParser.parseString(sanitize(raw)).getAsJsonObject();
            JsonElement inner = root.get("itemStack");
            JsonObject source = (inner != null && inner.isJsonObject()) ? inner.getAsJsonObject() : root;

            String id = source.has("id") && source.get("id").isJsonPrimitive()
                    ? source.get("id").getAsString()
                    : "air";
            if (id.startsWith("minecraft:")) {
                id = id.substring("minecraft:".length());
            }
            Material material = Material.matchMaterial(id);
            if (material == null) {
                warnOnce();
                return new ItemStack(Material.AIR);
            }

            int amount = 1;
            if (source.has("Count") && source.get("Count").isJsonPrimitive()) {
                amount = source.get("Count").getAsInt();
            }
            if (amount <= 0) {
                amount = 1;
            }
            ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                JsonObject tag = source.has("tag") && source.get("tag").isJsonObject()
                        ? source.getAsJsonObject("tag")
                        : new JsonObject();
                if (tag.has("display") && tag.get("display").isJsonObject()) {
                    JsonObject display = tag.getAsJsonObject("display");
                    if (display.has("Name") && display.get("Name").isJsonPrimitive()) {
                        meta.displayName(SERIALIZER.deserialize(display.get("Name").getAsString()));
                    }
                    if (display.has("Lore") && display.get("Lore").isJsonArray()) {
                        List<Component> lore = new ArrayList<>();
                        for (JsonElement line : display.getAsJsonArray("Lore")) {
                            if (line.isJsonPrimitive()) {
                                lore.add(SERIALIZER.deserialize(line.getAsString()));
                            }
                        }
                        meta.lore(lore);
                    }
                }
                if (tag.has("custom_model_data") && tag.get("custom_model_data").isJsonPrimitive()) {
                    meta.setCustomModelData(tag.get("custom_model_data").getAsInt());
                }
                if (meta instanceof Damageable damageable
                        && source.has("Damage") && source.get("Damage").isJsonPrimitive()) {
                    damageable.setDamage((short) source.get("Damage").getAsInt());
                }
                item.setItemMeta(meta);
            }
            return item;
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }

    private static void warnOnce() {
        if (!legacyWarned) {
            legacyWarned = true;
            System.err.println("[CustomGuiReworked] Some old-format data could not be converted "
                    + "(NBTAPI is not installed). Install NBTAPI and restart to restore such items.");
        }
    }
}
