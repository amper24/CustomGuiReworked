package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Builder функционального блока — самый удобный способ подключить
 * «умный» блок (печь, верстак, бочку, генератор) к GUI.
 *
 * <p>Пример — печь с топливом и прогрессом в DESIGN-слоте 4:
 * <pre>{@code
 * FunctionalBlock.builder("custom_furnace")
 *     .gui("furnace")
 *     .onOpen((player, block, inv) ->
 *             CustomGuiAPI.setLocalTitle(player,
 *                     "§6Фурна " + block.getBlockX() + "," + block.getBlockZ()))
 *     .onTick((block, inv) -> {
 *         int progress = getProgress(block);
 *         for (Player viewer : CustomGuiAPI.getViewers(block)) {
 *             CustomGuiAPI.setLocalDesign(viewer, 4, progressItem(progress));
 *         }
 *     })
 *     .craftingRecipe(CraftingRecipe.simple(
 *             Map.of(13, new ItemStack(Material.IRON_ORE)),
 *             Map.of(22, new ItemStack(Material.IRON_INGOT)),
 *             600))
 *     .fuelConsumption(Map.of(14, 1))
 *     .register();
 * }</pre>
 *
 * <p>{@code register()} запоминает обработчик в реестре плагина
 * и привязывает ID блока к GUI (правый клик по блоку откроет его).
 */
public final class FunctionalBlock {

    private FunctionalBlock() {
    }

    /**
     * Создаёт builder функционального блока.
     *
     * @param blockId ID кастомного блока (itemsadder:/craftengine:)
     * @throws IllegalStateException если плагин CustomGuiReworked не включён
     */
    public static Builder builder(String blockId) {
        return new Builder(blockId, CustomGuiAPI.service().getFunctionalBlocks());
    }

    /**
     * Fluent-конструктор {@link FunctionalBlockHandler}.
     */
    public static final class Builder {

        private final String blockId;
        private final FunctionalBlockRegistry registry;

        private String guiName;
        private BiPredicate<Player, Location> canOpen;
        private OnOpen onOpen;
        private OnClick onClick;
        private OnItemChanged onItemChanged;
        private OnClose onClose;
        private OnTick onTick;
        private OnBlockTick onBlockTick;
        private CraftingRecipe recipe;
        private Map<Integer, Integer> fuelConsumption;

        public Builder(String blockId, FunctionalBlockRegistry registry) {
            this.blockId = blockId;
            this.registry = registry;
        }

        /** GUI, который открывает этот блок (обязательно). */
        public Builder gui(String guiName) {
            this.guiName = guiName;
            return this;
        }

        /** Проверка доступа: {@code false} — GUI не открывается (клик «съедается»). */
        public Builder canOpen(BiPredicate<Player, Location> predicate) {
            this.canOpen = predicate;
            return this;
        }

        /** Колбэк открытия (до показа окна; здесь удобно сетаить локальный title/design). */
        public Builder onOpen(OnOpen callback) {
            this.onOpen = callback;
            return this;
        }

        /** Колбэк клика по любому слоту этого GUI. */
        public Builder onClick(OnClick callback) {
            this.onClick = callback;
            return this;
        }

        /**
         * Колбэк изменения содержимого слота (положили/забрали/перенесли,
         * либо плагин сделал produceResult/consumeFuel) — с предметами
         * «было/стало». Удобная точка для запуска крафта.
         */
        public Builder onItemChanged(OnItemChanged callback) {
            this.onItemChanged = callback;
            return this;
        }

        /**
         * Серверный колбэк «работающего» блока: тикает каждые 5 тиков,
         * пока включена {@code CustomGuiAPI.setWorking(block, true)} —
         * даже когда GUI закрыт (варка без зрителей).
         */
        public Builder onBlockTick(OnBlockTick callback) {
            this.onBlockTick = callback;
            return this;
        }

        /** Колбэк закрытия GUI. */
        public Builder onClose(OnClose callback) {
            this.onClose = callback;
            return this;
        }

        /** Колбэк тика (каждые 5 тиков, per-зритель). */
        public Builder onTick(OnTick callback) {
            this.onTick = callback;
            return this;
        }

        /** Рецепт крафта блока (опционально). */
        public Builder craftingRecipe(CraftingRecipe recipe) {
            this.recipe = recipe;
            return this;
        }

        /**
         * Расход топлива на один крафт: FUEL-слот → количество.
         * Опционально; вместе с рецептом даёт готовую основу для
         * «крафт + топливо» (см. {@link FunctionalBlockHandler#getFuelConsumption()}).
         */
        public Builder fuelConsumption(Map<Integer, Integer> slotsToAmounts) {
            this.fuelConsumption = slotsToAmounts;
            return this;
        }

