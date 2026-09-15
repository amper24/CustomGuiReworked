package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты переноса предметов в дизайн-слоты редактора. Как и остальные
 * тесты проекта, сервер не поднимается: ItemStack/Inventory — моки
 * (реальное конструирование ItemStack требует запущенного сервера).
 *
 * <p>Проверяется главное: панель «Пусто» считается свободным слотом
 * (её снимают перед ванильным переносом), панель «Заблокировано»
 * не трогается, а предметы самого дизайна остаются на месте.
 */
class EditorDesignTest {

    private static final String EMPTY_NAME = "§7Empty";
    private static final String LOCKED_NAME = "§cLocked (skeleton slot)";

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private LanguageManager lang;
    private EditorManager editor;
    private Player player;

    @BeforeEach
    void setUp() {
        lang = mock(LanguageManager.class);
        when(lang.raw("editor.design.empty")).thenReturn(EMPTY_NAME);
        when(lang.raw("editor.design.locked")).thenReturn(LOCKED_NAME);
        when(lang.msg(anyString())).thenReturn(Component.empty());
        editor = new EditorManager(mock(CustomGuiReworked.class), mock(GuiRegistry.class), lang);
        player = mock(Player.class);
    }

    // ================= вспомогательное =================

    /** Служебная панель экрана DESIGN (материал + имя из lang). */
    private static ItemStack pane(Material material, String name) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(SERIALIZER.deserialize(name));
        return item;
    }

    /** Обычный предмет (дизайна или игрока). */
    private static ItemStack stack(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.getMaxStackSize()).thenReturn(64);
        return item;
    }

    /** Пустой инвентарь нужного размера: getItem() → null, setItem() — заглушка. */
    private static Inventory inventory(int size) {
        Inventory inv = mock(Inventory.class);
        when(inv.getSize()).thenReturn(size);
        return inv;
    }

    private static EditorSession session(Gui gui) {
        return new EditorSession(UUID.randomUUID(), gui);
    }

    // ================= тесты =================

    @Test
    void placeholderIsRecognizedOnlyByItsName() {
        assertTrue(editor.isPlaceholderItem(pane(Material.LIME_STAINED_GLASS_PANE, EMPTY_NAME)));
        assertFalse(editor.isPlaceholderItem(pane(Material.BLACK_STAINED_GLASS_PANE, LOCKED_NAME)));
        assertFalse(editor.isPlaceholderItem(stack(Material.DIAMOND, 1)));
        assertFalse(editor.isPlaceholderItem(null));

        assertTrue(editor.isScreenPane(pane(Material.BLACK_STAINED_GLASS_PANE, LOCKED_NAME)));
        assertFalse(editor.isScreenPane(stack(Material.DIAMOND, 1)));
    }

    @Test
    void releasePaneTouchesOnlyServicePanes() {
        Inventory inv = inventory(9);
        when(inv.getItem(0)).thenReturn(pane(Material.BLACK_STAINED_GLASS_PANE, LOCKED_NAME));
        when(inv.getItem(1)).thenReturn(stack(Material.DIAMOND, 1));

        editor.releasePane(inv, 0);
        editor.releasePane(inv, 1); // предмет дизайна/игрока не трогаем

        verify(inv).setItem(0, null);
        verify(inv, never()).setItem(eq(1), any());
    }

    @Test
    void releasePaneIgnoresOutOfBoundsSlots() {
        Inventory inv = inventory(9);

        editor.releasePane(inv, -1);
        editor.releasePane(inv, 9);
        editor.releasePane(null, 0);

        verify(inv, never()).getItem(anyInt());
        verify(inv, never()).setItem(anyInt(), any());
    }

    @Test
    void releaseEmptyPanesSkipsServiceSlotsAndOtherSlots() {
        Gui gui = new Gui("test");
        gui.setSlotType(0, SlotType.CONTAINER); // служебный слот скелета
        gui.setSlotType(1, SlotType.DESIGN);
        gui.setSlotType(2, SlotType.DESIGN);
        Inventory inv = inventory(gui.slots());
        ItemStack empty = pane(Material.LIME_STAINED_GLASS_PANE, EMPTY_NAME);
        when(inv.getItem(0)).thenReturn(empty);
        when(inv.getItem(1)).thenReturn(empty);
        when(inv.getItem(2)).thenReturn(empty);

        editor.releaseEmptyPanes(gui, inv, List.of(0, 1));

        verify(inv).setItem(1, null);                  // дизайн-слот из набора
        verify(inv, never()).setItem(eq(0), any());    // скелет не трогаем
        verify(inv, never()).setItem(eq(2), any());    // слот вне набора не трогаем
    }

    @Test
    void releaseEmptyPanesWithNullSlotsCoversWholeDesign() {
        Gui gui = new Gui("test"); // по умолчанию все слоты DESIGN
        Inventory inv = inventory(gui.slots());
        ItemStack empty = pane(Material.LIME_STAINED_GLASS_PANE, EMPTY_NAME);
        when(inv.getItem(0)).thenReturn(empty);
        when(inv.getItem(gui.slots() - 1)).thenReturn(empty);

        editor.releaseEmptyPanes(gui, inv, null);

        verify(inv).setItem(0, null);
        verify(inv).setItem(gui.slots() - 1, null);
    }

    @Test
    void prepareDesignTransferReleasesPanesWhenThereIsRoom() {
        Gui gui = new Gui("test");
        Inventory inv = inventory(gui.slots());
        when(inv.getItem(0)).thenReturn(pane(Material.LIME_STAINED_GLASS_PANE, EMPTY_NAME));
        when(inv.getItem(1)).thenReturn(stack(Material.DIAMOND, 1));

        assertTrue(editor.prepareDesignTransfer(player, session(gui), inv, stack(Material.STONE, 4)));

        verify(inv).setItem(0, null);                // «Пусто» снято под перенос
        verify(inv, never()).setItem(eq(1), any());  // предмет дизайна не тронут
    }

    @Test
    void prepareDesignTransferWithoutRoomKeepsScreenIntact() {
        Gui gui = new Gui("test");
        Inventory inv = inventory(gui.slots());
        // Все дизайн-слоты заняты другими предметами — ваниль положить
        // предмет не сможет, значит и панели снимать незачем.
        ItemStack occupied = stack(Material.DIAMOND, 1);
        for (int i = 0; i < gui.slots(); i++) {
            when(inv.getItem(i)).thenReturn(occupied);
        }

        assertFalse(editor.prepareDesignTransfer(player, session(gui), inv, stack(Material.STONE, 4)));

        verify(inv, never()).setItem(anyInt(), any());
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void prepareDesignTransferRejectsEmptySource() {
        Gui gui = new Gui("test");
        Inventory inv = inventory(gui.slots());

        assertFalse(editor.prepareDesignTransfer(player, session(gui), inv, null));
        assertFalse(editor.prepareDesignTransfer(player, session(gui), inv, stack(Material.AIR, 0)));

        verify(inv, never()).setItem(anyInt(), any());
    }

    @Test
    void prepareDesignTransferMergesIntoMatchingStack() {
        Gui gui = new Gui("test");
        Inventory inv = inventory(gui.slots());
        // Единственный дизайн-слот занят таким же (неполным) стаком —
        // места нет содержимому, но есть куда досыпать.
        ItemStack source = stack(Material.DIAMOND, 4);
        ItemStack sameType = stack(Material.DIAMOND, 1);
        when(sameType.isSimilar(any(ItemStack.class))).thenReturn(true);
        for (int i = 0; i < gui.slots(); i++) {
            when(inv.getItem(i)).thenReturn(sameType);
        }

        assertTrue(editor.prepareDesignTransfer(player, session(gui), inv, source));
    }
}
