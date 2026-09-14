package dev.moonaticks.customGuiReworked.codec;

import de.tr7zw.nbtapi.NBTContainer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Эталонный кодек предметов на NBTAPI (de.tr7zw) — новый стандарт
 * экосистемы NBT (тот же автор, что и CraftEngine).
 *
 * <p><b>Важно:</b> класс ссылается на NBTAPI и должен использоваться
 * только при установленном плагине NBTAPI (см. {@link Codecs} и
 * {@link LegacyPayloads} — последний загружает его рефлективно).
 */
public final class NbtApiItemCodec implements ItemCodec {

    private static final String TAG = "itemStack";

    @Override
    public String tag() {
        return "n1";
    }

    @Override
    public String encode(ItemStack item) {
        try {
            NBTContainer container = new NBTContainer();
            container.setItemStack(TAG, item);
            String json = container.toJson();
            return (json == null || json.isBlank()) ? "" : json;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public ItemStack decode(String payload) {
        try {
            NBTContainer container = new NBTContainer(payload);
            ItemStack item = container.getItemStack(TAG);
            return item == null ? new ItemStack(Material.AIR) : item;
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }
}
