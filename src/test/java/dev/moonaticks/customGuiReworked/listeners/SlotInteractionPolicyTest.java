package dev.moonaticks.customGuiReworked.listeners;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotInteractionPolicyTest {

    @AfterEach
    void cleanup() {
        SlotType.clearCustom();
    }

    private ItemStack item(Material type) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        return item;
    }

    @Test
    void filtersApplyToCursorPlacementAndShiftExtraction() {
        SlotType fuel = SlotType.register(SlotType.builder("addon:fuel")
                .allowInsert(true).allowTake(false).persist(true)
                .acceptInsert(ctx -> ctx.item().getType() == Material.COAL).build());
        Gui gui = new Gui("test");
        gui.setSlotType(2, fuel);
        Player player = mock(Player.class);
        Inventory top = mock(Inventory.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        ItemStack coal = item(Material.COAL);
        ItemStack diamond = item(Material.DIAMOND);
        when(click.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        when(click.getCursor()).thenReturn(coal);
        assertTrue(SlotInteractionPolicy.allowed(player, gui, top, 2, click));
        when(click.getCursor()).thenReturn(diamond);
        assertFalse(SlotInteractionPolicy.allowed(player, gui, top, 2, click));

        when(click.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(click.getCurrentItem()).thenReturn(coal);
        assertFalse(SlotInteractionPolicy.allowed(player, gui, top, 2, click));
    }

    @Test
    void hotbarSwapAndOffhandCannotBypassOutputSlot() {
        SlotType output = SlotType.register(SlotType.builder("addon:result")
                .allowTake(true).track(true).build());
        Gui gui = new Gui("test");
        gui.setSlotType(2, output);
        Player player = mock(Player.class);
        PlayerInventory playerInv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInv);
        ItemStack diamond = item(Material.DIAMOND);
        when(playerInv.getItem(0)).thenReturn(diamond);
        when(playerInv.getItemInOffHand()).thenReturn(diamond);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getAction()).thenReturn(InventoryAction.HOTBAR_SWAP);
        when(click.getHotbarButton()).thenReturn(0);
        assertFalse(SlotInteractionPolicy.allowed(player, gui, mock(Inventory.class), 2, click));
        when(click.getClick()).thenReturn(ClickType.SWAP_OFFHAND);
        assertFalse(SlotInteractionPolicy.allowed(player, gui, mock(Inventory.class), 2, click));
    }

    @Test
    void shiftClickRoutesOnlyIntoSlotsAcceptingTheItem() {
        SlotType oreSlot = SlotType.register(SlotType.builder("addon:ore")
                .allowInsert(true).allowTake(true).persist(true)
                .acceptInsert(ctx -> ctx.item().getType() == Material.IRON_ORE).build());
        Gui gui = new Gui("test");
        gui.setSlotType(3, oreSlot);
        Inventory top = mock(Inventory.class);
        Player player = mock(Player.class);
        ItemStack ore = item(Material.IRON_ORE);
        ItemStack placed = item(Material.IRON_ORE);
        when(ore.getAmount()).thenReturn(4);
        when(ore.getMaxStackSize()).thenReturn(64);
        when(ore.clone()).thenReturn(placed);
        GuiInteractionListener listener = new GuiInteractionListener(null);
        assertEquals(4, listener.moveToTop(player, top, gui, ore));
        org.mockito.Mockito.verify(top).setItem(3, placed);
        org.mockito.Mockito.verify(placed).setAmount(4);

        ItemStack wood = item(Material.OAK_LOG);
        when(wood.getAmount()).thenReturn(4);
        when(wood.getMaxStackSize()).thenReturn(64);
        assertEquals(0, listener.moveToTop(player, top, gui, wood));
    }

    @Test
    void doubleClickOnPlayerInventoryMustNotCollectFromLockedSlot() {
        SlotType locked = SlotType.register(SlotType.builder("addon:locked").persist(true).build());
        Gui gui = new Gui("test");
        gui.setSlotType(3, locked);
        Inventory top = mock(Inventory.class);
        ItemStack cursor = item(Material.COAL);
        ItemStack inLocked = item(Material.COAL);
        when(top.getItem(3)).thenReturn(inLocked);
        when(inLocked.isSimilar(cursor)).thenReturn(true);
        assertFalse(SlotInteractionPolicy.canCollect(mock(Player.class), gui, top, cursor));
        when(inLocked.isSimilar(cursor)).thenReturn(false);
        assertTrue(SlotInteractionPolicy.canCollect(mock(Player.class), gui, top, cursor));
    }
}
