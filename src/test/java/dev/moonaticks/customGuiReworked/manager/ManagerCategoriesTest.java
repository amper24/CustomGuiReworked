package dev.moonaticks.customGuiReworked.manager;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.GuiCategory;
import dev.moonaticks.customGuiReworked.gui.CategoryRegistry;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagerCategoriesTest {

    @TempDir
    Path dir;

    @Test
    void categoryAndSearchFiltersComposeAndShowUnregisteredIds() {
        CustomGuiReworked plugin = mock(CustomGuiReworked.class);
        CategoryRegistry categories = new CategoryRegistry(dir.toFile(), null);
        when(plugin.categories()).thenReturn(categories);
        // RUNTIME: нет файлов, но конструктору реестра нужна папка плагина.
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        GuiRegistry guis = new GuiRegistry(plugin);
        guis.register(new Gui("alpha"), false);
        guis.register(new Gui("beta").category("addon:tools"), false);
        guis.register(new Gui("gamma").category("lost:addon"), false);
        categories.register(new GuiCategory("addon:tools", "Tools"), false);
        categories.register(new GuiCategory("empty", "Unused"), false);
        ManagerMenu menu = new ManagerMenu(plugin, guis, null, null, null);
        ManagerSession session = new ManagerSession(UUID.randomUUID());

        assertEquals(List.of("alpha", "beta", "gamma"), menu.filteredNames(session));
        session.category(GuiCategory.NONE);
        assertEquals(List.of("alpha"), menu.filteredNames(session));
        session.category("addon:tools");
        assertEquals(List.of("beta"), menu.filteredNames(session));
        session.search("a");
        assertEquals(List.of("beta"), menu.filteredNames(session));
        session.search("miss");
        assertTrue(menu.filteredNames(session).isEmpty());
        session.category(null);
        assertNull(session.category());
        assertEquals(List.of("addon:tools", "empty", "lost:addon"), menu.categoryIds());
    }

    @Test
    void holderRemembersCategoryBySlotAcrossScreens() {
        ManagerHolder holder = new ManagerHolder(ManagerHolder.Screen.CATEGORIES, null);
        holder.putCategory(11, "addon:tools");
        assertEquals("addon:tools", holder.categoryAt(11));
        assertNull(holder.categoryAt(2));
    }
}
