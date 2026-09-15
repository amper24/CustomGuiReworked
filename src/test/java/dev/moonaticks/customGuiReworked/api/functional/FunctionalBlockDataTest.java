package dev.moonaticks.customGuiReworked.api.functional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Тесты {@link FunctionalBlockData} — типизированный KV-стейт блока. */
class FunctionalBlockDataTest {

    private FunctionalBlockData newEmptyData() {
        return new FunctionalBlockData(new java.util.concurrent.ConcurrentHashMap<>(), null);
    }

    @Test
    void typedGettersWithDefaults() {
        FunctionalBlockData data = newEmptyData();
        assertEquals(7, data.getInt("cook", 7));
        assertEquals(1.5, data.getDouble("xp", 1.5));
        assertEquals("none", data.getString("recipe", "none"));
        assertFalse(data.getBoolean("working", false));
        assertTrue(data.getBoolean("on", true));
    }

    @Test
    void typedSettersRoundTrip() {
        FunctionalBlockData data = newEmptyData();
        data.setInt("cook", 42);
        data.setDouble("xp", 0.25);
        data.setBoolean("lit", true);
        data.set("recipe", "stew");

        assertEquals(42, data.getInt("cook", 0));
        assertEquals(0.25, data.getDouble("xp", 0.0));
        assertTrue(data.getBoolean("lit", false));
        assertEquals("stew", data.getString("recipe", ""));
        assertTrue(data.contains("cook"));
        assertTrue(data.keys().containsAll(java.util.Set.of("cook", "xp", "lit", "recipe")));
        assertFalse(data.isEmpty());
    }

    @Test
    void malformedValueFallsBackToDefault() {
        FunctionalBlockData data = newEmptyData();
        data.set("cook", "not-a-number");
        assertEquals(9, data.getInt("cook", 9));
        data.set("lit", "yes");
        assertTrue(data.getBoolean("lit", false));
        data.set("lit", "0");
        assertFalse(data.getBoolean("lit", true));
    }

    @Test
    void removeAndClear() {
        FunctionalBlockData data = newEmptyData();
        data.setInt("a", 1);
        data.set("b", "x");
        data.remove("a");
        assertFalse(data.contains("a"));
        assertEquals(0, data.getInt("a", 0));
        data.clear();
        assertTrue(data.isEmpty());
    }

    @Test
    void nullValueRemovesKey() {
        FunctionalBlockData data = newEmptyData();
        data.set("k", "v");
        data.set("k", null);
        assertFalse(data.contains("k"));
    }
}
