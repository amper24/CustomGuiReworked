package dev.moonaticks.customGuiReworked.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyPayloadsTest {

    @Test
    void sanitizeQuotedTypedNumbers() {
        assertEquals("{\"a\":5}", LegacyPayloads.sanitize("{\"a\":\"5b\"}"));
        assertEquals("{\"a\":5}", LegacyPayloads.sanitize("{\"a\":\"5s\"}"));
        assertEquals("{\"a\":5}", LegacyPayloads.sanitize("{\"a\":\"5i\"}"));
        assertEquals("{\"a\":1.5}", LegacyPayloads.sanitize("{\"a\":\"1.5f\"}"));
        assertEquals("{\"a\":1.5}", LegacyPayloads.sanitize("{\"a\":\"1.5d\"}"));
        // отрицательные
        assertEquals("{\"a\":-3}", LegacyPayloads.sanitize("{\"a\":\"-3b\"}"));
    }

    @Test
    void sanitizeBareTypedNumbers() {
        assertEquals("[1,2,3]", LegacyPayloads.sanitize("[1b,2b,3b]"));
        assertEquals("[1.5]", LegacyPayloads.sanitize("[1.5f]"));
        // в конце значения (перед закрытием массива/объекта и в конце строки)
        assertEquals("[1]", LegacyPayloads.sanitize("[1b]"));
        assertEquals("{\"n\":5}", LegacyPayloads.sanitize("{\"n\":5b}"));
        assertEquals("5", LegacyPayloads.sanitize("5b"));
        // строковые значения с похожими символами не повреждаются:
        // после суффикса идёт буква, а не разделитель JSON
        assertEquals("{\"x\":\"abc5bX\"}", LegacyPayloads.sanitize("{\"x\":\"abc5bX\"}"));
    }

    @Test
    void migrateKeepsTaggedPayloadsUntouched() {
        String n1 = "n1:{\"itemStack\":{}}";
        String b1 = "b1:{\"v\":1}";
        String b2 = "b2:QUJD";
        assertEquals(n1, LegacyPayloads.migrate(n1));
        assertEquals(b1, LegacyPayloads.migrate(b1));
        assertEquals(b2, LegacyPayloads.migrate(b2));
    }

    @Test
    void migrateBlankBecomesEmpty() {
        assertEquals("", LegacyPayloads.migrate(null));
        assertEquals("", LegacyPayloads.migrate("   "));
        assertEquals("", LegacyPayloads.migrate("{}"));
    }
}
