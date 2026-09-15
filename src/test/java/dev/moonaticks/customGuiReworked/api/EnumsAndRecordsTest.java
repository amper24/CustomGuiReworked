package dev.moonaticks.customGuiReworked.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumsAndRecordsTest {

    @Test
    void storageTypeFromId() {
        assertEquals(StorageType.BLOCK, StorageType.fromId("block"));
        assertEquals(StorageType.PERSONAL, StorageType.fromId(" PERSONAL "));
        assertEquals(StorageType.GLOBAL, StorageType.fromId("global"));
        assertEquals(StorageType.TEAM, StorageType.fromId("team"));
        assertEquals(StorageType.TEMPORARY, StorageType.fromId("temporary"));
        assertEquals(StorageType.TEMPORARY, StorageType.fromId(null));
        assertEquals(StorageType.TEMPORARY, StorageType.fromId("nonsense"));
    }

    @Test
    void storageTypeLegacyNumbers() {
        assertEquals(StorageType.BLOCK, StorageType.fromLegacyNumber(1));
        assertEquals(StorageType.PERSONAL, StorageType.fromLegacyNumber(2));
        assertEquals(StorageType.GLOBAL, StorageType.fromLegacyNumber(3));
        assertEquals(StorageType.TEAM, StorageType.fromLegacyNumber(4));
        assertEquals(StorageType.TEMPORARY, StorageType.fromLegacyNumber(5));
        assertEquals(StorageType.TEMPORARY, StorageType.fromLegacyNumber(99));
    }

    @Test
    void slotTypeFromLegacy() {
        assertEquals(SlotType.DESIGN, SlotType.fromLegacy(null));
        assertEquals(SlotType.DESIGN, SlotType.fromLegacy("design0"));
        assertEquals(SlotType.DESIGN, SlotType.fromLegacy("DESIGN_12"));
        assertEquals(SlotType.CONTAINER, SlotType.fromLegacy("container_3"));
        assertEquals(SlotType.RESULT, SlotType.fromLegacy("result"));
        assertEquals(SlotType.CRAFT, SlotType.fromLegacy("craft"));
        assertEquals(SlotType.FUEL, SlotType.fromLegacy("fuel_1"));
        assertEquals(SlotType.DESIGN, SlotType.fromLegacy("??? "));
    }

    @Test
    void slotCommandCanonicalizes() {
        SlotCommand c = new SlotCommand(3, "/say hi", -5);
        assertEquals(3, c.slot());
        assertEquals("/say hi", c.command());
        assertEquals(0, c.delay(), "negative delay must be clamped to 0");

        SlotCommand nullCommand = new SlotCommand(0, null, 10);
        assertEquals("", nullCommand.command());
    }

    @Test
    void storageTypeIds() {
        assertEquals("block", StorageType.BLOCK.id());
        assertEquals("personal", StorageType.PERSONAL.id());
        assertEquals("global", StorageType.GLOBAL.id());
        assertEquals("team", StorageType.TEAM.id());
        assertEquals("temporary", StorageType.TEMPORARY.id());
        // round-trip всех значений
        for (StorageType type : StorageType.values()) {
            assertEquals(type, StorageType.fromId(type.id()));
        }
    }

    @Test
    void slotTypeCoverage() {
        // каждая ветка parse должна быть достижима
        assertTrue(java.util.Arrays.stream(SlotType.values())
                .allMatch(t -> SlotType.fromLegacy(t.name()) == t));
        assertFalse(false);
    }

    @Test
    void guiBuilderBuildsModel() {
        Gui gui = GuiBuilder.named("Shop")
                .title("§6Shop")
                .size(18)
                .slot(0, SlotType.DESIGN)
                .slot(9, SlotType.CONTAINER)
                .design(0, "n1:payload")
                .command(9, "give %player% diamond 1", 0)
                .blockId("ia:shop")
                .build();

        assertEquals("shop", gui.name());
        assertEquals(18, gui.slots());
        assertEquals(SlotType.CONTAINER, gui.slotType(9));
        assertEquals("n1:payload", gui.designAt(0));
        assertEquals(1, gui.commandsForSlot(9).size());
        assertTrue(gui.blockIds().contains("ia:shop"));
    }
}
