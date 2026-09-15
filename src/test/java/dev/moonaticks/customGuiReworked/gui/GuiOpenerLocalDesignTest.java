package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.codec.ItemCodec;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты учёта локальных (per-viewer) оверрайдов в {@link GuiOpener}:
 * applyDesign/rescueDesignItems используют локальный предмет, если он
 * задан, иначе — дизайн из файла; живое применение через
 * {@link GuiOpener#applyLocalDesign}; поиск зрителей блока.
 *
 * <p>Без поднятия сервера: инвентари и предметы — моки,
 * кодек — тестовый (реальный ItemStack в paper-api требует сервера).
 */
class GuiOpenerLocalDesignTest {

    /** Кодек: payload → предмет из карты (неизвестный payload — null). */
    static final class MapCodec implements ItemCodec {
        /** Карта перезаписывается тестами (доступ из внешнего класса). */
        Map<String, ItemStack> byPayload;

        MapCodec(Map<String, ItemStack> byPayload) {
            this.byPayload = byPayload;
        }

        @Override
        public String tag() {
            return "m1";
        }

        @Override
        public String encode(ItemStack item) {
            return "x";
        }

        @Override
        public ItemStack decode(String payload) {
            return byPayload == null ? null : byPayload.get(payload);
        }
    }

    private MapCodec codec;

    @BeforeEach
    void setUp() {
        Codecs.reset();
        codec = new MapCodec(null);
        Codecs.initialize(codec);
    }

    @AfterEach
    void tearDown() {
        Codecs.reset();
    }

    private GuiOpener opener() {
        // rescue/applyDesign не пользуются plugin/registry/storage/lang.
        return new GuiOpener(null, null, null, null);
    }

    /** Мок предмета с типом, количеством и (опционально) клоне-заменой. */
    private ItemStack mockItem(Material type, int amount, ItemStack clone) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        if (clone != null) {
            when(item.clone()).thenReturn(clone);
        }
        return item;
    }

    // ================= applyDesign =================

    @Test
    void applyDesignSetsPreparedLocalOverride() {
        Gui gui = new Gui("test");
        ItemStack fileClone = mockItem(Material.IRON_INGOT, 1, null);
        ItemStack fileItem = mockItem(Material.IRON_INGOT, 1, fileClone);
        ItemStack localClone = mockItem(Material.GOLD_INGOT, 1, null);
        ItemStack localItem = mockItem(Material.GOLD_INGOT, 1, localClone);
        codec.byPayload = Map.of("base", fileItem);
        gui.setDesignAt(0, "m1:base");

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        holder.setLocalDesign(0, localItem);
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);

        opener().applyDesign(inv, holder);

        verify(inv).setItem(0, localClone);
        // Слоты без payload дизайна не трогаются, без локального оверрайда
        // слот 0 файл-дизайн не используется.
    }

    @Test
    void applyDesignWithoutLocalUsesFileDesign() {
        Gui gui = new Gui("test");
        ItemStack fileClone = mockItem(Material.IRON_INGOT, 1, null);
        ItemStack fileItem = mockItem(Material.IRON_INGOT, 1, fileClone);
        codec.byPayload = Map.of("base", fileItem);
        gui.setDesignAt(0, "m1:base");

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);

        opener().applyDesign(inv, holder);

        verify(inv).setItem(0, fileClone);
    }

    // ================= rescueDesignItems =================

    @Test
    void rescueRestoresLocalWhenSlotEmptied() {
        Gui gui = new Gui("test");
        ItemStack localClone = mockItem(Material.GOLD_INGOT, 1, null);
        ItemStack localItem = mockItem(Material.GOLD_INGOT, 1, localClone);
        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        holder.setLocalDesign(0, localItem);
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        when(inv.getItem(0)).thenReturn(null);

        opener().rescueDesignItems(holder);

        verify(inv).setItem(0, localClone);
    }

    @Test
    void rescueRestoresFileDesignWhenNoLocal() {
        Gui gui = new Gui("test");
        ItemStack fileClone = mockItem(Material.IRON_INGOT, 1, null);
        ItemStack fileItem = mockItem(Material.IRON_INGOT, 1, fileClone);
        codec.byPayload = Map.of("base", fileItem);
        gui.setDesignAt(0, "m1:base");
        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        when(inv.getItem(0)).thenReturn(null);

        opener().rescueDesignItems(holder);

        verify(inv).setItem(0, fileClone);
    }

    @Test
    void rescueReturnsForeignItemAndRestoresLocal() {
        Gui gui = new Gui("test");
        ItemStack localClone = mockItem(Material.GOLD_INGOT, 1, null);
        ItemStack localItem = mockItem(Material.GOLD_INGOT, 1, localClone);
        ItemStack foreignClone = mockItem(Material.STONE, 1, null);
        ItemStack foreign = mockItem(Material.STONE, 1, foreignClone);
        when(foreign.isSimilar(localClone)).thenReturn(false);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        holder.setLocalDesign(0, localItem);
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        when(inv.getItem(0)).thenReturn(foreign);

        Player player = mock(Player.class);
        PlayerInventory playerInv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInv);
        when(inv.getViewers()).thenReturn(List.of(player));

        opener().rescueDesignItems(holder);

        verify(inv).setItem(0, localClone);
        verify(playerInv).addItem(foreignClone);
    }

    @Test
    void rescueReturnsDeltaForMergedIntoLocal() {
        Gui gui = new Gui("test");
        ItemStack localClone = mockItem(Material.GOLD_INGOT, 1, null);
        ItemStack localItem = mockItem(Material.GOLD_INGOT, 1, localClone);
        // «Слитый» стек: локальный декор + 2 лишних предмета игрока.
        ItemStack mergedClone = mockItem(Material.GOLD_INGOT, 2, null);
        ItemStack merged = mockItem(Material.GOLD_INGOT, 3, mergedClone);
        when(merged.isSimilar(localClone)).thenReturn(true);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        holder.setLocalDesign(0, localItem);
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);
        when(inv.getItem(0)).thenReturn(merged);

        Player player = mock(Player.class);
        PlayerInventory playerInv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInv);
        when(inv.getViewers()).thenReturn(List.of(player));

        opener().rescueDesignItems(holder);

        verify(inv).setItem(0, localClone);
        verify(playerInv).addItem(mergedClone); // только дельта (2 шт.)
    }

    // ================= applyLocalDesign (живое применение) =================

    @Test
    void applyLocalDesignUpdatesHolderAndLiveInventory() {
        Gui gui = new Gui("test");
        ItemStack localClone = mockItem(Material.GOLD_INGOT, 1, null);
        ItemStack localItem = mockItem(Material.GOLD_INGOT, 1, localClone);
        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);

        GuiOpener.applyLocalDesign(holder, 0, localItem);

        assertSame(localItem, holder.getLocalDesign(0));
        verify(inv).setItem(0, localClone);
    }

    @Test
    void applyLocalDesignClearRestoresFileDesign() {
        Gui gui = new Gui("test");
        ItemStack fileClone = mockItem(Material.IRON_INGOT, 1, null);
        ItemStack fileItem = mockItem(Material.IRON_INGOT, 1, fileClone);
        codec.byPayload = Map.of("base", fileItem);
        gui.setDesignAt(0, "m1:base");
        ItemStack localClone = mockItem(Material.GOLD_INGOT, 1, null);
        ItemStack localItem = mockItem(Material.GOLD_INGOT, 1, localClone);

        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        Inventory inv = mock(Inventory.class);
        holder.attach(inv);

        GuiOpener.applyLocalDesign(holder, 0, localItem);
        verify(inv).setItem(0, localClone);

        GuiOpener.applyLocalDesign(holder, 0, null);
        assertNull(holder.getLocalDesign(0));
        verify(inv).setItem(0, fileClone); // слот вернулся к дизайну из файла
    }

    @Test
    void applyLocalDesignRejectsPersistableSlots() {
        Gui gui = new Gui("test");
        gui.setSlotType(3, SlotType.CRAFT);
        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));
        holder.attach(mock(Inventory.class));

        assertThrows(IllegalArgumentException.class,
                () -> GuiOpener.applyLocalDesign(holder, 3, mockItem(Material.IRON_INGOT, 1, null)));
        assertNull(holder.getLocalDesign(3));
    }

    @Test
    void defaultForSlotUsesFileDesign() {
        Gui gui = new Gui("test");
        ItemStack fileClone = mockItem(Material.IRON_INGOT, 1, null);
        ItemStack fileItem = mockItem(Material.IRON_INGOT, 1, fileClone);
        codec.byPayload = Map.of("base", fileItem);
        gui.setDesignAt(0, "m1:base");
        GuiHolder holder = new GuiHolder(gui, StorageKey.global("test.yml"));

        assertSame(fileClone, GuiOpener.defaultForSlot(holder, 0));
        // Пустой payload — null (слот остаётся пустым).
        assertNull(GuiOpener.defaultForSlot(holder, 5));
    }

    // ================= getViewers =================

    @Test
    void getViewersFindsPlayersWithBlockSession() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location block = new Location(world, 1, 2, 3);

        GuiHolder matching = new GuiHolder(new Gui("a"),
                new StorageKey(StorageType.BLOCK, "world:1,2,3", "a.yml"));
        GuiHolder otherBlock = new GuiHolder(new Gui("b"),
                new StorageKey(StorageType.BLOCK, "world:9,9,9", "b.yml"));
        GuiHolder personal = new GuiHolder(new Gui("c"),
                StorageKey.forPlayerName("steve", "c.yml"));

        Player p1 = playerWithTop(matching);
        Player p2 = playerWithTop(otherBlock);
        Player p3 = playerWithTop(personal);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2, p3));
            List<Player> viewers = GuiOpener.getViewers(block);
            assertEquals(List.of(p1), viewers, "видит только зритель именно этого блока");
        }
    }

    @Test
    void getViewersEmptyForInvalidLocation() {
        assertEquals(0, GuiOpener.getViewers(null).size());
        // Мир не загружен — owner-ключ не построить.
        assertEquals(0, GuiOpener.getViewers(new Location(null, 1, 2, 3)).size());
    }

    private Player playerWithTop(GuiHolder holder) {
        Player player = mock(Player.class);
        Inventory top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(top.getHolder()).thenReturn(holder);
        when(view.getTopInventory()).thenReturn(top);
        when(player.getOpenInventory()).thenReturn(view);
        return player;
    }
}
