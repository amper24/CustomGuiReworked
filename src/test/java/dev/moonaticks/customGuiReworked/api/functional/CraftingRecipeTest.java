package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты {@link CraftingRecipe}: фабрики, иммутабельность,
 * логика {@code matches} (CRAFT-слоты против ингредиентов).
 */
class CraftingRecipeTest {

    private static ItemStack mockItem(Material type, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }

    /** GUI: 27 слотов, CRAFT — 13 и 14, RESULT — 22. */
    private static Gui craftGui() {
        Gui gui = new Gui("furnace");
        gui.setSlotType(13, SlotType.CRAFT);
        gui.setSlotType(14, SlotType.CRAFT);
        gui.setSlotType(22, SlotType.RESULT);
        return gui;
    }

    @Test
    void simpleFactoryFillsMapsAndTime() {
        ItemStack ore = mockItem(Material.IRON_ORE, 1);
        ItemStack ingot = mockItem(Material.IRON_INGOT, 4);
        CraftingRecipe recipe = CraftingRecipe.simple(
                Map.of(13, ore), Map.of(22, ingot), 600);
        assertEquals(1, recipe.getIngredients().size());
        assertEquals(ore, recipe.getIngredients().get(13));
        assertEquals(ingot, recipe.getResults().get(22));
        assertTrue(recipe.getFuel().isEmpty(), "simple — без топлива");
        assertEquals(600, recipe.getCraftTimeTicks());
    }

    @Test
    void ofFactoryKeepsFuelAndWithersAreImmutable() {
        ItemStack ore = mockItem(Material.IRON_ORE, 1);
        ItemStack ingot = mockItem(Material.IRON_INGOT, 4);
        ItemStack coal = mockItem(Material.COAL, 1);
        CraftingRecipe.SimpleRecipe recipe = CraftingRecipe.of(
                Map.of(13, ore), Map.of(22, ingot), Map.of(14, coal), 200);
        assertEquals(coal, recipe.getFuel().get(14));

        CraftingRecipe.SimpleRecipe fueled = recipe.withFuel(Map.of(14, mockItem(Material.LAVA_BUCKET, 1)));
        assertEquals(coal, recipe.getFuel().get(14), "оригинал не изменился");
        assertEquals(1, fueled.getFuel().get(14).getAmount());

        CraftingRecipe.SimpleRecipe faster = recipe.withTime(10);
        assertEquals(200, recipe.getCraftTimeTicks(), "оригинал не изменился");
        assertEquals(10, faster.getCraftTimeTicks());
    }

    @Test
    void negativeTimeClampedToZero() {
        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, mockItem(Material.COAL, 1)), Map.of(), -5);
        assertEquals(0, recipe.getCraftTimeTicks());
    }

    @Test
    void matchesWhenIngredientsPresentAndRestEmpty() {
        Gui gui = craftGui();
        ItemStack needed = mockItem(Material.IRON_ORE, 1);
        ItemStack actual = mockItem(Material.IRON_ORE, 1);
        when(actual.isSimilar(needed)).thenReturn(true);

        Inventory inv = mock(Inventory.class);
        when(inv.getItem(13)).thenReturn(actual);
        when(inv.getItem(14)).thenReturn(null); // остальные CRAFT пусты

        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, needed), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 600);
        assertTrue(recipe.matches(inv, gui));
    }

    @Test
    void matchesNotWhenAmountInsufficient() {
        Gui gui = craftGui();
        ItemStack needed = mockItem(Material.IRON_ORE, 3);
        ItemStack actual = mockItem(Material.IRON_ORE, 2);
        when(actual.isSimilar(needed)).thenReturn(true);

        Inventory inv = mock(Inventory.class);
        when(inv.getItem(13)).thenReturn(actual);
        when(inv.getItem(14)).thenReturn(null);

        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, needed), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 600);
        assertFalse(recipe.matches(inv, gui), "не хватает количества");
    }

    @Test
    void matchesNotWhenItemDiffers() {
        Gui gui = craftGui();
        ItemStack needed = mockItem(Material.IRON_ORE, 1);
        ItemStack actual = mockItem(Material.GOLD_ORE, 1);
        when(actual.isSimilar(needed)).thenReturn(false);

        Inventory inv = mock(Inventory.class);
        when(inv.getItem(13)).thenReturn(actual);
        when(inv.getItem(14)).thenReturn(null);

        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, needed), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 600);
        assertFalse(recipe.matches(inv, gui), "другой предмет");
    }

    @Test
    void matchesNotWhenExtraItemInOtherCraftSlot() {
        Gui gui = craftGui();
        ItemStack needed = mockItem(Material.IRON_ORE, 1);
        ItemStack actual = mockItem(Material.IRON_ORE, 1);
        when(actual.isSimilar(needed)).thenReturn(true);

        Inventory inv = mock(Inventory.class);
        when(inv.getItem(13)).thenReturn(actual);
        when(inv.getItem(14)).thenReturn(mockItem(Material.DIRT, 1)); // лишний

        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, needed), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 600);
        assertFalse(recipe.matches(inv, gui), "лишний предмет в CRAFT-слоте");
    }

    @Test
    void matchesNotWhenIngredientMissingOrNullInventory() {
        Gui gui = craftGui();
        ItemStack needed = mockItem(Material.IRON_ORE, 1);
        Inventory inv = mock(Inventory.class);
        when(inv.getItem(13)).thenReturn(null);
        when(inv.getItem(14)).thenReturn(null);
        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, needed), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 600);
        assertFalse(recipe.matches(inv, gui));
        assertFalse(recipe.matches(null, gui), "null-инвентарь — false");
    }

    @Test
    void matchesNotWhenRecipeHasNoIngredients() {
        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 10);
        assertFalse(recipe.matches(mock(Inventory.class), craftGui()), "пустой рецепт не может совпасть");
    }

    @Test
    void matchesWithoutGuiChecksOnlyIngredientSlots() {
        ItemStack needed = mockItem(Material.IRON_ORE, 1);
        ItemStack actual = mockItem(Material.IRON_ORE, 1);
        when(actual.isSimilar(needed)).thenReturn(true);
        Inventory inv = mock(Inventory.class);
        when(inv.getItem(13)).thenReturn(actual);
        // Без Gui не перечисляются прочие CRAFT-слоты — проверяется только 13.
        CraftingRecipe recipe = CraftingRecipe.simple(Map.of(13, needed), Map.of(22, mockItem(Material.IRON_INGOT, 1)), 10);
        assertTrue(recipe.matches(inv, null));
    }

    @Test
    void builderRequiresGuiName() {
        FunctionalBlock.Builder builder = new FunctionalBlock.Builder("some_block", null);
        assertThrows(IllegalStateException.class, builder::build);
        builder.gui("furnace");
        assertEquals("furnace", builder.build().getGuiName());
    }
}
