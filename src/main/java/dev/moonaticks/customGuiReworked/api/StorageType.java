package dev.moonaticks.customGuiReworked.api;

/**
 * Способы хранения данных инвентаря GUI.
 */
public enum StorageType {

    /** Данные привязаны к блоку (мир, x, y, z). При ломании блока предметы выпадают. */
    BLOCK("block"),
    /** У каждого игрока своя копия. */
    PERSONAL("personal"),
    /** Одна общая копия для всех игроков. */
    GLOBAL("global"),
    /** Общая копия для игроков одной Bukkit-команды. */
    TEAM("team"),
    /** Данные не сохраняются: при закрытии предметы возвращаются игроку. */
    TEMPORARY("temporary");

    private final String id;

    StorageType(String id) {
        this.id = id;
    }

    /** Короткий идентификатор (используется в файлах и API). */
    public String id() {
        return id;
    }

    /**
     * Разбирает тип по строковому идентификатору (новый формат файлов).
     *
     * @param id строка, например «personal»
     * @return тип, {@link #TEMPORARY} для неизвестных значений
     */
    public static StorageType fromId(String id) {
        if (id != null) {
            String value = id.trim().toLowerCase();
            for (StorageType type : values()) {
                if (type.id.equals(value)) {
                    return type;
                }
            }
        }
        return TEMPORARY;
    }

    /**
     * Разбирает старый числовой формат (поле «saveDataMethod» 1..5).
     *
     * @param value 1=block, 2=player, 3=global, 4=team, 5=без сохранения
     * @return соответствующий тип
     */
    public static StorageType fromLegacyNumber(int value) {
        return switch (value) {
            case 1 -> BLOCK;
            case 2 -> PERSONAL;
            case 3 -> GLOBAL;
            case 4 -> TEAM;
            default -> TEMPORARY;
        };
    }
}
