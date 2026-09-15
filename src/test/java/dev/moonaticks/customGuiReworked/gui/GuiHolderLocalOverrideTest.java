package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты локальных (per-viewer) оверрайдов в {@link GuiHolder}:
 * дизайн, title, локация блока, очистка состояния.
 */
class GuiHolderLocalOverrideTest {

    private static GuiHolder newHolder() {
        // 27 слотов, все DESIGN по умолчанию.
        Gui gui = new Gui("test");
        StorageKey key = StorageKey.global("test.yml");
        return new GuiHolder(gui, key);
    }

    private static ItemStack mockItem() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.IRON_INGOT);
        return item;
    }

    private static ItemStack mockAir() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.AIR);
        return item;
    }

    @Test
    void localDesignSetAndGetIsO1Lookup() {
        GuiHolder holder = newHolder();
        ItemStack item = mockItem();
        holder.setLocalDesign(0, item);
        assertSame(item, holder.getLocalDesign(0), "хранится тот же экземпляр");
        assertEquals(item, holder.localDesigns().get(0));
    }

    @Test
    void localDesignNullOrAirClears() {
        GuiHolder holder = newHolder();
        holder.setLocalDesign(4, mockItem());
        holder.setLocalDesign(4, null);
        assertEquals(null, holder.getLocalDesign(4));

        holder.setLocalDesign(5, mockItem());
        holder.setLocalDesign(5, mockAir());
        assertEquals(null, holder.getLocalDesign(5), "AIR считается сбросом");
    }

    @Test
    void localDesignRejectsPersistableSlots() {
        GuiHolder holder = newHolder();
        holder.gui().setSlotType(3, SlotType.CRAFT);
        holder.gui().setSlotType(6, SlotType.CONTAINER);
        holder.gui().setSlotType(9, SlotType.FUEL);
        // Оверрайды только DESIGN/RESULT — персистентные слоты нельзя
        // подменить виртуальным предметом (риск дюпа через хранилище).
        assertThrows(IllegalArgumentException.class, () -> holder.setLocalDesign(3, mockItem()));
        assertThrows(IllegalArgumentException.class, () -> holder.setLocalDesign(6, mockItem()));
        assertThrows(IllegalArgumentException.class, () -> holder.setLocalDesign(9, mockItem()));
        assertTrue(holder.localDesigns().isEmpty(), "непринятые слоты не запомнились");
    }

    @Test
    void localDesignAllowsResultSlots() {
        GuiHolder holder = newHolder();
        holder.gui().setSlotType(22, SlotType.RESULT);
        ItemStack item = mockItem();
        holder.setLocalDesign(22, item);
        assertSame(item, holder.getLocalDesign(22), "RESULT — допустим для локального результата");
    }

    @Test
    void localDesignRejectsOutOfRange() {
        GuiHolder holder = newHolder();
        assertThrows(IndexOutOfBoundsException.class, () -> holder.setLocalDesign(27, mockItem()));
        assertThrows(IndexOutOfBoundsException.class, () -> holder.setLocalDesign(-1, mockItem()));
    }

    @Test
    void clearLocalDesignAndClearAll() {
        GuiHolder holder = newHolder();
        holder.setLocalDesign(1, mockItem());
        holder.setLocalDesign(7, mockItem());
        holder.clearLocalDesign(1);
        assertEquals(null, holder.getLocalDesign(1));
        assertEquals(1, holder.localDesigns().size());
        holder.clearAllLocalDesigns();
        assertTrue(holder.localDesigns().isEmpty());
    }

    @Test
    void localTitleSetGetClear() {
        GuiHolder holder = newHolder();
        assertEquals(null, holder.getLocalTitle());
        holder.setLocalTitle("§6Фурна 12");
        assertEquals("§6Фурна 12", holder.getLocalTitle());
        holder.setLocalTitle("");
        assertEquals(null, holder.getLocalTitle(), "пустая строка = сброс");
        holder.setLocalTitle("§bБочка");
        holder.clearLocalTitle();
        assertEquals(null, holder.getLocalTitle());
    }

    @Test
    void blockLocationStored() {
        GuiHolder holder = newHolder();
        assertEquals(null, holder.blockLocation());
        Location location = new Location(null, 10, 64, -20);
        holder.setBlockLocation(location);
        assertSame(location, holder.blockLocation());
    }

    @Test
    void clearLocalStateWipesEverything() {
        GuiHolder holder = newHolder();
        holder.setLocalDesign(2, mockItem());
        holder.setLocalTitle("§fCustom");
        holder.setBlockLocation(new Location(null, 1, 2, 3));
        holder.clearLocalState();
        assertTrue(holder.localDesigns().isEmpty());
        assertEquals(null, holder.getLocalTitle());
        assertEquals(null, holder.blockLocation());
    }

    @Test
    void blockKeyHolderStartsWithoutBlockLocation() {
        Gui gui = new Gui("blockgui");
        StorageKey key = new StorageKey(StorageType.BLOCK, "world:1,2,3", "blockgui.yml");
        GuiHolder holder = new GuiHolder(gui, key);
        assertEquals(null, holder.blockLocation(), "локация запоминается явно при открытии");
        assertSame(key, holder.key());
    }
}
