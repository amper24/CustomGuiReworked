package dev.moonaticks.customGuiReworked.codec;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Статический доступ к активному кодексу предметов.
 *
 * <p>Все сохраняемые payload подписаны тегом активного кодировщика:
 * <ul>
 *   <li>{@code n1:<NBTAPI-JSON>} — эталонный формат (плагин NBTAPI);</li>
 *   <li>{@code b2:<Base64-байты Paper>} — нативный бинарный формат Paper;</li>
 *   <li>{@code b1:<Bukkit-JSON>} — устаревший формат ранних сборок 2.1.x (только чтение).</li>
 * </ul>
 *
 * <p>Благодаря подписи данные, записанные одним кодеком,
 * читаются другим без потерь: декодирование происходит
 * тем кодеком, которым payload был подписан.
 *
 * <p>Данные старого формата плагина (NBTAPI-JSON без тега)
 * прозрачно мигрируются через {@link LegacyPayloads}.
 */
public final class Codecs {

    private static final Map<String, ItemCodec> CODECS = new HashMap<>();
    private static volatile ItemCodec active;

    private Codecs() {
    }

    /** Регистрация дополнительного кодека (по тегу). */
    public static synchronized void register(ItemCodec codec) {
        if (codec != null && codec.tag() != null) {
            CODECS.put(codec.tag(), codec);
        }
    }

    /** Установка активного кодека (вызывается плагином при включении). */
    public static synchronized void initialize(ItemCodec codec) {
        register(codec);
        active = codec;
    }

    /** Сброс (вызывается при выключении плагина). */
    public static synchronized void reset() {
        active = null;
        CODECS.clear();
    }

    /** Активный кодек; бросает исключение, если плагин ещё не инициализирован. */
    public static ItemCodec active() {
        ItemCodec codec = active;
        if (codec == null) {
            throw new IllegalStateException("Codecs are not initialized (is the plugin enabled?)");
        }
        return codec;
    }

    /**
     * Кодирует предмет в подписанный payload.
     *
     * @return payload вида «<тег>:<данные>» или пустая строка для пустых предметов
     */
    public static String encode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return "";
        }
        ItemCodec codec = active();
        String raw = codec.encode(item);
        if (raw == null || raw.isBlank() || raw.equals("{}")) {
            return "";
        }
        // Подпись строго формата «тег:данные» (Base64 у b2 сам по себе
        // не содержит двоеточия, поэтому разделитель обязателен).
        return codec.tag() + ":" + raw;
    }

    /**
     * Декодирует подписанный (или legacy) payload.
     *
     * @return предмет; {@code null} для пустого/битого payload
     *         («нет предмета» — вызывающий код обязан это учитывать)
     */
    public static ItemStack decode(String payload) {
        if (payload == null || payload.isBlank() || payload.equals("{}")) {
            return null;
        }
        int colon = payload.indexOf(':');
        if (colon == 2) {
            ItemCodec codec = CODECS.get(payload.substring(0, 2));
            if (codec != null) {
                ItemStack item = codec.decode(payload.substring(3));
                if (item != null && item.getType() != Material.AIR) {
                    return item;
                }
                return null;
            }
        }
        // Без тега — legacy-формат (плагин 1.x)
        return LegacyPayloads.decodeLegacy(payload);
    }
}
