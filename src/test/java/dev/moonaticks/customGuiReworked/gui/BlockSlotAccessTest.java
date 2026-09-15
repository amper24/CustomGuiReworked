package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.GuiServiceImpl;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.codec.ItemCodec;
import dev.moonaticks.customGuiReworked.integration.BlockHookDispatcher;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты доступа к персистентным слотам блока при закрытом GUI:
 * {@code getBlockSlotItem}/{@code setBlockSlotItem}/{@code consumeBlockSlotItem}
 * (через GuiServiceImpl) и {@code GuiOpener.applyRemoteChange} (живые
 * зрители: перерисовка + GuiSlotChangedEvent).
 */
class BlockSlotAccessTest {

    static final class AmountCodec implements ItemCodec {
        final Map<String, ItemStack> decodeMap = new HashMap<>();

        @Override
        public String tag() {
            return "a1";
        }

        @Override
        public String encode(ItemStack item) {
            return "e" + item.getAmount();
        }

        @Override
        public ItemStack decode(String payload) {
            return decodeMap.get(payload);
        }
    }

    private AmountCodec codec;
    private World world;
    private Location block;
    private StorageKey key;
    private Gui gui;
    private GuiRegistry guiRegistry;
    private StorageService storage;
    private CustomGuiReworked plugin;
    private BlockHookDispatcher dispatcher;
    private GuiOpener opener;
    private GuiServiceImpl service;

    @BeforeEach
    void setUp() {
        Codecs.reset();
        codec = new AmountCodec();
        Codecs.initialize(codec);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        block = new Location(world, 1, 2, 3);
        key = StorageKey.forBlock(block, "pot.yml");

        gui = mock(Gui.class);
        when(gui.fileName()).thenReturn("pot.yml");
        when(gui.slots()).thenReturn(27);
        when(gui.slotType(24)).thenReturn(SlotType.RESULT);
        when(gui.slotType(13)).thenReturn(SlotType.CRAFT);
        when(gui.slotType(2)).thenReturn(SlotType.DESIGN);
        guiRegistry = mock(GuiRegistry.class);
        when(guiRegistry.getByBlockId("custom_pot")).thenReturn(gui);

        storage = mock(StorageService.class);
        plugin = mock(CustomGuiReworked.class);
        dispatcher = mock(BlockHookDispatcher.class);
        when(plugin.registry()).thenReturn(guiRegistry);
        when(plugin.storage()).thenReturn(storage);
        when(plugin.dispatcher()).thenReturn(dispatcher);
        opener = new GuiOpener(plugin, guiRegistry, storage, null);
        when(plugin.opener()).thenReturn(opener);
        service = new GuiServiceImpl(plugin);
    }

    @AfterEach
    void tearDown() {
        Codecs.reset();
    }

