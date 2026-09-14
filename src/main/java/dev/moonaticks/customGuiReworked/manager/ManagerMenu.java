package dev.moonaticks.customGuiReworked.manager;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import dev.moonaticks.customGuiReworked.editor.EditorManager;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Меню управления GUI: список с поиском/пагинацией, экран опций
 * (открыть/редактировать/перезагрузить/удалить), создание нового GUI.
 */
public class ManagerMenu {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int PER_PAGE = 27;
    private static final long DELETE_ARM_MS = 5000;

    private final CustomGuiReworked plugin;
    private final GuiRegistry registry;
    private final GuiOpener opener;
    private final EditorManager editor;
    private final LanguageManager lang;
    private final Map<UUID, ManagerSession> sessions = new ConcurrentHashMap<>();

    public ManagerMenu(CustomGuiReworked plugin, GuiRegistry registry, GuiOpener opener,
                       EditorManager editor, LanguageManager lang) {
        this.plugin = plugin;
        this.registry = registry;
        this.opener = opener;
        this.editor = editor;
        this.lang = lang;
    }

    // ================= сессии =================

    public ManagerSession session(UUID uuid) {
        return sessions.get(uuid);
    }

    /** Активно ли меню (любой экран) у игрока. */
    public boolean isOpen(Player player) {
        return player != null
                && player.getOpenInventory().getTopInventory().getHolder() instanceof ManagerHolder;
    }

    public void closeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    // ================= открытие экранов =================

    /** Открывает список GUI. */
    public void open(Player player) {
        ManagerSession session = sessions.computeIfAbsent(player.getUniqueId(), ManagerSession::new);
        session.page(0);
        renderList(player, session);
    }

