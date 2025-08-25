package dev.moonaticks.customGuiReworked.tools;

import org.bukkit.Location;

/**
 * @param tableName     Название таблицы
 * @param blockLocation Координаты блока (если это интерфейс блока)
 */
public record OpenInterfaceData(String tableName, Location blockLocation) {
}