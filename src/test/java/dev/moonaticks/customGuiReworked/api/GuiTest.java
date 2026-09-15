package dev.moonaticks.customGuiReworked.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiTest {

    @Test
    @DisplayName("normalizeName: lowercase, strip .yml, replace invalid chars")
    void normalizeName() {
        assertEquals("shop", Gui.normalizeName("Shop"));
        assertEquals("shop", Gui.normalizeName("Shop.YML"));
        assertEquals("shop", Gui.normalizeName("shop.yml.yml"));
        // пробел и '#' заменяются на '_', дефис остаётся
        assertEquals("my_shop_1", Gui.normalizeName("My Shop#1"));
        assertEquals("a-b", Gui.normalizeName("a-b"));
        assertEquals("gui", Gui.normalizeName(null));
        assertEquals("gui", Gui.normalizeName("  "));
        // каждый недопустимый символ -> '_' (результат непустой)
        assertEquals("___", Gui.normalizeName("###"));
    }

    @Test
    @DisplayName("clampSlots: округление до ближайшего ряда и границы 9..54")
    void clampSlots() {
        assertEquals(9, Gui.clampSlots(0));
        assertEquals(9, Gui.clampSlots(-10));
        assertEquals(9, Gui.clampSlots(9));
        assertEquals(9, Gui.clampSlots(10));  // ближе к 9
        assertEquals(18, Gui.clampSlots(14)); // граница округления
        assertEquals(18, Gui.clampSlots(18));
        assertEquals(27, Gui.clampSlots(27));
        assertEquals(54, Gui.clampSlots(50));
        assertEquals(54, Gui.clampSlots(99));
    }

    @Test
    @DisplayName("Новый GUI: 27 слотов, все DESIGN, TEMPORARY")
    void defaults() {
        Gui gui = new Gui("test");
        assertEquals("test", gui.name());
        assertEquals(27, gui.slots());
        assertEquals(27, gui.skeleton().size());
        assertTrue(gui.skeleton().stream().allMatch(t -> t == SlotType.DESIGN));
        assertEquals(StorageType.TEMPORARY, gui.storage());
        assertEquals("test.yml", gui.fileName());
        // дизайн-массив обязан быть инициализирован под размер GUI
        assertEquals(27, gui.design().size());
        // иначе запись дизайна в свежий GUI падала бы IndexOutOfBounds
        assertDoesNotThrow(() -> gui.setDesignAt(1, "n1:payload"));
        assertEquals("n1:payload", gui.designAt(1));
        assertDoesNotThrow(() -> gui.setDesignAt(26, "n1:last"));
    }

    @Test
    @DisplayName("slots(): расширение сохраняет данные, новые слоты — DESIGN/пустой дизайн")
    void resizeGrow() {
        Gui gui = new Gui("test");
        gui.setSlotType(0, SlotType.CONTAINER);
        gui.setDesignAt(1, "payload-x");
        gui.slots(54);

        assertEquals(54, gui.slots());
        assertEquals(54, gui.skeleton().size());
        assertEquals(54, gui.design().size());
        assertEquals(SlotType.CONTAINER, gui.slotType(0));
        assertEquals("payload-x", gui.designAt(1));
        assertEquals(SlotType.DESIGN, gui.slotType(27));
        assertEquals("", gui.designAt(27));
    }

    @Test
    @DisplayName("slots(): уменьшение отбрасывает команды за пределами")
    void resizeShrink() {
        Gui gui = new Gui("test");
        gui.slots(54);
        gui.addCommand(new SlotCommand(50, "say hi", 0));
        gui.addCommand(new SlotCommand(5, "say bye", 0));
        gui.setSlotType(50, SlotType.CONTAINER);
        gui.slots(27);

        assertEquals(27, gui.slots());
        assertEquals(1, gui.commands().size());
        assertEquals("say bye", gui.commands().get(0).command());
        assertEquals(SlotType.DESIGN, gui.slotType(50) /* out of range → DESIGN */);
    }

    @Test
    @DisplayName("replaceSkeleton/replaceDesign проверяют размер")
    void replaceValidation() {
        Gui gui = new Gui("test");
        List<SlotType> wrong = List.of(SlotType.CONTAINER);
        assertThrows(IllegalArgumentException.class, () -> gui.replaceSkeleton(wrong));
        assertThrows(IllegalArgumentException.class, () -> gui.replaceDesign(List.of("")));

        List<SlotType> skeleton = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            skeleton.add(i == 0 ? SlotType.RESULT : SlotType.DESIGN);
        }
        assertDoesNotThrow(() -> gui.replaceSkeleton(skeleton));
        assertEquals(SlotType.RESULT, gui.slotType(0));
    }

    @Test
    @DisplayName("setSlotType/setDesignAt: границы индексов")
    void bounds() {
        Gui gui = new Gui("test");
        assertThrows(IndexOutOfBoundsException.class, () -> gui.setSlotType(-1, SlotType.CONTAINER));
        assertThrows(IndexOutOfBoundsException.class, () -> gui.setSlotType(27, SlotType.CONTAINER));
        assertThrows(IndexOutOfBoundsException.class, () -> gui.setDesignAt(99, "x"));
        // неизвестный тип не бросает, а становится DESIGN
        assertDoesNotThrow(() -> gui.replaceSkeleton(gui.skeleton()));
    }

    @Nested
    @DisplayName("Команды слотов")
    class Commands {
        @Test
        @DisplayName("commandsForSlot и removeCommand по индексу внутри слота")
        void addRemove() {
            Gui gui = new Gui("test");
            gui.addCommand(new SlotCommand(10, "a", 0));
            gui.addCommand(new SlotCommand(10, "b", 0));
            gui.addCommand(new SlotCommand(11, "c", 0));

            assertEquals(2, gui.commandsForSlot(10).size());
            assertEquals(1, gui.commandsForSlot(11).size());

            assertTrue(gui.removeCommand(10, 0));
            assertEquals(1, gui.commandsForSlot(10).size());
            assertEquals("b", gui.commandsForSlot(10).get(0).command());
            assertFalse(gui.removeCommand(10, 99));
            assertFalse(gui.removeCommand(5, 0));
        }
    }

    @Nested
    @DisplayName("Привязки блоков")
    class Blocks {
        @Test
        @DisplayName("дубликаты игнорируются, удаление без учёта регистра")
        void bindUnbind() {
            Gui gui = new Gui("test");
            gui.addBlockId("ia:ore");
            gui.addBlockId("ia:ore");
            gui.addBlockId("  ce:chest ");
            assertEquals(2, gui.blockIds().size());

            assertTrue(gui.removeBlockId("IA:ORE"));
            assertEquals(1, gui.blockIds().size());
            assertFalse(gui.removeBlockId("nope"));
        }
    }

    @Test
    @DisplayName("rename нормализует имя")
    void rename() {
        Gui gui = new Gui("old");
        gui.rename("New Name");
        assertEquals("new_name", gui.name());
        assertEquals("new_name.yml", gui.fileName());
    }
}
