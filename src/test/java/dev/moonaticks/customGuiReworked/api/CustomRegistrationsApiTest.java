package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.gui.CategoryRegistry;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class CustomRegistrationsApiTest {

    @TempDir
    Path dir;

    @AfterEach
    void cleanup() {
        CustomGuiAPI.shutdown();
        SlotType.clearCustom();
    }

    @Test
    void staticFacadeAndServiceRegisterCategoryAndSlotType() {
        CustomGuiReworked plugin = mock(CustomGuiReworked.class);
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.categories()).thenReturn(new CategoryRegistry(dir.toFile(), null));
        GuiService service = new GuiServiceImpl(plugin);
        CustomGuiAPI.initialize(service);

        GuiCategory category = CustomGuiAPI.registerCategory(new GuiCategory(
                "addon:machines", "§6Machines", Material.FURNACE, "§7Workstations"));
        assertEquals(category, service.getCategory("ADDON:machines"));
        assertEquals(List.of(category), CustomGuiAPI.getCategories());

        SlotType type = CustomGuiAPI.registerSlotType(SlotType.builder("addon:input")
                .icon(Material.IRON_ORE).persist(true).allowInsert(true).allowTake(true).build());
        assertSame(type, service.getSlotType("ADDON:INPUT"));
        assertTrue(CustomGuiAPI.getSlotTypes().contains(type));
        assertTrue(CustomGuiAPI.unregisterSlotType(type.id()));
        assertNull(CustomGuiAPI.getSlotType(type.id()));
        assertTrue(service.unregisterCategory(category.id()));
        assertTrue(CustomGuiAPI.getCategories().isEmpty());
    }

    @Test
    void trackedCustomOutputCanBeUpdatedThroughApiButDecorationCannot() {
        CustomGuiReworked plugin = mock(CustomGuiReworked.class);
        GuiOpener opener = mock(GuiOpener.class);
        when(plugin.opener()).thenReturn(opener);
        GuiServiceImpl service = new GuiServiceImpl(plugin);
        SlotType output = SlotType.register(SlotType.builder("addon:output")
                .track(true).allowTake(true).build());
        Gui gui = new Gui("test");
        gui.setSlotType(4, output);
        GuiHolder holder = mock(GuiHolder.class);
        Inventory inventory = mock(Inventory.class);
        when(holder.gui()).thenReturn(gui);
        when(holder.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(gui.slots());
        ItemStack item = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.clone()).thenReturn(clone);

        assertTrue(service.setSlotItem(inventory, 4, item));
        verify(inventory).setItem(4, clone);
        verify(holder).addCandidate(4);
        verify(opener).scheduleReconcile(holder);
        assertFalse(service.setSlotItem(inventory, 1, item));
        assertFalse(service.setSlotItem(inventory, 99, item));
    }
}
