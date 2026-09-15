package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiEventsTest {

    private final Gui gui = new Gui("shop");
    private final StorageKey key = StorageKey.global("shop.yml");

    @Test
    void openEventCancellationAndKey() {
        GuiOpenEvent event = new GuiOpenEvent(null, gui, null, key);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertEquals(StorageType.GLOBAL, event.getStorageType());
        assertEquals(key, event.getStorageKey());
        assertNull(new GuiOpenEvent(null, gui, null).getStorageKey());
    }

    @Test
    void closeEventExposesKey() {
        GuiCloseEvent event = new GuiCloseEvent(null, gui, null, key);
        assertEquals(gui, event.getGui());
        assertEquals(StorageType.GLOBAL, event.getStorageType());
    }

    @Test
    void slotClickEventCancelCommandsOnly() {
        GuiSlotClickEvent event = new GuiSlotClickEvent(
                null, gui, null, 12, SlotType.CONTAINER, true,
                ClickType.LEFT, null);
        assertEquals(12, event.getSlot());
        assertEquals(SlotType.CONTAINER, event.getSlotType());
        assertEquals(ClickType.LEFT, event.getClick());
        assertTrue(event.isTopInventory());
        assertFalse(event.isCancelled());
        assertFalse(event.isInteractionCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertFalse(event.isInteractionCancelled(), "отмена команд не должна отменять клик");
    }

    @Test
    void slotClickEventInteractionCancelImpliesCommandCancel() {
        GuiSlotClickEvent event = new GuiSlotClickEvent(
                null, gui, null, 0, SlotType.DESIGN, true,
                ClickType.RIGHT, null);
        event.setInteractionCancelled(true);
        assertTrue(event.isInteractionCancelled());
        assertTrue(event.isCancelled());
        assertNull(event.getAction());
        assertNull(event.getCursor());
        assertNull(event.getCurrentItem());
        assertEquals(-1, event.getHotbarButton());
        assertNull(event.getHandle());
    }

    @Test
    void handlerListsArePresent() {
        // статические getHandlerList требуются Bukkit для регистрации
        assertTrue(GuiOpenEvent.getHandlerList() != null);
        assertTrue(GuiCloseEvent.getHandlerList() != null);
        assertTrue(GuiSlotClickEvent.getHandlerList() != null);
        assertTrue(GuiDragEvent.getHandlerList() != null);
    }

    @Test
    void dragEventSlotSnapshot() {
        GuiDragEvent event = new GuiDragEvent(null, gui, null, java.util.List.of(1, 2, 3), null);
        assertEquals(3, event.getTopSlots().size());
        // список неизменяем
        try {
            event.getTopSlots().add(4);
            throw new AssertionError("drag slots must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
}
