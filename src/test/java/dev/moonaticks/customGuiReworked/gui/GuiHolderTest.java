package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiHolderTest {

    private GuiHolder newHolder() {
        Gui gui = new Gui("test");
        StorageKey key = StorageKey.global("test.yml");
        return new GuiHolder(gui, key);
    }

    @Test
    void persistableSlotTypes() {
        assertTrue(GuiHolder.isPersistable(SlotType.CONTAINER));
        assertTrue(GuiHolder.isPersistable(SlotType.CRAFT));
        assertTrue(GuiHolder.isPersistable(SlotType.FUEL));
        assertFalse(GuiHolder.isPersistable(SlotType.DESIGN));
        assertFalse(GuiHolder.isPersistable(SlotType.RESULT));
    }

    @Test
    void trackedSlotTypesIncludeResult() {
        // Отслеживаются персистентные слоты + RESULT (для GuiSlotChangedEvent),
        // DESIGN — нет.
        assertTrue(GuiHolder.isTracked(SlotType.CONTAINER));
        assertTrue(GuiHolder.isTracked(SlotType.CRAFT));
        assertTrue(GuiHolder.isTracked(SlotType.FUEL));
        assertTrue(GuiHolder.isTracked(SlotType.RESULT));
        assertFalse(GuiHolder.isTracked(SlotType.DESIGN));
    }

    @Test
    void sessionPlayerIsStored() {
        GuiHolder holder = newHolder();
        assertNull(holder.player());
        java.util.UUID uuid = java.util.UUID.randomUUID();
        holder.setPlayer(uuid);
        assertSame(uuid, holder.player());
    }

    @Test
    void holdsGuiAndKey() {
        Gui gui = new Gui("test");
        StorageKey key = new StorageKey(StorageType.TEAM, "red", "test.yml");
        GuiHolder holder = new GuiHolder(gui, key);
        assertSame(gui, holder.gui());
        assertSame(key, holder.key());
        assertNull(holder.getInventory(), "до attach инвентаря нет");
    }

    @Test
    void candidatesAccumulateAndDrain() {
        GuiHolder holder = newHolder();
        holder.addCandidate(3);
        holder.addCandidate(3);
        holder.addCandidate(7);
        Collection<Integer> drained = holder.drainCandidates();
        assertTrue(drained.contains(3));
        assertTrue(drained.contains(7));
        assertEquals(2, drained.size());
        // после drain список пуст
        assertEquals(0, holder.drainCandidates().size());
    }

    @Test
    void allCandidatesWinsAndDrainsAsNull() {
        GuiHolder holder = newHolder();
        holder.addCandidate(2);
        holder.addAllCandidates();
        assertNull(holder.drainCandidates(), "флаг «все слоты» дранится как null");
        // после drain набор кандидатов чист
        holder.addCandidate(1);
        assertEquals(1, holder.drainCandidates().size());
    }

    @Test
    void reconcileQueueFlag() {
        GuiHolder holder = newHolder();
        assertFalse(holder.isReconcileQueued());
        holder.setReconcileQueued(true);
        assertTrue(holder.isReconcileQueued());
    }

    @Test
    void baselineIsStoredAndMutable() {
        GuiHolder holder = newHolder();
        assertNull(holder.baseline());
        String[] baseline = {"n1:a", null, ""};
        holder.initBaseline(baseline);
        assertSame(baseline, holder.baseline());
    }
}
