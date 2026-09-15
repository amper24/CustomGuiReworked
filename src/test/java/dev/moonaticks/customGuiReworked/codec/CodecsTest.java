package dev.moonaticks.customGuiReworked.codec;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodecsTest {

    /** Тестовый кодек: запоминает переданный payload и возвращает воздух. */
    static final class FakeCodec implements ItemCodec {
        String decodedPayload;
        boolean encodeCalled;

        @Override
        public String tag() {
            return "t1";
        }

        @Override
        public String encode(ItemStack item) {
            encodeCalled = true;
            return "fake";
        }

        @Override
        public ItemStack decode(String payload) {
            decodedPayload = payload;
            return new ItemStack(Material.AIR);
        }
    }

    private FakeCodec fake;

    @BeforeEach
    void setUp() {
        fake = new FakeCodec();
        Codecs.reset();
    }

    @AfterEach
    void tearDown() {
        Codecs.reset();
    }

    @Test
    void activeCodecMustBeInitialized() {
        assertThrows(IllegalStateException.class, Codecs::active);
        Codecs.initialize(fake);
        assertEquals(fake, Codecs.active());
    }

    @Test
    void nullAndAirEncodeToEmptyEvenWithoutCodec() {
        // item == null проверяется до получения активного кодека
        assertEquals("", Codecs.encode(null));
    }

    @Test
    void decodeBlankIsAlwaysSafe() {
        for (String blank : new String[]{null, "", "   ", "{}"}) {
            ItemStack item = assertDoesNotThrow(() -> Codecs.decode(blank));
            assertNotNull(item, "декод пустого payload возвращает воздушный предмет, не null");
        }
    }

    @Test
    void routesByTag() {
        Codecs.register(fake);
        ItemStack item = Codecs.decode("t1:hello-world");
        assertNotNull(item);
        assertEquals("hello-world", fake.decodedPayload, "кодек должен получить payload без префикса");
    }

    @Test
    void unknownTagFallsBackToLegacyWithoutThrowing() {
        ItemStack item = assertDoesNotThrow(() -> Codecs.decode("zz:something"));
        assertNotNull(item);
    }

    @Test
    void garbagePayloadDoesNotThrow() {
        ItemStack item = assertDoesNotThrow(() -> Codecs.decode("not-json-at-all!!!"));
        assertNotNull(item);
    }
}