    private ItemStack mockItem(int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.IRON_INGOT);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }

    @Test
    void getBlockSlotItemReadsStorage() {
        ItemStack stored = mockItem(3);
        codec.decodeMap.put("e3", stored);
        String[] slots = new String[27];
        slots[24] = "a1:e3";
        when(storage.load(key)).thenReturn(slots);

        try (MockedStatic<FunctionalBlockRegistry> fbr = mockStatic(FunctionalBlockRegistry.class)) {
            fbr.when(() -> FunctionalBlockRegistry.resolveCustomBlockId(block))
                    .thenReturn("custom_pot");
            assertSame(stored, service.getBlockSlotItem(block, 24));
            assertNull(service.getBlockSlotItem(block, 13), "пустой слот");
            assertNull(service.getBlockSlotItem(block, 2), "DESIGN не отслеживается");
        }
    }

    @Test
    void setBlockSlotItemWritesStorage() {
        ItemStack item = mockItem(1);
        try (MockedStatic<FunctionalBlockRegistry> fbr = mockStatic(FunctionalBlockRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            fbr.when(() -> FunctionalBlockRegistry.resolveCustomBlockId(block))
                    .thenReturn("custom_pot");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.emptyList());

            assertTrue(service.setBlockSlotItem(block, 24, item));
            assertFalse(service.setBlockSlotItem(block, 2, item), "DESIGN нельзя");
        }
        verify(storage).updateSlot(key, 24, "a1:e1");
    }

    @Test
    void setBlockSlotItemRerendersLiveViewersWithEvent() {
        ItemStack oldItem = mockItem(1);
        codec.decodeMap.put("e1", oldItem);
        String[] slots = new String[27];
        slots[24] = "a1:e1";
        when(storage.load(key)).thenReturn(slots);

        ItemStack produced = mockItem(2);
        ItemStack producedClone = mockItem(2);
        when(produced.clone()).thenReturn(producedClone);
        codec.decodeMap.put("e2", producedClone);

        GuiHolder holder = new GuiHolder(gui, key);
        UUID viewerUuid = UUID.randomUUID();
        holder.setPlayer(viewerUuid);
        // «Поведенческий» мок-инвентарь: хранит предметы по слотам,
        // чтобы getItem после setItem возвращал новое (как в реальном).
        java.util.Map<Integer, ItemStack> backing = new java.util.HashMap<>();
        backing.put(24, oldItem);
        Inventory top = mock(Inventory.class);
        when(top.getHolder()).thenReturn(holder);
        when(top.getItem(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> backing.get(inv.getArgument(0)));
        org.mockito.Mockito.doAnswer(inv -> {
            backing.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(top).setItem(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ItemStack.class));
        holder.attach(top);
        String[] baseline = new String[27];
        baseline[24] = "a1:e1";
        holder.initBaseline(baseline);

        Player viewer = mock(Player.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(viewer.getOpenInventory()).thenReturn(view);
        when(viewer.getUniqueId()).thenReturn(viewerUuid);

        try (MockedStatic<FunctionalBlockRegistry> fbr = mockStatic(FunctionalBlockRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            fbr.when(() -> FunctionalBlockRegistry.resolveCustomBlockId(block))
                    .thenReturn("custom_pot");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singletonList(viewer));
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
            bukkit.when(() -> Bukkit.getPlayer(viewerUuid)).thenReturn(viewer);

            assertTrue(service.setBlockSlotItem(block, 24, produced));

            // Зритель перерисован...
            verify(top).setItem(24, producedClone);
            // ...baseline обновлён...
            assertEquals("a1:e2", holder.baseline()[24], "baseline синхронен с записью");
            // ...и событие «результат появился» вызвано.
            ArgumentCaptor<GuiSlotChangedEvent> captor =
                    ArgumentCaptor.forClass(GuiSlotChangedEvent.class);
            verify(pm).callEvent(captor.capture());
            GuiSlotChangedEvent event = captor.getValue();
            assertEquals(24, event.getSlot());
            assertEquals(SlotType.RESULT, event.getSlotType());
            assertSame(oldItem, event.getOldItem());
            assertSame(producedClone, event.getNewItem());
            assertSame(viewer, event.getPlayer());
        }
    }

    @Test
    void consumeBlockSlotItemDecrementsAndClears() {
        // «Поведенческий» предмет: уважает setAmount,
        // чтобы encode после снятия показывал новое количество.
        int[] amount = {5};
        ItemStack five = mock(ItemStack.class);
        when(five.getType()).thenReturn(Material.IRON_INGOT);
        when(five.getAmount()).thenAnswer(inv -> amount[0]);
        org.mockito.Mockito.doAnswer(inv -> {
            amount[0] = inv.getArgument(0);
            return null;
        }).when(five).setAmount(org.mockito.ArgumentMatchers.anyInt());
        codec.decodeMap.put("e5", five);

        ItemStack three = mockItem(3);
        codec.decodeMap.put("e3", three);
        String[] slots = new String[27];
        slots[13] = "a1:e5";
        when(storage.load(key)).thenReturn(slots);

        int taken;
        try (MockedStatic<FunctionalBlockRegistry> fbr = mockStatic(FunctionalBlockRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            fbr.when(() -> FunctionalBlockRegistry.resolveCustomBlockId(block))
                    .thenReturn("custom_pot");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.emptyList());

            taken = service.consumeBlockSlotItem(block, 13, 2);
            assertEquals(2, taken);
            // Осталось 3 — записано:
            verify(storage).updateSlot(key, 13, "a1:e3");

            // Кэш хранилища в реальности обновился — эмулируем:
            slots[13] = "a1:e3";

            taken = service.consumeBlockSlotItem(block, 13, 99);
            assertEquals(3, taken, "всё, что осталось");
            verify(storage).updateSlot(key, 13, "");
        }
        verify(storage, never()).updateSlot(key, 13, "a1:e5");
    }

    @Test
    void consumeEmptySlotReturnsZero() {
        when(storage.load(key)).thenReturn(new String[27]);
        try (MockedStatic<FunctionalBlockRegistry> fbr = mockStatic(FunctionalBlockRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            fbr.when(() -> FunctionalBlockRegistry.resolveCustomBlockId(block))
                    .thenReturn("custom_pot");
            assertEquals(0, service.consumeBlockSlotItem(block, 13, 1));
        }
        verify(storage, never()).updateSlot(org.mockito.ArgumentMatchers.any(StorageKey.class),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void applyRemoteChangeWithoutChangeDoesNotFireEvent() {
        ItemStack item = mockItem(1);
        ItemStack sameClone = mockItem(1);
        when(item.clone()).thenReturn(sameClone);
        GuiHolder holder = new GuiHolder(gui, key);
        holder.setPlayer(UUID.randomUUID());
        Inventory top = mock(Inventory.class);
        holder.attach(top);
        when(top.getItem(24)).thenReturn(sameClone);
        String[] baseline = new String[27];
        baseline[24] = "a1:e1";
        holder.initBaseline(baseline);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
            bukkit.when(() -> Bukkit.getPlayer(holder.player())).thenReturn(mock(Player.class));

            opener.applyRemoteChange(holder, 24, item);
            verify(pm, never()).callEvent(org.mockito.ArgumentMatchers.any());
        }
        verify(storage, never()).updateSlot(org.mockito.ArgumentMatchers.any(StorageKey.class),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void applyRemoteChangeSkipsUntrackedAndOutOfRange() {
        GuiHolder holder = new GuiHolder(gui, key);
        holder.setPlayer(UUID.randomUUID());
        Inventory top = mock(Inventory.class);
        holder.attach(top);
        holder.initBaseline(new String[27]);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);

            opener.applyRemoteChange(holder, 2, mockItem(1));   // DESIGN
            opener.applyRemoteChange(holder, 99, mockItem(1));  // out of range
            verify(pm, never()).callEvent(org.mockito.ArgumentMatchers.any());
        }
        verify(top, never()).setItem(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ItemStack.class));
    }

    @Test
    void blockDataFacadeDelegatesToRegistry() {
        FunctionalBlockRegistry registry = mock(FunctionalBlockRegistry.class);
        when(plugin.functionalBlocks()).thenReturn(registry);
        try (MockedStatic<FunctionalBlockRegistry> fbr = mockStatic(FunctionalBlockRegistry.class)) {
            fbr.when(() -> FunctionalBlockRegistry.resolveCustomBlockId(block))
                    .thenReturn("custom_pot");
            // registry.data(...) по умолчанию null → facade передаёт null
            assertNull(service.blockData(block));
            verify(registry).data("custom_pot", block);
        }
    }
}
