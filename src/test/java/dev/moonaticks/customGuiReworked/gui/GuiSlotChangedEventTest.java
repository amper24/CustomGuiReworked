package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlock;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.codec.ItemCodec;
import dev.moonaticks.customGuiReworked.integration.BlockHookDispatcher;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты {@link GuiSlotChangedEvent}: dif-реконсиляция генерирует событие
 * по каждому изменившемуся отслеживаемому слоту (CONTAINER/CRAFT/FUEL/RESULT)
 * с предметами «было/стало», локальные оверрайды RESULT-слота не создают
 * ложных событий, функциональный обработчик получает колбэк.
 */
class GuiSlotChangedEventTest {

    /**
     * Кодек, кодирующий предмет по количеству («e1», «e2», ...) и
     * декодирующий из карты — позволяет отличать «было/стало» без
     * реального сервера.
     */
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
    private CustomGuiReworked plugin;
    private BlockHookDispatcher dispatcher;
    private StorageService storage;
    private GuiOpener opener;

    @BeforeEach
    void setUp() {
        Codecs.reset();
        codec = new AmountCodec();
        Codecs.initialize(codec);
        plugin = mock(CustomGuiReworked.class);
        dispatcher = mock(BlockHookDispatcher.class);
        storage = mock(StorageService.class);
        when(plugin.dispatcher()).thenReturn(dispatcher);
        opener = new GuiOpener(plugin, null, storage, null);
        when(plugin.opener()).thenReturn(opener);
    }

    @AfterEach
    void tearDown() {
        Codecs.reset();
    }

