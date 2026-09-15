package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.api.StorageType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Ключ записи хранилища.
 *
 * <p>Поле {@code owner} зависит от типа:
 * <ul>
 *   <li>{@link StorageType#BLOCK} — «worldName:x,y,z»;</li>
 *   <li>{@link StorageType#PERSONAL} — имя игрока;</li>
 *   <li>{@link StorageType#TEAM} — название команды;</li>
 *   <li>{@link StorageType#GLOBAL} / {@link StorageType#TEMPORARY} — пустая строка (для TEMPORARY — UUID).</li>
 * </ul>
 *
 * <p>Поле {@code table} — имя таблицы (для GUI это {@link dev.moonaticks.customGuiReworked.api.Gui#fileName()}).
 *
 * @param type  тип хранилища
 * @param owner владелец данных
 * @param table имя таблицы
 */
public record StorageKey(StorageType type, String owner, String table) {

    public StorageKey {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        owner = owner == null ? "" : owner;
        if (table == null) {
            throw new IllegalArgumentException("table must not be null");
        }
    }

    /** Человекочитаемое представление ключа. */
    public String stringKey() {
        return type.id() + "|" + owner + "|" + table;
    }

    /** Ключ для данных, привязанных к блоку. */
    public static StorageKey forBlock(Location location, String table) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location/world must not be null");
        }
        World world = location.getWorld();
        return new StorageKey(StorageType.BLOCK,
                world.getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ(),
                table);
    }

    /**
     * Разбирает owner блок-ключа («worldName:x,y,z») в Location.
     *
     * @return Location или null, если мир не загружен/формат неверен
     */
    public static Location blockLocation(String owner) {
        if (owner == null) {
            return null;
        }
        int colon = owner.indexOf(':');
        if (colon <= 0 || colon >= owner.length() - 1) {
            return null;
        }
        String worldName = owner.substring(0, colon);
        String[] coords = owner.substring(colon + 1).split(",");
        if (coords.length != 3) {
            return null;
        }
        final int x;
        final int y;
        final int z;
        try {
            // Координаты валидируем ДО обращения к серверу — мусорный
            // owner не должен приводить даже к поиску мира.
            x = Integer.parseInt(coords[0].trim());
            y = Integer.parseInt(coords[1].trim());
            z = Integer.parseInt(coords[2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    /** Ключ личных данных игрока. */
    public static StorageKey forPlayer(Player player, String table) {
        return new StorageKey(StorageType.PERSONAL, player.getName(), table);
    }

    /** Ключ личных данных по имени игрока. */
    public static StorageKey forPlayerName(String name, String table) {
        return new StorageKey(StorageType.PERSONAL, name, table);
    }

    /** Ключ данных команды. */
    public static StorageKey forTeam(String team, String table) {
        return new StorageKey(StorageType.TEAM, team, table);
    }

    /** Ключ глобальных данных. */
    public static StorageKey global(String table) {
        return new StorageKey(StorageType.GLOBAL, "", table);
    }

    /** Ключ временных данных (в памяти, без диска). */
    public static StorageKey temporary(UUID player, String table) {
        return new StorageKey(StorageType.TEMPORARY, player == null ? "" : player.toString(), table);
    }
}
