package dev.moonaticks.customGuiReworked.codec;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Запасной кодек без NBTAPI на базе нативного бинарного формата Paper:
 * {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}.
 *
 * <p>Бинарные данные передаются как Base64 (payload хранится текстом).
 * Формат сохраняет всю информацию о предмете, которую умеет сериализовать
 * сам Paper (компоненты, PDC, зачарования и т.д.).
 *
 * <p>Тег {@code b2}; старый формат {@code b1} (JSON-карта, существовавший
 * в ранних сборках 2.1.x) читается отдельным {@link LegacyBukkitMapItemCodec}.
 */
public final class BukkitItemCodec implements ItemCodec {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    @Override
    public String tag() {
        return "b2";
    }

    @Override
    public String encode(ItemStack item) {
        try {
            byte[] bytes = item.serializeAsBytes();
            if (bytes == null || bytes.length == 0) {
                return "";
            }
            return ENCODER.encodeToString(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public ItemStack decode(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                return new ItemStack(Material.AIR);
            }
            byte[] bytes = DECODER.decode(payload.trim());
            ItemStack item = ItemStack.deserializeBytes(bytes);
            return item == null ? new ItemStack(Material.AIR) : item;
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }
}
