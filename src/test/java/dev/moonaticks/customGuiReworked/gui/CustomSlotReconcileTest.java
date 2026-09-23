package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.codec.ItemCodec;
import dev.moonaticks.customGuiReworked.integration.BlockHookDispatcher;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomSlotReconcileTest {

    @AfterEach
    void cleanup() {
        SlotType.clearCustom();
        Codecs.reset();
    }

    @Test
    void customInputChangePersistsAndNotifiesOutputSlots() {
        AtomicInteger changed = new AtomicInteger();
        AtomicInteger related = new AtomicInteger();
        SlotType input = SlotType.register(SlotType.builder("sample:input")
                .persist(true).allowInsert(true).allowTake(true)
                .onChange(event -> changed.incrementAndGet()).build());
        SlotType output = SlotType.register(SlotType.builder("sample:output")
                .track(true).allowTake(true).watch(input)
                .onRelatedChange(event -> {
                    assertEquals(12, event.relatedSlot());
                    assertEquals(3, event.change().getSlot());
                    related.incrementAndGet();
                }).build());
        Gui gui = new Gui("test");
        gui.setSlotType(3, input);
        gui.setSlotType(12, output);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.IRON_INGOT);
        when(item.getAmount()).thenReturn(1);
        Codecs.initialize(new ItemCodec() {
            @Override public String tag() { return "q1"; }
            @Override public String encode(ItemStack stack) { return "iron"; }
            @Override public ItemStack decode(String value) { return item; }
        });

        GuiHolder holder = new GuiHolder(gui, StorageKey.global(gui.fileName()));
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        when(inv.getItem(3)).thenReturn(item);
        holder.initBaseline(new String[gui.slots()]);
        CustomGuiReworked plugin = mock(CustomGuiReworked.class);
        BlockHookDispatcher dispatcher = mock(BlockHookDispatcher.class);
        when(plugin.dispatcher()).thenReturn(dispatcher);
        StorageService storage = mock(StorageService.class);
        GuiOpener opener = new GuiOpener(plugin, null, storage, null);

        PluginManager pm = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
            opener.reconcile(holder, Set.of(3));
        }
        verify(storage).updateSlot(eq(holder.key()), eq(3), eq("q1:iron"));
        assertEquals(1, changed.get());
        assertEquals(1, related.get());
    }
}
