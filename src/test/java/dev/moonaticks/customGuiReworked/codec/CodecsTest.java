package dev.moonaticks.customGuiReworked.codec;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты кодеков не поднимают сервер: ItemStack заменён моком
 * (конструирование реального ItemStack в paper-api требует запущенного
 * сервера с реестрами), пустые/битые payload должны давать null.
 */
class CodecsTest {

    /** Тестовый кодек: запоминает переданный payload. */
    static final class FakeCodec implements ItemCodec {
        String decodedPayload;
        ItemStack decodeResult;

        @Override
        public String tag() {
            return "t1";
        }

        @Override
        public String encode(ItemStack item) {
            return "fake";
        }

        @Override
        public ItemStack decode(String payload) {
            decodedPayload = payload;
            return decodeResult;
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

    private static ItemStack mockItem(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        return item;
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
        ItemStack air = mockItem(Material.AIR, 1);
        Codecs.initialize(fake);
        assertEquals("", Codecs.encode(air));
    }

    @Test
    void realItemEncodedWithTagPrefix() {
        Codecs.initialize(fake);
        assertEquals("t1:fake", Codecs.encode(mockItem(Material.DIAMOND, 2)));
    }

    @Test
    void decodeBlankIsAlwaysSafeAndEmpty() {
        Codecs.initialize(fake);
        for (String blank : new String[]{null, "", "   ", "{}"}) {
            ItemStack item = assertDoesNotThrow(() -> Codecs.decode(blank));
            assertNull(item, "декод пустого payload должен давать null");
        }
    }

    @Test
    void routesByTag() {
        ItemStack result = mockItem(Material.GOLD_INGOT, 1);
        fake.decodeResult = result;
        Codecs.register(fake);
        ItemStack item = Codecs.decode("t1:hello-world");
        assertSame(result, item);
        assertEquals("hello-world", fake.decodedPayload, "кодек должен получить payload без префикса");
    }

    @Test
    void unknownCodecResultIsEmpty() {
        // кодек вернул null — наружу тоже null, без падений
        Codecs.register(fake);
        assertNull(Codecs.decode("t1:whatever"));
    }

    @Test
    void unknownTagFallsBackToLegacyWithoutThrowing() {
        assertDoesNotThrow(() -> Codecs.decode("zz:something"));
        assertNull(Codecs.decode("zz:something"));
    }

    @Test
    void garbagePayloadDoesNotThrow() {
        assertDoesNotThrow(() -> Codecs.decode("not-json-at-all!!!"));
        assertNull(Codecs.decode("not-json-at-all!!!"));
    }
}