    private void renderList(Player player, ManagerSession session) {
        List<String> names = filteredNames(session.search());
        int pages = Math.max(1, (names.size() + PER_PAGE - 1) / PER_PAGE);
        if (session.page() >= pages) {
            session.page(pages - 1);
        }

        ManagerHolder holder = new ManagerHolder(ManagerHolder.Screen.LIST, null);
        Inventory inventory = Bukkit.createInventory(holder, 45, SERIALIZER.deserialize(lang.raw("manager.title.list")));

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, lang.raw("manager.filler"));
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, filler);
        }

        // Верхняя панель
        inventory.setItem(6, item(Material.PAPER, lang.raw("manager.btn.search"), lang.raw("manager.btn.searchHint")));
        inventory.setItem(7, item(Material.CHEST, lang.raw("manager.btn.create"), lang.raw("manager.btn.createHint")));
        inventory.setItem(8, item(Material.BLAZE_POWDER, lang.raw("manager.btn.reload"), lang.raw("manager.btn.reloadHint")));

        // Список
        int start = session.page() * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = start + i;
            if (index >= names.size()) {
                break;
            }
            String name = names.get(index);
            Gui gui = registry.get(name);
            if (gui == null) {
                continue;
            }
            int slot = 9 + i;
            inventory.setItem(slot, guiItem(gui));
            holder.put(slot, name);
        }

        // Нижняя панель
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.status.count"), names.size())));
        String search = session.search();
        statusLore.add(SERIALIZER.deserialize(search == null ? lang.raw("manager.status.noSearch")
                : String.format(lang.raw("manager.status.search"), search)));
        inventory.setItem(39, item(Material.CLOCK, lang.raw("manager.btn.status"), statusLore.toArray(new Component[0])));

        boolean hasPrev = session.page() > 0;
        boolean hasNext = session.page() < pages - 1;
        inventory.setItem(40, item(hasPrev ? Material.ARROW : Material.GRAY_DYE, lang.raw("manager.btn.prev")));
        inventory.setItem(41, item(Material.PAPER, String.format(lang.raw("manager.btn.page"), session.page() + 1, pages)));
        inventory.setItem(42, item(hasNext ? Material.ARROW : Material.GRAY_DYE, lang.raw("manager.btn.next")));

        holder.attach(inventory);
        player.openInventory(inventory);
    }

    private void renderOptions(Player player, ManagerSession session, String guiName) {
        Gui gui = registry.get(guiName);
        if (gui == null) {
            renderList(player, session);
            return;
        }
        session.disarm();

        ManagerHolder holder = new ManagerHolder(ManagerHolder.Screen.OPTIONS, gui.name());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                SERIALIZER.deserialize(String.format(lang.raw("manager.title.options"), gui.name())));

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, lang.raw("manager.filler"));
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(4, item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("manager.filler")));
        inventory.setItem(12, item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("manager.filler")));
        inventory.setItem(16, item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("manager.filler")));
        inventory.setItem(20, item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("manager.filler")));

        inventory.setItem(10, item(Material.EMERALD, lang.raw("manager.options.preview"), lang.raw("manager.options.previewHint")));
        inventory.setItem(11, item(Material.WRITABLE_BOOK, lang.raw("manager.options.edit"), lang.raw("manager.options.editHint")));
        boolean hasFile = gui.source() != Gui.Source.RUNTIME;
        inventory.setItem(12, item(hasFile ? Material.BLAZE_POWDER : Material.GRAY_DYE, lang.raw("manager.options.reload"),
                lang.raw(hasFile ? "manager.options.reloadHint" : "manager.options.reloadUnavailable")));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.title"), gui.title())));
        infoLore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.size"), gui.slots())));
        infoLore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.storage"), gui.storage().id())));
        infoLore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.blocks"), gui.blockIds().size())));
        infoLore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.commands"), gui.commands().size())));
        inventory.setItem(13, item(Material.BOOK, lang.raw("manager.options.info"), infoLore.toArray(new Component[0])));
        inventory.setItem(14, item(Material.TNT, lang.raw("manager.options.delete"),
                lang.raw("manager.options.deleteHint"), lang.raw("manager.options.deleteHint2")));
        inventory.setItem(22, item(Material.BARRIER, lang.raw("manager.options.back")));

        holder.attach(inventory);
        player.openInventory(inventory);
    }

    // ================= действия (вызываются из слушателя) =================

    /** Клик по элементу списка GUI. */
    public void onGuiClick(Player player, String guiName, org.bukkit.event.inventory.ClickType click) {
        ManagerSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Gui gui = registry.get(guiName);
        if (gui == null) {
            player.sendMessage(lang.msg("manager.notFound"));
            return;
        }
        switch (click) {
            case LEFT, NUMBER_KEY, SHIFT_NUMBER, DOUBLE_CLICK -> {
                if (!player.hasPermission("cgui.open")) {
                    player.sendMessage(lang.msg("noPermission"));
                    return;
                }
                opener.openForPlayer(player, gui);
            }
            case MIDDLE -> {
                if (!player.hasPermission("cgui.edit")) {
                    player.sendMessage(lang.msg("noPermission"));
                    return;
                }
                editor.openMain(player, gui);
            }
            case RIGHT, SWAP_OFFHAND, DROP, CONTROL_DROP -> {
                renderOptions(player, session, gui.name());
            }
            default -> {
                // игнорируем
            }
        }
    }

    /** Клик по экрану опций. */
    public void onOptionClick(Player player, String guiName, int slot) {
        ManagerSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Gui gui = registry.get(guiName);
        if (gui == null) {
            renderList(player, session);
            return;
        }
        switch (slot) {
            case 10 -> {
                if (!player.hasPermission("cgui.open")) {
                    player.sendMessage(lang.msg("noPermission"));
                    return;
                }
                opener.openForPlayer(player, gui);
            }
            case 11 -> {
                if (!player.hasPermission("cgui.edit")) {
                    player.sendMessage(lang.msg("noPermission"));
                    return;
                }
                editor.openMain(player, gui);
            }
            case 12 -> {
                if (gui.source() == Gui.Source.RUNTIME) {
                    player.sendMessage(lang.msg("manager.options.reloadUnavailable"));
                    return;
                }
                if (!player.hasPermission("cgui.reload")) {
                    player.sendMessage(lang.msg("noPermission"));
                    return;
                }
                if (registry.reload(gui.name()) == null) {
                    player.sendMessage(lang.msg("manager.notFound"));
                    return;
                }
                player.sendMessage(lang.msg("manager.options.reloaded"));
                renderOptions(player, session, gui.name());
            }
            case 14 -> {
                onDeleteClick(player, session, gui.name());
            }
            case 22 -> renderList(player, session);
            default -> {
                // остальное — декор
            }
        }
    }

    private void onDeleteClick(Player player, ManagerSession session, String guiName) {
        if (!player.hasPermission("cgui.delete")) {
            player.sendMessage(lang.msg("noPermission"));
            return;
        }
        long now = System.currentTimeMillis();
        if (session.isArmed(guiName, now)) {
            registry.delete(guiName);
            session.disarm();
            player.sendMessage(lang.msg("manager.deleted", guiName));
            renderList(player, session);
        } else {
            session.armDelete(guiName, now + DELETE_ARM_MS);
            player.sendMessage(lang.msg("manager.deleteArmed"));
        }
    }

    /** Клик «создать новый GUI» — запрашивает имя в чате. */
    public void onCreateClick(Player player) {
        if (!player.hasPermission("cgui.create")) {
            player.sendMessage(lang.msg("noPermission"));
            return;
        }
        ManagerSession session = sessions.computeIfAbsent(player.getUniqueId(), ManagerSession::new);
        session.creating(true);
        player.sendMessage(lang.msg("manager.createPrompt"));
    }

    /** Клик «перезагрузить всё». */
    public void onReloadClick(Player player) {
        if (!player.hasPermission("cgui.reload")) {
            player.sendMessage(lang.msg("noPermission"));
            return;
        }
        registry.loadAll();
        player.sendMessage(lang.msg("manager.reloadDone"));
        ManagerSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.page(0);
            renderList(player, session);
        }
    }

    /** Клик по статусу — очищает поиск. */
    public void onStatusClick(Player player) {
        ManagerSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.search() != null) {
            session.search(null);
            session.page(0);
            renderList(player, session);
        }
    }

    /** Поворот страницы. */
    public void onPaging(Player player, boolean next) {
        ManagerSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.page(session.page() + (next ? 1 : -1));
        renderList(player, session);
    }

    /** Игрок ввёл текст в чате, пока меню открыто. */
    public void onChat(Player player, String message) {
        ManagerSession session = sessions.get(player.getUniqueId());
        if (session == null || !isOpen(player)) {
            return;
        }
        if (session.isCreating()) {
            session.creating(false);
            String name = Gui.normalizeName(message);
            if (name.isEmpty() || name.length() > 32) {
                player.sendMessage(lang.msg("manager.nameInvalid"));
                return;
            }
            if (registry.get(name) != null) {
                player.sendMessage(lang.msg("manager.exists"));
                return;
            }
            Gui gui = registry.create(name);
            player.sendMessage(lang.msg("manager.created", gui.name()));
            editor.openMain(player, gui);
            return;
        }
        session.search(message);
        session.page(0);
        renderList(player, session);
    }

    private List<String> filteredNames(String search) {
        List<String> names = new ArrayList<>();
        String needle = search == null ? null : search.toLowerCase(Locale.ROOT);
        for (String name : registry.names()) {
            if (needle == null || name.toLowerCase(Locale.ROOT).contains(needle)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private ItemStack guiItem(Gui gui) {
        Material material = switch (gui.source()) {
            case CUSTOM -> Material.BOOKSHELF;
            case RUNTIME -> Material.ENDER_EYE;
            default -> Material.BOOK;
        };
        List<Component> lore = new ArrayList<>();
        lore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.title"), gui.title())));
        lore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.size"), gui.slots())));
        lore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.storage"), gui.storage().id())));
        lore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.blocks"), gui.blockIds().size())));
        lore.add(SERIALIZER.deserialize(String.format(lang.raw("manager.gui.commands"), gui.commands().size())));
        lore.add(SERIALIZER.deserialize(lang.raw("manager.gui.source." + gui.source().name().toLowerCase(Locale.ROOT))));
        lore.add(Component.empty());
        lore.add(SERIALIZER.deserialize(lang.raw("manager.gui.hint.open")));
        lore.add(SERIALIZER.deserialize(lang.raw("manager.gui.hint.edit")));
        lore.add(SERIALIZER.deserialize(lang.raw("manager.gui.hint.options")));
        return item(material, gui.name(), lore.toArray(new Component[0]));
    }

    // ================= утилиты =================

    private ItemStack item(Material material, String name) {
        return item(material, name, new Component[0]);
    }

    private ItemStack item(Material material, String name, String... loreLines) {
        Component[] lore = new Component[loreLines.length];
        for (int i = 0; i < loreLines.length; i++) {
            lore[i] = SERIALIZER.deserialize(loreLines[i]);
        }
        return item(material, name, lore);
    }

    private ItemStack item(Material material, String name, Component[] lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(SERIALIZER.deserialize(name).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (lore.length > 0) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
