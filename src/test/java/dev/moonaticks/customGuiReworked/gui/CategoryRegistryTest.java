package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.GuiCategory;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryRegistryTest {

    @TempDir
    Path dir;

    @AfterEach
    void cleanup() {
        SlotType.clearCustom();
    }

    @Test
    void categoryDescriptionsPersistButRuntimeOnesDoNot() {
        CategoryRegistry categories = new CategoryRegistry(dir.toFile(), Logger.getLogger("test"));
        GuiCategory machines = new GuiCategory("addon:Machines", "§6Machines", Material.FURNACE,
                "§7Smelting and processing");
        categories.register(machines, true);
        categories.register(new GuiCategory("runtime", "Only now"), false);
        assertEquals(2, categories.all().size());
        assertThrows(IllegalArgumentException.class, () -> new GuiCategory("none", "Cannot override"));
        assertThrows(IllegalArgumentException.class, () -> new GuiCategory("../outside", "Invalid"));

        CategoryRegistry restarted = new CategoryRegistry(dir.toFile(), Logger.getLogger("test"));
        restarted.loadAll();
        assertEquals(machines, restarted.get("ADDON:machines"));
        assertEquals(Material.FURNACE, restarted.get("addon:machines").icon());
        assertNull(restarted.get("runtime"));
        assertTrue(restarted.unregister("addon:machines"));
        restarted.loadAll();
        assertNull(restarted.get("addon:machines"));
    }

    @Test
    void runtimeOnlyCategoryDoesNotCreateMetadataFile() {
        CategoryRegistry categories = new CategoryRegistry(dir.toFile(), null);
        categories.register(new GuiCategory("addon:runtime", "Runtime"), false);
        assertFalse(new File(dir.toFile(), "categories.yml").exists());
        categories.loadAll(); // описания в памяти остаются при /gui reload
        assertNotNull(categories.get("addon:runtime"));
    }

    @Test
    void guiCategoryAndUnknownSlotIdSurviveFileRoundTripAndLateRegistration() {
        CustomGuiReworked plugin = mock(CustomGuiReworked.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        GuiRegistry registry = new GuiRegistry(plugin, dir.toFile());
        Gui gui = new Gui("my_machine");
        assertEquals(GuiCategory.NONE, gui.category());
        gui.category("MyAddon:Machines");
        SlotType unknown = SlotType.fromLegacy("myaddon:coil");
        gui.setSlotType(13, unknown);
        registry.save(gui);

        File savedFile = new File(dir.toFile(), "tables/my_machine.yml");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(savedFile);
        assertEquals("myaddon:machines", saved.getString("category"));
        assertEquals("myaddon:coil", saved.getStringList("skeleton").get(13));

        registry.loadAll();
        Gui loaded = registry.get("my_machine");
        assertNotNull(loaded);
        assertEquals("myaddon:machines", loaded.category());
        assertSame(unknown, loaded.slotType(13));
        assertTrue(loaded.slotType(13).isDecorative());
        SlotType registered = SlotType.register(SlotType.builder("myaddon:coil")
                .allowInsert(true).persist(true).allowTake(true).build());
        assertSame(registered, loaded.slotType(13));
        assertTrue(GuiHolder.isPersistable(loaded.slotType(13)));

        registry.save(loaded);
        registry.loadAll();
        assertSame(registered, registry.get("my_machine").slotType(13));
        assertEquals("myaddon:machines", registry.get("my_machine").category());
    }

    @Test
    void legacyFileWithoutCategoryDefaultsToNone() {
        CustomGuiReworked plugin = mock(CustomGuiReworked.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        GuiRegistry registry = new GuiRegistry(plugin, dir.toFile());
        registry.save(new Gui("legacy"));
        File file = new File(dir.toFile(), "tables/legacy.yml");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        saved.set("category", null);
        try {
            saved.save(file);
        } catch (java.io.IOException e) {
            fail(e);
        }
        registry.loadAll();
        assertEquals(GuiCategory.NONE, registry.get("legacy").category());
    }
}
