package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.api.functional.CraftingRecipe;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlock;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Статический фасад публичного API.
 *
 * <p>Для плагин-разработчиков, добавивших CustomGuiReworked как
 * compileOnly-зависимость, это самый удобный способ доступа.
 * Библиотеки без зависимости от классов плагина должны использовать
 * {@link GuiService} через Bukkit Services API.
 *
 * <pre>{@code
 * // создать GUI программно
 * Gui gui = GuiBuilder.named("shop")
 *         .title("§6Shop")
 *         .size(27)
 *         .slot(10, SlotType.CONTAINER)
 *         .slot(11, SlotType.CONTAINER)
 *         .slot(20, SlotType.CONTAINER)
 *         .design(0, new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
 *         .storage(StorageType.PERSONAL)
 *         .build();
 * CustomGuiAPI.saveGui(gui);
 *
 * // открыть
 * CustomGuiAPI.openGui(player, "shop");
 * }</pre>
 */
public final class CustomGuiAPI {

    private static volatile GuiService service;

    private CustomGuiAPI() {
    }

    /** Внутренний метод: вызывается плагином при включении. */
    public static void initialize(GuiService impl) {
        service = impl;
    }

    /** Внутренний метод: вызывается плагином при выключении. */
    public static void shutdown() {
        service = null;
    }

    /** Инициализирован ли API (плагин включён). */
    public static boolean isInitialized() {
        return service != null;
    }

    /**
     * Актуальный экземпляр сервиса.
     *
     * @throws IllegalStateException если плагин не включён
     */
    public static GuiService service() {
        GuiService current = service;
        if (current == null) {
            throw new IllegalStateException("CustomGuiReworked is not enabled (service not registered)");
        }
        return current;
    }

    // ================= GUI =================

    public static Gui getGui(String name) {
        return service().getGui(name);
    }

    public static boolean guiExists(String name) {
        return service().getGui(name) != null;
    }

    public static Set<String> getGuiNames() {
        return service().getGuiNames();
    }

    public static List<Gui> getAllGuis() {
        return new ArrayList<>(service().getGuis());
    }

    public static int getGuiCount() {
        return service().getGuiNames().size();
    }

    public static Gui createGui(String name) {
        return service().createGui(name);
    }

    /** Флюентный конструктор GUI. */
    public static GuiBuilder builder(String name) {
        return GuiBuilder.named(name);
    }

    /**
     * Регистрирует GUI из другого плагина (сохраняется в {@code custom/}
     * и переживает рестарт; данные хранилища для него работают как обычно).
     * Если GUI с таким именем уже есть — он заменяется.
     */
    public static Gui registerGui(Gui gui) {
        return service().registerGui(gui);
    }

    /**
     * Регистрирует GUI из другого плагина.
     *
     * @param persist false — GUI живёт только в памяти (без файла)
     */
    public static Gui registerGui(Gui gui, boolean persist) {
        return service().registerGui(gui, persist);
    }

    /** Снимает GUI, зарегистрированный через API. */
    public static boolean unregisterGui(String name, boolean deleteFile) {
        return service().unregisterGui(name, deleteFile);
    }

    /** Происхождение GUI: «table», «custom», «runtime» или «none». */
    public static String sourceOf(String name) {
        return service().sourceOf(name);
    }

    public static boolean deleteGui(String name) {
        return service().deleteGui(name);
    }

    public static void saveGui(Gui gui) {
        service().saveGui(gui);
    }

    // ================= открытие =================

    public static void openGui(Player player, String name) {
        service().openGui(player, name);
    }

    public static void openGui(Player player, String name, Location blockLocation) {
        service().openGui(player, name, blockLocation);
    }

    public static void openGui(Player player, String name, StorageType storageOverride) {
        service().openGui(player, name, storageOverride);
    }

    /** GUI, который игрок открыл прямо сейчас, или null. */
    public static Gui getOpenGui(Player player) {
        return service().getOpenGui(player);
    }

    /** Создаёт GUI (если нет) и сразу открывает его игроку. */
    public static void createAndOpenGui(Player player, String name) {
        createGui(name);
        openGui(player, name);
    }

    // ================= блоки =================

    public static void registerBlockGui(String blockId, String guiName) {
        service().registerBlockGui(blockId, guiName);
    }

    public static void unregisterBlockGui(String blockId) {
        service().unregisterBlockGui(blockId);
    }

    public static Gui getBlockGui(String blockId) {
        return service().getBlockGui(blockId);
    }

    // ================= хранилище =================

    public static List<ItemStack> readStorage(StorageType type, String owner, String table) {
        return service().readStorage(type, owner, table);
    }

    public static void writeStorage(StorageType type, String owner, String table, List<ItemStack> items) {
        service().writeStorage(type, owner, table, items);
    }

    public static void deleteStorage(StorageType type, String owner, String table) {
        service().deleteStorage(type, owner, table);
    }

    // ================= локальные оверрайды (per-viewer) =================

    /**
     * Локальное название окна открытого GUI игрока (legacy § цвета
     * поддерживаются). Не трогает файл GUI и других игроков.
     */
    public static void setLocalTitle(Player player, String title) {
        service().setLocalTitle(player, title);
    }

    /** Локальное название окна, либо null. */
    public static String getLocalTitle(Player player) {
        return service().getLocalTitle(player);
    }

    /** Возвращает название из файла GUI. */
    public static void clearLocalTitle(Player player) {
        service().clearLocalTitle(player);
    }

    /**
     * Локальный предмет в DESIGN/RESULT слоте открытого GUI игрока.
     * null — сбросить оверрайд (дизайн из файла).
     */
    public static void setLocalDesign(Player player, int slot, ItemStack item) {
        service().setLocalDesign(player, slot, item);
    }

    /** Несколько локальных предметов сразу (DESIGN/RESULT слоты). */
    public static void setLocalDesigns(Player player, Map<Integer, ItemStack> slots) {
        service().setLocalDesigns(player, slots);
    }

    /** Сброс одного локального предмета. */
    public static void clearLocalDesign(Player player, int slot) {
        service().clearLocalDesign(player, slot);
    }

    /** Сброс всех локальных предметов сессии. */
    public static void clearAllLocalDesigns(Player player) {
        service().clearAllLocalDesigns(player);
    }

    /** Локальный предмет слота, либо null. */
    public static ItemStack getLocalDesign(Player player, int slot) {
        return service().getLocalDesign(player, slot);
    }

    /**
     * Пер-блок + пер-плеер: локальный предмет для GUI, открытый
     * {@code player} на блоке {@code block}.
     */
    public static void setLocalDesign(Player player, Location block, int slot, ItemStack item) {
        service().setLocalDesign(player, block, slot, item);
    }

    /**
     * Пер-блок + пер-плеер: локальное название для GUI, открытый
     * {@code player} на блоке {@code block}.
     */
    public static void setLocalTitle(Player player, Location block, String title) {
        service().setLocalTitle(player, block, title);
    }

    // ================= утилиты =================

    /**
     * Готовит предмет к размещению как дизайн (maxStackSize + PDC-маркер,
     * анти-дюп). Аналог внутренних дизайн-предметов.
     */
    public static ItemStack prepareDesignItem(ItemStack item) {
        return service().prepareDesignItem(item);
    }

    /** Блок, на котором игрок открыл GUI прямо сейчас, либо null. */
    public static Location getOpenBlockLocation(Player player) {
        return service().getOpenBlockLocation(player);
    }

    /** Все игроки, у которых прямо сейчас открыт GUI на блоке. */
    public static List<Player> getViewers(Location block) {
        return service().getViewers(block);
    }

    // ================= крафт / топливо / результат =================

    /** Валиден ли рецепт крафта в инвентаре (CRAFT-слоты). */
    public static boolean matchesCraft(Inventory inventory, CraftingRecipe recipe) {
        return service().matchesCraft(inventory, recipe);
    }

    /** Расход до {@code amount} предметов из FUEL-слотов; возвращает фактический расход. */
    public static int consumeFuel(Inventory inventory, int amount) {
        return service().consumeFuel(inventory, amount);
    }

    /** Выдача результатов в RESULT-слоты (all-or-nothing); true — удалось. */
    public static boolean produceResult(Inventory inventory, Map<Integer, ItemStack> results) {
        return service().produceResult(inventory, results);
    }

    // ================= функциональные блоки =================

    /** Реестр функциональных блоков. */
    public static FunctionalBlockRegistry getFunctionalBlocks() {
        return service().getFunctionalBlocks();
    }

    /**
     * Builder функционального блока (печь/верстак/бочка/генератор).
     *
     * <pre>{@code
     * FunctionalBlock.builder("my_furnace")
     *     .gui("furnace")
     *     .onOpen((player, block, inv) ->
     *             CustomGuiAPI.setLocalTitle(player, "§6Печь " + block.getBlockX()))
     *     .onTick((block, inv) -> updateProgress(block, inv))
     *     .register();
     * }</pre>
     */
    public static FunctionalBlock.Builder functionalBlock(String blockId) {
        return FunctionalBlock.builder(blockId);
    }
}
