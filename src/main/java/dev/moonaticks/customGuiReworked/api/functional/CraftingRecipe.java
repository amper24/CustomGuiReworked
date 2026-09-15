package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Рецепт крафта для функциональных блоков (печь, верстак и т.п.).
 *
 * <p>Рецепт описывает, какие предметы в каких слотах должны быть,
 * какой результат выдаётся и какое топливо расходуется:
 * <ul>
 *   <li>{@link #getIngredients()} — CRAFT-слоты: слот → предмет
 *       (необходимая вещь с требуемым количеством);</li>
 *   <li>{@link #getResults()} — RESULT-слоты: слот → выдаваемый предмет;</li>
 *   <li>{@link #getFuel()} — FUEL-слоты: слот → требуемое топливо
 *       (пусто, если блок не требует топливо);</li>
 *   <li>{@link #getCraftTimeTicks()} — длительность крафта в тиках
 *       (0 — мгновенно).</li>
 * </ul>
 *
 * <p>Простейший рецепт:
 * <pre>{@code
 * CraftingRecipe recipe = CraftingRecipe.simple(
 *         Map.of(13, new ItemStack(Material.LOG)),      // CRAFT слот 13
 *         Map.of(22, new ItemStack(Material.PLANK, 4)), // RESULT слот 22
 *         600);                                          // 30 секунд
 * }</pre>
 */
public interface CraftingRecipe {

    /** CRAFT-ингредиенты: слот → предмет (количество — сколько нужно). */
    Map<Integer, ItemStack> getIngredients();

    /** Результаты: RESULT-слот → предмет, который выдаётся. */
    Map<Integer, ItemStack> getResults();

    /** Топливо: FUEL-слот → предмет, который расходуется. Пусто по умолчанию. */
    default Map<Integer, ItemStack> getFuel() {
        return Collections.emptyMap();
    }

    /** Длительность крафта в тиках (0 — мгновенно). */
    int getCraftTimeTicks();

    /**
     * Проверяет, соответствует ли содержимое инвентаря рецепту:
     * каждый ингредиент-слот содержит similar-предмет с достаточным
     * количеством, а все остальные CRAFT-слоты GUI пусты.
     *
     * @param inv инвентарь GUI
     * @param gui GUI (для перечисления CRAFT-слотов; может быть null —
     *            тогда проверяются только ingredient-слоты)
     * @return true, если рецепт валиден
     */
    default boolean matches(Inventory inv, Gui gui) {
        if (inv == null) {
            return false;
        }
        Map<Integer, ItemStack> ingredients = getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        for (Map.Entry<Integer, ItemStack> entry : ingredients.entrySet()) {
            ItemStack needed = entry.getValue();
            if (needed == null || needed.getType() == Material.AIR) {
                continue;
            }
            ItemStack actual = inv.getItem(entry.getKey());
            if (actual == null || actual.getType() == Material.AIR
                    || !actual.isSimilar(needed)
                    || actual.getAmount() < needed.getAmount()) {
                return false;
            }
        }
        if (gui != null) {
            for (int i = 0; i < gui.slots(); i++) {
                if (gui.slotType(i) != SlotType.CRAFT || ingredients.containsKey(i)) {
                    continue;
                }
                ItemStack actual = inv.getItem(i);
                if (actual != null && actual.getType() != Material.AIR) {
                    return false; // лишний предмет в CRAFT-слоте
                }
            }
        }
        return true;
    }

    // ================= фабрики =================

    /**
     * Создаёт простой рецепт без топлива.
     *
     * @param ingredients CRAFT-слоты (слот → предмет)
     * @param results     RESULT-слоты (слот → предмет)
     * @param timeTicks   длительность в тиках (0 — мгновенно)
     * @return рецепт; {@link #getFuel()} пуст
     */
    static SimpleRecipe simple(Map<Integer, ItemStack> ingredients,
                               Map<Integer, ItemStack> results, int timeTicks) {
        return new SimpleRecipe(ingredients, results, Collections.emptyMap(), timeTicks);
    }

    /**
     * Создаёт рецепт с топливом.
     *
     * @param ingredients CRAFT-слоты (слот → предмет)
     * @param results     RESULT-слоты (слот → предмет)
     * @param fuel        FUEL-слоты (слот → предмет)
     * @param timeTicks   длительность в тиках (0 — мгновенно)
     * @return рецепт
     */
    static SimpleRecipe of(Map<Integer, ItemStack> ingredients,
                           Map<Integer, ItemStack> results,
                           Map<Integer, ItemStack> fuel, int timeTicks) {
        return new SimpleRecipe(ingredients, results, fuel, timeTicks);
    }

    /**
     * Конкретная реализация рецепта (иммутабельная, потокобезопасная).
     */
    final class SimpleRecipe implements CraftingRecipe {

        private final Map<Integer, ItemStack> ingredients;
        private final Map<Integer, ItemStack> results;
        private final Map<Integer, ItemStack> fuel;
        private final int craftTimeTicks;

        public SimpleRecipe(Map<Integer, ItemStack> ingredients,
                            Map<Integer, ItemStack> results,
                            Map<Integer, ItemStack> fuel,
                            int craftTimeTicks) {
            this.ingredients = copyOf(ingredients);
            this.results = copyOf(results);
            this.fuel = copyOf(fuel);
            this.craftTimeTicks = Math.max(0, craftTimeTicks);
        }

        @Override
        public Map<Integer, ItemStack> getIngredients() {
            return ingredients;
        }

        @Override
        public Map<Integer, ItemStack> getResults() {
            return results;
        }

        @Override
        public Map<Integer, ItemStack> getFuel() {
            return fuel;
        }

        @Override
        public int getCraftTimeTicks() {
            return craftTimeTicks;
        }

        /** Возвращает копию рецепта с другим топливом (оригинал не меняется). */
        public SimpleRecipe withFuel(Map<Integer, ItemStack> fuel) {
            return new SimpleRecipe(ingredients, results, fuel, craftTimeTicks);
        }

        /** Возвращает копию рецепта с другой длительностью (оригинал не меняется). */
        public SimpleRecipe withTime(int timeTicks) {
            return new SimpleRecipe(ingredients, results, fuel, timeTicks);
        }

        private static Map<Integer, ItemStack> copyOf(Map<Integer, ItemStack> source) {
            if (source == null || source.isEmpty()) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
