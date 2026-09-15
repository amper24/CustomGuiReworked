package dev.moonaticks.customGuiReworked.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Утилита для подготовки дизайн-предметов перед размещением в GUI.
 *
 * <p>Каждый дизайн-предмет при загрузке в инвентарь получает:
 * <ul>
 *   <li>{@code maxStackSize} равный его количеству ({@link ItemStack#getAmount()}) —
 *       ванильная механика не сможет добавить к нему ещё предметы ни shift-кликом,
 *       ни при сборке стака double-click'ом;</li>
 *   <li>скрытый PDC-маркер {@link #MARKER_KEY} — благодаря ему
 *       {@link ItemStack#isSimilar(ItemStack)} с обычными предметами игрока
 *       возвращает {@code false}, и double-click не стягивает дизайн-предметы
 *       на курсор (предметы игрока без маркера не сливаются с декоративными).</li>
 * </ul>
 *
 * <p>Маркер не меняет визуальное представление предмета (название, лор,
 * зачарования, кастомные данные остаются как были).
 */
public final class DesignItems {

    /** PDC-ключ маркера дизайн-предмета (значение всегда {@code 1b}). */
    public static final String MARKER_KEY_NAME = "design_item";

    private static NamespacedKey markerKey;

    private DesignItems() {
    }

    /** Инициализация {@link NamespacedKey} (вызывается при включении плагина). */
    public static void init(Plugin plugin) {
        markerKey = new NamespacedKey(plugin, MARKER_KEY_NAME);
    }

    /** Ключ маркера для внешнего использования (например, в тестах). */
    public static NamespacedKey markerKey() {
        return markerKey;
    }

    /**
     * Подготавливает дизайн-предмет для размещения в инвентаре: клонирует стек,
     * ставит {@code maxStackSize} равным его количеству и добавляет скрытый PDC-маркер.
     *
     * @param item исходный предмет (из декодированного дизайна)
     * @return клон-предмет, готовый к установке в инвентарь; {@code null} для AIR/null
     */
    public static ItemStack prepare(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemStack clone = item.clone();
        // max-stack-size == количеству: ваниль не сможет доложить ни одного предмета
        // в этот слот (в т.ч. при shift-переносе и сборке стака).
        int amount = clone.getAmount();
        if (amount < 1) {
            amount = 1;
        }

        // В Paper 1.21.5+ setMaxStackSize переехал из ItemStack в ItemMeta
        // (item-level max stack теперь задаётся через meta).
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(amount);
            // Скрытый PDC-маркер делает дизайн-предмет не-similar обычным предметам игрока
            // → double-click не стягивает декор на курсор.
            if (markerKey != null) {
                meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            }
            clone.setItemMeta(meta);
        }
        return clone;
    }

    /**
     * Клонирует и подготавливает «ожидаемый» предмет для {@code rescueDesignItems},
     * чтобы сравнение {@code isSimilar} корректно проходило даже если дизайн-предмет
     * в слоте уже имеет маркер и установленный maxStackSize.
     */
    public static ItemStack prepareExpected(ItemStack item) {
        return prepare(item);
    }
}