    private ItemStack mockItem(Material type, int amount, ItemStack clone) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        if (clone != null) {
            when(item.clone()).thenReturn(clone);
        }
        return item;
    }

    private Gui furnaceGui() {
        Gui gui = new Gui("furnace"); // 27 слотов, все DESIGN
        gui.setSlotType(13, SlotType.CRAFT);
        gui.setSlotType(14, SlotType.FUEL);
        gui.setSlotType(22, SlotType.RESULT);
        return gui;
    }

    @Test
    void craftSlotChangeFiresEventWithOldAndNew() {
        Gui gui = furnaceGui();
        ItemStack oldClone = mockItem(Material.IRON_ORE, 1, null);
        ItemStack oldItem = mockItem(Material.IRON_ORE, 1, oldClone);
        ItemStack newItem = mockItem(Material.IRON_ORE, 3, null);
        codec.decodeMap.put("e1", oldClone);
        codec.decodeMap.put("e3", newItem);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("furnace.yml"));
        UUID playerUuid = UUID.randomUUID();
        holder.setPlayer(playerUuid);
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        String[] baseline = new String[gui.slots()];
        baseline[13] = Codecs.encode(oldItem); // в слоте был 1 ore
        holder.initBaseline(baseline);

        when(inv.getItem(13)).thenReturn(newItem); // теперь 3 ore
        Player player = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
            bukkit.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);

            opener.reconcile(holder, null);

            ArgumentCaptor<GuiSlotChangedEvent> captor = ArgumentCaptor.forClass(GuiSlotChangedEvent.class);
            verify(pm).callEvent(captor.capture());
            GuiSlotChangedEvent event = captor.getValue();
            assertEquals(13, event.getSlot());
            assertEquals(SlotType.CRAFT, event.getSlotType());
            assertSame(oldClone, event.getOldItem());
            assertSame(newItem, event.getNewItem());
            assertSame(player, event.getPlayer());
            assertSame(inv, event.getInventory());
            assertSame(gui, event.getGui());
            // Функциональный диспетчер вызван до внешних слушателей
            verify(dispatcher).onSlotChanged(holder, event);
        }
        verify(storage).updateSlot(any(StorageKey.class),
                org.mockito.Mockito.eq(13), org.mockito.Mockito.eq(Codecs.encode(newItem)));
    }

    @Test
    void resultTakenFiresEventButIsNotPersisted() {
        Gui gui = furnaceGui();
        ItemStack resultClone = mockItem(Material.IRON_INGOT, 1, null);
        codec.decodeMap.put("e1", resultClone);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("furnace.yml"));
        holder.setPlayer(UUID.randomUUID());
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        String[] baseline = new String[gui.slots()];
        baseline[22] = Codecs.encode(resultClone); // результат был в слоте
        holder.initBaseline(baseline);
        // inv.getItem(22) — null: игрок забрал результат

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
            bukkit.when(() -> Bukkit.getPlayer(any())).thenReturn(mock(Player.class));

            opener.reconcile(holder, Set.of(22));

            ArgumentCaptor<GuiSlotChangedEvent> captor = ArgumentCaptor.forClass(GuiSlotChangedEvent.class);
            verify(pm).callEvent(captor.capture());
            GuiSlotChangedEvent event = captor.getValue();
            assertEquals(22, event.getSlot());
            assertEquals(SlotType.RESULT, event.getSlotType());
            assertSame(resultClone, event.getOldItem());
            assertNull(event.getNewItem(), "слот стал пуст");
        }
        verify(storage, never()).updateSlot(any(StorageKey.class), org.mockito.Mockito.eq(22), any());
    }

    @Test
    void unchangedSlotsDoNotFireEvents() {
        Gui gui = furnaceGui();
        ItemStack itemClone = mockItem(Material.IRON_ORE, 1, null);
        ItemStack item = mockItem(Material.IRON_ORE, 1, itemClone);
        codec.decodeMap.put("e1", itemClone);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("furnace.yml"));
        holder.setPlayer(UUID.randomUUID());
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        String[] baseline = new String[gui.slots()];
        baseline[13] = Codecs.encode(item);
        holder.initBaseline(baseline);
        when(inv.getItem(13)).thenReturn(item); // то же самое

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);

            opener.reconcile(holder, null);

            verify(pm, never()).callEvent(any());
            verify(dispatcher, never()).onSlotChanged(any(), any());
        }
    }

    @Test
    void temporaryStorageStillFiresEvents() {
        Gui gui = furnaceGui();
        ItemStack oldClone = mockItem(Material.IRON_ORE, 1, null);
        ItemStack newItem = mockItem(Material.IRON_ORE, 3, null);
        codec.decodeMap.put("e1", oldClone);
        codec.decodeMap.put("e3", newItem);

        GuiHolder holder = new GuiHolder(gui,
                StorageKey.temporary(UUID.randomUUID(), "furnace.yml"));
        holder.setPlayer(UUID.randomUUID());
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        String[] baseline = new String[gui.slots()];
        baseline[13] = Codecs.encode(oldClone);
        holder.initBaseline(baseline);
        when(inv.getItem(13)).thenReturn(newItem);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);

            opener.reconcile(holder, null);

            ArgumentCaptor<GuiSlotChangedEvent> captor = ArgumentCaptor.forClass(GuiSlotChangedEvent.class);
            verify(pm).callEvent(captor.capture());
            assertEquals(13, captor.getValue().getSlot());
        }
        verify(storage, never()).updateSlot(any(), org.mockito.Mockito.anyInt(), any());
    }

    @Test
    void baselineUpdatedSoChangeFiresOnce() {
        Gui gui = furnaceGui();
        ItemStack oldClone = mockItem(Material.IRON_ORE, 1, null);
        ItemStack newItem = mockItem(Material.IRON_ORE, 3, null);
        codec.decodeMap.put("e1", oldClone);
        codec.decodeMap.put("e3", newItem);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("furnace.yml"));
        holder.setPlayer(UUID.randomUUID());
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        String[] baseline = new String[gui.slots()];
        baseline[13] = Codecs.encode(oldClone);
        holder.initBaseline(baseline);
        when(inv.getItem(13)).thenReturn(newItem);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);

            opener.reconcile(holder, null);
            assertEquals(Codecs.encode(newItem), holder.baseline()[13], "baseline обновлён");

            opener.reconcile(holder, null);
            verify(pm, org.mockito.Mockito.times(1)).callEvent(any()); // только первое событие
        }
    }

    @Test
    void localResultOverrideDoesNotFireSpuriousEvent() {
        Gui gui = furnaceGui();
        ItemStack frameClone = mockItem(Material.IRON_INGOT, 1, null);
        ItemStack frame = mockItem(Material.IRON_INGOT, 1, frameClone);
        codec.decodeMap.put("e1", frameClone);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("furnace.yml"));
        holder.setPlayer(UUID.randomUUID());
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        String[] baseline = new String[gui.slots()];
        holder.initBaseline(baseline); // RESULT пуст при открытии

        // onTick «показал результат» через локальный оверрайд...
        // (мок-инвентарь не хранит предметы: getItem(22) отдаёт именно кадр)
        when(inv.getItem(22)).thenReturn(frameClone);
        GuiOpener.applyLocalDesign(holder, 22, frame);
        assertEquals(Codecs.encode(frameClone), holder.baseline()[22], "baseline подхвачен");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager pm = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pm);

            // ...реконсиляция от чужого клика не видит «изменения»:
            opener.reconcile(holder, Set.of(22));
            verify(pm, never()).callEvent(any());

            // ...а реальное действие игрока (забрал результат) событие даёт:
            when(inv.getItem(22)).thenReturn(null);
            opener.reconcile(holder, Set.of(22));
            ArgumentCaptor<GuiSlotChangedEvent> captor = ArgumentCaptor.forClass(GuiSlotChangedEvent.class);
            verify(pm).callEvent(captor.capture());
            assertSame(frameClone, captor.getValue().getOldItem());
            assertNull(captor.getValue().getNewItem());
        }
    }

    @Test
    void produceResultSchedulesReconcile() {
        Gui gui = furnaceGui();
        GuiHolder holder = new GuiHolder(gui, StorageKey.global("furnace.yml"));
        holder.setPlayer(UUID.randomUUID());
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        when(inv.getHolder()).thenReturn(holder);
        holder.initBaseline(new String[gui.slots()]);
        ItemStack produced = mockItem(Material.IRON_INGOT, 1, null);
        when(inv.getItem(22)).thenReturn(null);

        dev.moonaticks.customGuiReworked.api.GuiServiceImpl service =
                new dev.moonaticks.customGuiReworked.api.GuiServiceImpl(plugin);
        boolean ok = service.produceResult(inv, Map.of(22, produced));

        assertTrue(ok);
        assertTrue(holder.isReconcileQueued(),
                "reconcile запланирован — GuiSlotChangedEvent последует на следующем тике");
    }

    @Test
    void dispatcherCallsHandlerOnSlotChanged() {
        FunctionalBlockRegistry registry = new FunctionalBlockRegistry(null);
        Map<String, Object> seen = new ConcurrentHashMap<>();
        FunctionalBlock.Builder builder = new FunctionalBlock.Builder("custom_furnace", registry)
                .gui("furnace")
                .onItemChanged((player, block, slot, type, oldItem, newItem) -> {
                    seen.put("slot", slot);
                    seen.put("type", type);
                });
        registry.registerHandler("custom_furnace", builder.build());

        BlockHookDispatcher realDispatcher = new BlockHookDispatcher(plugin, null, null);
        when(plugin.functionalBlocks()).thenReturn(registry);

        Gui gui = furnaceGui();
        GuiHolder holder = new GuiHolder(gui,
                new StorageKey(StorageType.BLOCK, "world:1,2,3", "furnace.yml"));
        ItemStack old = mockItem(Material.IRON_ORE, 1, null);
        ItemStack now = mockItem(Material.IRON_ORE, 3, null);
        GuiSlotChangedEvent event = new GuiSlotChangedEvent(null, gui, mock(Inventory.class),
                13, SlotType.CRAFT, old, now);

        World world = mock(World.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            realDispatcher.onSlotChanged(holder, event);
        }

        assertEquals(13, seen.get("slot"));
        assertEquals(SlotType.CRAFT, seen.get("type"));
    }
}
