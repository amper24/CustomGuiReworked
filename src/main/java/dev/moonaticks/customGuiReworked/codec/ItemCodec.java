package dev.moonaticks.customGuiReworked.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Кодирование/декодирование {@link ItemStack} в компактный текстовый payload.
 *
 * <p>Реализации работают с «сырыми» данными без префиксов;
 * подпись payload тегом кодировщика выполняет {@link Codecs}.
 *
 * <p>Кодеки используются на основном потоке (NBT/NMS не тред-сейф)
 * и должны быть быстрыми.
 */
public interface ItemCodec {

    /** Короткий тег реализации, используется в подписи payload (например «n1», «b1»). */
    String tag();

    /**
     * Кодирует предмет.
     *
     * @param item предмет (null/воздушное значение допустимы)
     * @return сырой payload; пустая строка, если предмет пуст или кодирование не удалось
     */
    String encode(ItemStack item);

    /**
     * Декодирует предмет из сырого payload.
     *
     * @param payload сырой payload (без тега)
     * @return предмет; воздух, если payload пуст или повреждён
     */
    ItemStack decode(String payload);
}
