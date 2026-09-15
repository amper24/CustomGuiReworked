package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.api.StorageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageViewTest {

    @Test
    void newViewIsNotDirty() {
        StorageView view = new StorageView(StorageKey.global("a.yml"), new String[]{"n1:x"});
        assertFalse(view.dirty);
        assertEquals(1, view.slots.length);
        assertTrue(view.lastUsed > 0);
    }

    @Test
    void markDirtyIncrementsVersion() throws InterruptedException {
        StorageView view = new StorageView(StorageKey.global("a.yml"), null);
        long v0 = view.version;
        view.markDirty();
        assertEquals(v0 + 1, view.version);
        assertTrue(view.dirty);

        long before = view.lastUsed;
        Thread.sleep(2);
        view.touch();
        assertTrue(view.lastUsed >= before);
    }

    @Test
    void nullSlotsBecomeEmptyArray() {
        StorageView view = new StorageView(new StorageKey(StorageType.TEMPORARY, "u", "t.yml"), null);
        assertEquals(0, view.slots.length);
    }
}
