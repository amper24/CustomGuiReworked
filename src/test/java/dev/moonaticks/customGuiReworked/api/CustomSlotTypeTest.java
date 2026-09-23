package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomSlotTypeTest {

    @AfterEach
    void cleanup() {
        SlotType.clearCustom();
    }

    @Test
    void builtinsRetainTheirSemanticsAndOrder() {
        assertArrayEquals(new SlotType[]{SlotType.DESIGN, SlotType.CONTAINER,
                SlotType.CRAFT, SlotType.RESULT, SlotType.FUEL}, SlotType.values());
        assertEquals(0, SlotType.DESIGN.ordinal());
        assertEquals("RESULT", SlotType.RESULT.name());
        assertFalse(GuiHolder.isPersistable(SlotType.RESULT));
        assertTrue(GuiHolder.isTracked(SlotType.RESULT));
        assertTrue(SlotType.RESULT.allowsLocalDesign());
        assertTrue(SlotType.DESIGN.isDecorative());
        assertFalse(SlotType.DESIGN.allowsInsert());
        assertTrue(SlotType.FUEL.allowsInsert());
        assertThrows(IllegalArgumentException.class, () -> SlotType.register(SlotType.DESIGN));
    }

    @Test
    void lateRegistrationActivatesPlaceholderWithoutLosingItsId() {
        SlotType placeholder = SlotType.fromLegacy("MyAddon:Input");
        Gui gui = new Gui("test");
        gui.setSlotType(7, placeholder);
        assertSame(placeholder, gui.slotType(7));
        assertEquals("myaddon:input", gui.slotType(7).id());
        assertTrue(placeholder.isDecorative());
        assertFalse(placeholder.isPersistable());
        assertEquals(-1, placeholder.ordinal());

        SlotType registered = SlotType.register(SlotType.builder("myaddon:input")
                .displayName("§6Input").icon(Material.CHEST)
                .allowInsert(true).allowTake(true).persist(true).build());
        assertSame(placeholder, registered);
        assertSame(registered, gui.slotType(7));
        assertEquals("§6Input", registered.displayName());
        assertTrue(GuiHolder.isPersistable(registered));
        assertTrue(GuiHolder.isTracked(registered));
        assertFalse(registered.isDecorative());
        assertEquals(List.of(7), gui.slotsOf(registered));
        assertEquals(registered, SlotType.valueOf("MYADDON:INPUT"));

        assertTrue(SlotType.unregister("MYADDON:INPUT"));
        assertSame(placeholder, gui.slotType(7));
        assertFalse(placeholder.isRegistered());
        assertFalse(GuiHolder.isPersistable(placeholder));
        assertTrue(placeholder.isDecorative());
        assertNull(SlotType.get("myaddon:input"));
        assertEquals("myaddon:input", gui.slotType(7).id());
        SlotType again = SlotType.register(SlotType.builder("myaddon:input")
                .allowTake(true).track(true).build());
        assertSame(placeholder, again);
        assertTrue(gui.slotType(7).isTracked());
    }

    @Test
    void insertionFiltersAndRelationshipsAreConfigurable() {
        AtomicInteger related = new AtomicInteger();
        SlotType input = SlotType.register(SlotType.builder("myaddon:ore")
                .allowInsert(true).allowTake(true).persist(true)
                .acceptInsert(ctx -> ctx.item().getType() == Material.IRON_ORE)
                .build());
        SlotType output = SlotType.register(SlotType.builder("myaddon:ingot")
                .allowTake(true).track(true).localDesign(true)
                .onClick(event -> event.setInteractionCancelled(true))
                .watch(input).onRelatedChange(event -> {
                    assertEquals(3, event.relatedSlot());
                    assertSame(input, event.change().getSlotType());
                    related.incrementAndGet();
                }).build());
        Gui gui = new Gui("foundry");
        gui.setSlotType(2, input);
        gui.setSlotType(3, output);
        ItemStack ore = mock(ItemStack.class);
        ItemStack wood = mock(ItemStack.class);
        when(ore.getType()).thenReturn(Material.IRON_ORE);
        when(wood.getType()).thenReturn(Material.OAK_LOG);

        assertTrue(input.canInsert(gui, null, 2, null, ore));
        assertFalse(input.canInsert(gui, null, 2, null, wood));
        assertFalse(output.canInsert(gui, null, 3, null, ore));
        assertTrue(output.canTake(gui, null, 3, null, ore));
        assertFalse(output.isPersistable());
        assertTrue(output.isTracked());
        assertTrue(output.watches(input));
        GuiSlotChangedEvent changed = new GuiSlotChangedEvent(null, gui, null, 2, input, null, ore);
        output.handleRelatedChange(new SlotType.SlotRelationEvent(changed, 3));
        assertEquals(1, related.get());
        GuiSlotClickEvent click = new GuiSlotClickEvent(null, gui, null, 3, output,
                true, ClickType.LEFT, null);
        output.handleClick(click);
        assertTrue(click.isInteractionCancelled());
    }

    @Test
    void invalidDefinitionsCannotLoseItemsOrOverrideBuiltins() {
        assertThrows(IllegalArgumentException.class, () -> SlotType.builder("result"));
        assertThrows(IllegalArgumentException.class, () -> SlotType.builder("../../etc:input"));
        assertThrows(IllegalStateException.class, () -> SlotType.builder("plugin:untracked")
                .allowInsert(true).build());
        assertThrows(IllegalStateException.class, () -> SlotType.builder("plugin:bad")
                .persist(true).localDesign(true).build());
        assertThrows(IllegalStateException.class, () -> SlotType.builder("plugin:bad")
                .decorative(true).allowTake(true).build());
        assertEquals(SlotType.DESIGN, SlotType.fromLegacy("unknown"));
        assertEquals(SlotType.CONTAINER, SlotType.fromLegacy("container_2"));
    }
}
