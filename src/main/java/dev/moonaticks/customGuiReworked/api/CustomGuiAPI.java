package dev.moonaticks.customGuiReworked.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
        GuiService current = service();
        List<Gui> list = new ArrayList<>();
        for (String name : current.getGuiNames()) {
            Gui gui = current.getGui(name);
            if (gui != null) {
                list.add(gui);
            }
        }
        return list;
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
}
