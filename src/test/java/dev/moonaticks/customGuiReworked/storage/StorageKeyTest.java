package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.api.StorageType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageKeyTest {

    @Test
    void rejectsNullParts() {
        assertThrows(IllegalArgumentException.class, () -> new StorageKey(null, "o", "t.yml"));
        assertThrows(IllegalArgumentException.class, () -> new StorageKey(StorageType.GLOBAL, "o", null));
    }

    @Test
    void nullOwnerBecomesEmpty() {
        StorageKey key = new StorageKey(StorageType.GLOBAL, null, "table.yml");
        assertEquals("", key.owner());
        assertEquals("global||table.yml", key.stringKey());
    }

    @Test
    void keyFactories() {
        // Эквивалент StorageKey.forPlayer без инстанса Bukkit Player:
        StorageKey personal = StorageKey.forPlayerName("Steve", "shop.yml");
        assertEquals(StorageType.PERSONAL, personal.type());
        assertEquals("Steve", personal.owner());
        assertEquals("shop.yml", personal.table());

        StorageKey global = StorageKey.global("shop.yml");
        assertEquals(StorageType.GLOBAL, global.type());
        assertEquals("", global.owner());

        StorageKey team = StorageKey.forTeam("red", "shop.yml");
        assertEquals(StorageType.TEAM, team.type());
        assertEquals("red", team.owner());
    }

    @Test
    void temporaryKeyUsesPlayerUuid() {
        UUID uuid = UUID.randomUUID();
        StorageKey key = StorageKey.temporary(uuid, "shop.yml");
        assertEquals(StorageType.TEMPORARY, key.type());
        assertEquals(uuid.toString(), key.owner());
        assertTrue(key.stringKey().startsWith("temporary|"));
    }

    @Test
    void tableNameIsKeptAsGiven() {
        StorageKey key = StorageKey.global("shop.yml");
        assertEquals("shop.yml", key.table());
    }

    @Test
    void blockLocationRejectsGarbageWithoutTouchingWorld() {
        assertNull(StorageKey.blockLocation(null));
        assertNull(StorageKey.blockLocation(""));
        assertNull(StorageKey.blockLocation("no-colon"));
        assertNull(StorageKey.blockLocation("world:1,2"));
        assertNull(StorageKey.blockLocation("world:a,b,c"));
        // несуществующий мир (Bukkit.getWorld вернёт null и в реальном окружении)
        assertNull(StorageKey.blockLocation("definitely-not-a-world:1,2,3"));
    }
}
