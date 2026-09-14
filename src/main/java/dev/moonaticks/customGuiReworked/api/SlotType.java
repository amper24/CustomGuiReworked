package dev.moonaticks.customGuiReworked.api;

/**
 * Типы слотов интерфейса («скелет» GUI).
 *
 * <p>Скелет определяет, как игрок может взаимодействовать с каждым слотом:
 * <ul>
 *   <li>{@link #DESIGN} — декоративный слот, содержимое задаётся дизайном GUI,
 *       любые действия игрока запрещены;</li>
 *   <li>{@link #CONTAINER} — слот хранилища, полное взаимодействие, содержимое сохраняется;</li>
 *   <li>{@link #CRAFT} — слот крафта (семаантика «рецепта»), содержимое сохраняется;</li>
 *   <li>{@link #RESULT} — слот результата: предметы можно забирать, ставить нельзя;</li>
 *   <li>{@link #FUEL} — слот топлива, как контейнер (для кастомных механик).</li>
 * </ul>
 */
public enum SlotType {

    DESIGN,
    CONTAINER,
    CRAFT,
    RESULT,
    FUEL;

    /**
     * Разбирает значение из файла. Поддерживает и старые форматы
     * («design», «design0», «design_12», «container_3», ...), и новые
     * (имя перечисления в любом регистре).
     *
     * @param value строка из конфигурации
     * @return тип слота, {@link #DESIGN} для неизвестных значений
     */
    public static SlotType fromLegacy(String value) {
        if (value == null) {
            return DESIGN;
        }
        String v = value.trim().toLowerCase();
        if (v.startsWith("container")) {
            return CONTAINER;
        }
        if (v.startsWith("result")) {
            return RESULT;
        }
        if (v.startsWith("craft")) {
            return CRAFT;
        }
        if (v.startsWith("fuel")) {
            return FUEL;
        }
        return DESIGN;
    }
}