        /**
         * Регистрирует функциональный блок в плагине
         * (ID блока → GUI, обработчик в реестре).
         *
         * @return обработчик (для сохранения ссылки/тестов)
         */
        public FunctionalBlockHandler register() {
            FunctionalBlockHandler handler = build();
            registry.registerHandler(blockId, handler);
            return handler;
        }

        /**
         * Собирает обработчик без регистрации.
         *
         * @throws IllegalStateException если не задано имя GUI
         */
        public FunctionalBlockHandler build() {
            if (guiName == null || guiName.isBlank()) {
                throw new IllegalStateException("gui(...) is required for block '" + blockId + "'");
            }
            return new HandlerImpl(guiName, canOpen, onOpen, onClick, onItemChanged,
                    onClose, onTick, onBlockTick, recipe, fuelConsumption);
        }

        // ================= колбэки =================

        /** Колбэк открытия. */
        @FunctionalInterface
        public interface OnOpen {
            void onOpen(Player player, Location block, Inventory inv);
        }

        /** Колбэк клика по слоту. */
        @FunctionalInterface
        public interface OnClick {
            void onClick(Player player, Location block, int slot, SlotType type, GuiSlotClickEvent event);
        }

        /** Колбэк изменения содержимого слота (предметы «было/стало»). */
        @FunctionalInterface
        public interface OnItemChanged {
            void onItemChanged(Player player, Location block, int slot, SlotType type,
                               ItemStack oldItem, ItemStack newItem);
        }

        /**
         * Серверный тик «работающего» блока (каждые 5 тиков, без зрителей).
         */
        @FunctionalInterface
        public interface OnBlockTick {
            void onBlockTick(Location block, FunctionalBlockData data);
        }

        /** Колбэк закрытия. */
        @FunctionalInterface
        public interface OnClose {
            void onClose(Player player, Location block);
        }

        /** Колбэк тика (per-зритель). */
        @FunctionalInterface
        public interface OnTick {
            void onTick(Location block, Inventory inv);
        }
    }

    /** Реализация обработчика, собранная builder'ом. */
    private static final class HandlerImpl implements FunctionalBlockHandler {

        private final String guiName;
        private final BiPredicate<Player, Location> canOpen;
        private final Builder.OnOpen onOpen;
        private final Builder.OnClick onClick;
        private final Builder.OnItemChanged onItemChanged;
        private final Builder.OnClose onClose;
        private final Builder.OnTick onTick;
        private final Builder.OnBlockTick onBlockTick;
        private final CraftingRecipe recipe;
        private final Map<Integer, Integer> fuelConsumption;

        HandlerImpl(String guiName, BiPredicate<Player, Location> canOpen,
                    Builder.OnOpen onOpen, Builder.OnClick onClick, Builder.OnItemChanged onItemChanged,
                    Builder.OnClose onClose, Builder.OnTick onTick, Builder.OnBlockTick onBlockTick,
                    CraftingRecipe recipe, Map<Integer, Integer> fuelConsumption) {
            this.guiName = guiName;
            this.canOpen = canOpen;
            this.onOpen = onOpen;
            this.onClick = onClick;
            this.onItemChanged = onItemChanged;
            this.onClose = onClose;
            this.onTick = onTick;
            this.onBlockTick = onBlockTick;
            this.recipe = recipe;
            this.fuelConsumption = fuelConsumption == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(fuelConsumption));
        }

        @Override
        public String getGuiName() {
            return guiName;
        }

        @Override
        public boolean canOpen(Player player, Location block) {
            return canOpen == null || canOpen.test(player, block);
        }

        @Override
        public void onOpen(Player player, Location block, Inventory inv) {
            if (onOpen != null) {
                onOpen.onOpen(player, block, inv);
            }
        }

        @Override
        public void onClick(Player player, Location block, int slot, SlotType type, GuiSlotClickEvent event) {
            if (onClick != null) {
                onClick.onClick(player, block, slot, type, event);
            }
        }

        @Override
        public void onItemChanged(Player player, Location block, int slot, SlotType type,
                                  ItemStack oldItem, ItemStack newItem) {
            if (onItemChanged != null) {
                onItemChanged.onItemChanged(player, block, slot, type, oldItem, newItem);
            }
        }

        @Override
        public void onClose(Player player, Location block) {
            if (onClose != null) {
                onClose.onClose(player, block);
            }
        }

        @Override
        public void onTick(Location block, Inventory inv) {
            if (onTick != null) {
                onTick.onTick(block, inv);
            }
        }

        @Override
        public void onBlockTick(Location block, FunctionalBlockData data) {
            if (onBlockTick != null) {
                onBlockTick.onBlockTick(block, data);
            }
        }

        @Override
        public CraftingRecipe getCraftingRecipe() {
            return recipe;
        }

        @Override
        public Map<Integer, Integer> getFuelConsumption() {
            return fuelConsumption;
        }
    }
}
