package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Визуальный редактор GUI: сессии игроков и построение экранов.
 *
 * <p>Экраны:
 * <ul>
 *   <li><b>MAIN</b> — сводка и навигация;</li>
 *   <li><b>SIZE</b> — выбор размера (9..54);</li>
 *   <li><b>SKELETON</b> — типы слотов (клик — циклическая смена,
 *       shift-клик — Design);</li>
 *   <li><b>DESIGN</b> — расстановка предметов в дизайн-слотах
 *       (right-click по пустому — очистить);</li>
 *   <li><b>STORAGE</b> — тип хранилища;</li>
 *   <li><b>BLOCKS</b> — привязка ID кастомных блоков.</li>
 * </ul>
 *
 * <p>Все действия сохраняются мгновенно; экраны обновляются
 * без мигания (вставка предметов «на месте», а не reopening окна).
 */
public class EditorManager {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final CustomGuiReworked plugin;
    private final GuiRegistry registry;
    private final LanguageManager lang;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();

    public EditorManager(CustomGuiReworked plugin, GuiRegistry registry, LanguageManager lang) {
        this.plugin = plugin;
        this.registry = registry;
        this.lang = lang;
    }

    public GuiRegistry registry() {
        return registry;
    }

    public LanguageManager lang() {
        return lang;
    }

    // ================= сессии =================

    public EditorSession session(UUID uuid) {
        return sessions.get(uuid);
    }

    public EditorSession startSession(Player player, Gui gui) {
        EditorSession session = new EditorSession(player.getUniqueId(), gui);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    // ================= навигация =================

    public void openMain(Player player, Gui gui) {
        player.openInventory(mainScreen(startSession(player, gui)));
    }

    public void openScreen(Player player, EditorSession session, EditorHolder.Screen screen) {
        player.openInventory(screen(session, screen));
    }

    /** Строит инвентарь указанного экрана редактора. */
    public Inventory screen(EditorSession session, EditorHolder.Screen screen) {
        return switch (screen) {
            case MAIN -> mainScreen(session);
            case SIZE -> sizeScreen(session);
            case SKELETON -> skeletonScreen(session);
            case DESIGN -> designScreen(session);
            case STORAGE -> storageScreen(session);
            case BLOCKS -> blocksScreen(session);
        };
    }

    public void deleteGui(Player player, EditorSession session) {
        Gui gui = session.gui();
        registry.delete(gui.name());
        removeSession(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(lang.msg("editor.deleted", gui.name()));
    }

    // ================= экраны =================

    public Inventory mainScreen(EditorSession session) {
        Gui gui = session.gui();
        Inventory inv = createScreen(session, EditorHolder.Screen.MAIN, 27, "editor.mainTitle");
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("editor.filler"));
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        for (int i = 17; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(9, infoItem(gui));
        inv.setItem(10, button(Material.CHEST, "editor.btn.size"));
        inv.setItem(11, button(Material.BONE, "editor.btn.skeleton"));
        inv.setItem(12, button(Material.PAINTING, "editor.btn.design"));
        inv.setItem(13, button(Material.WRITABLE_BOOK, "editor.btn.title"));
        inv.setItem(14, button(Material.ENDER_CHEST, "editor.btn.storage"));
        inv.setItem(15, button(Material.MAP, "editor.btn.preview"));
        inv.setItem(16, button(Material.COMPASS, "editor.btn.blocks"));
        inv.setItem(22, button(Material.BARRIER, "editor.btn.back"));
        inv.setItem(25, button(Material.TNT, "editor.btn.delete"));
        return inv;
    }

    public Inventory sizeScreen(EditorSession session) {
        Inventory inv = createScreen(session, EditorHolder.Screen.SIZE, 27, "editor.sizeTitle");
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("editor.filler"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        int[] sizes = {9, 18, 27, 36, 45, 54};
        for (int i = 0; i < sizes.length; i++) {
            ItemStack chest = new ItemStack(Material.CHEST, 1);
            ItemMeta meta = chest.getItemMeta();
            if (meta != null) {
                meta.displayName(legacy(lang.raw("editor.size.option").replace("%s", String.valueOf(sizes[i]))));
                if (sizes[i] == session.gui().slots()) {
                    meta.setEnchantmentGlintOverride(true);
                    meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true));
                }
                chest.setItemMeta(meta);
            }
            chest.setAmount(sizes[i]);
            inv.setItem(10 + i, chest);
        }
        inv.setItem(22, button(Material.BARRIER, "editor.btn.back"));
        return inv;
    }

    public Inventory skeletonScreen(EditorSession session) {
        Gui gui = session.gui();
        Inventory inv = createScreen(session, EditorHolder.Screen.SKELETON, gui.slots(), "editor.skeletonTitle");
        for (int i = 0; i < gui.slots(); i++) {
            inv.setItem(i, skeletonItem(gui.slotType(i)));
        }
        return inv;
    }

    public Inventory designScreen(EditorSession session) {
        Gui gui = session.gui();
        Inventory inv = createScreen(session, EditorHolder.Screen.DESIGN, gui.slots(), "editor.designTitle");
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) == SlotType.DESIGN) {
                ItemStack item = Codecs.decode(gui.designAt(i));
                if (item != null && item.getType() != Material.AIR) {
                    inv.setItem(i, item);
                } else {
                    inv.setItem(i, item(Material.LIME_STAINED_GLASS_PANE, lang.raw("editor.design.empty")));
                }
            } else {
                inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, lang.raw("editor.design.locked")));
            }
        }
        return inv;
    }

    public Inventory storageScreen(EditorSession session) {
        Gui gui = session.gui();
        Inventory inv = createScreen(session, EditorHolder.Screen.STORAGE, 27, "editor.storageTitle");
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("editor.filler"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        StorageType[] types = {StorageType.BLOCK, StorageType.PERSONAL, StorageType.GLOBAL, StorageType.TEAM, StorageType.TEMPORARY};
        Material[] materials = {Material.CHEST, Material.PLAYER_HEAD, Material.ENDER_CHEST, Material.LIGHT_BLUE_WOOL, Material.BARRIER};
        String[] nameKeys = {"storage.block.name", "storage.personal.name", "storage.global.name", "storage.team.name", "storage.temporary.name"};
        String[] descKeys = {"storage.block.desc", "storage.personal.desc", "storage.global.desc", "storage.team.desc", "storage.temporary.desc"};
        for (int i = 0; i < types.length; i++) {
            List<Component> lore = new ArrayList<>();
            lore.add(legacy(lang.raw(descKeys[i])));
            ItemStack stack = loreItem(materials[i], lang.raw(nameKeys[i]), lore);
            if (types[i] == gui.storage()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setEnchantmentGlintOverride(true);
                    meta.displayName(meta.displayName().append(legacy(lang.raw("storage.current"))));
                    stack.setItemMeta(meta);
                }
            }
            inv.setItem(11 + i, stack);
        }
        inv.setItem(22, button(Material.BARRIER, "editor.btn.back"));
        return inv;
    }

    public Inventory blocksScreen(EditorSession session) {
        Gui gui = session.gui();
        Inventory inv = createScreen(session, EditorHolder.Screen.BLOCKS, 27, "editor.blocksTitle");
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, lang.raw("editor.filler"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(10, button(Material.NAME_TAG, "editor.blocks.add"));
        int slot = 11;
        for (String id : gui.blockIds()) {
            if (slot > 16) {
                break;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(legacy(lang.raw("editor.blocks.removeHint")));
            inv.setItem(slot++, loreItem(Material.PAPER, id, lore));
        }
        inv.setItem(22, button(Material.BARRIER, "editor.btn.back"));
        return inv;
    }

    // ================= действия =================

    public void setSlots(Player player, EditorSession session, int newSlots) {
        Gui gui = session.gui();
        gui.slots(newSlots);
        registry.save(gui);
        player.sendMessage(lang.msg("editor.sizeChanged", gui.slots()));
        openMain(player, gui);
    }

    /**
     * Меняет тип слота: циклически, либо принудительно (shift-клик → DESIGN).
     * Инвентарь обновляется «на месте».
     */
    public void cycleSlotType(Player player, EditorSession session, int slot, SlotType forced) {
        Gui gui = session.gui();
        if (slot < 0 || slot >= gui.slots()) {
            return;
        }
        SlotType current = gui.slotType(slot);
        SlotType next = forced != null ? forced : nextType(current);
        gui.setSlotType(slot, next);
        registry.save(gui);
        Inventory inv = currentEditorInventory(player, session, EditorHolder.Screen.SKELETON);
        if (inv != null) {
            inv.setItem(slot, skeletonItem(next));
        }
    }

    /** Переснимает дизайн-слоты из открытого инвентаря и сохраняет.
     * Сообщение «сохранено» показываем только при реальных изменениях. */
    public void captureDesign(Player player, EditorSession session) {
        Inventory inv = currentEditorInventory(player, session, EditorHolder.Screen.DESIGN);
        if (inv == null) {
            return;
        }
        Gui gui = session.gui();
        boolean changed = false;
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) != SlotType.DESIGN) {
                continue;
            }
            ItemStack item = inv.getItem(i);
            if (item != null && isPlaceholderItem(item)) {
                continue; // служебная панель «пусто» — не сохраняем
            }
            String encoded = Codecs.encode(item);
            if (!encoded.equals(gui.designAt(i))) {
                gui.setDesignAt(i, encoded);
                changed = true;
            }
        }
        if (changed) {
            registry.save(gui);
            player.sendMessage(lang.msg("editor.saved"));
        }
        // Возвращаем визуальные панели в опустевшие слоты,
        // чтобы экран не «дырявился» после забора предметов.
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) == SlotType.DESIGN
                    && (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR)) {
                inv.setItem(i, item(Material.LIME_STAINED_GLASS_PANE, lang.raw("editor.design.empty")));
            }
        }
    }

    /** true, если предмет — служебная панель-заглушка пустого дизайн-слота. */
    public boolean isPlaceholderItem(ItemStack item) {
        return isPlaceholder(item);
    }

    /** Сбрасывает чат-промпт по таймауту (5 минут), если игрок так ничего и не ввёл. */
    public void schedulePromptTimeout(Player player, EditorSession session) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && session(player.getUniqueId()) == session
                    && session.prompt() != EditorSession.Prompt.NONE) {
                session.prompt(EditorSession.Prompt.NONE);
                player.sendMessage(lang.msg("editor.promptTimeout"));
            }
        }, 20L * 60 * 5);
    }

    /** Очищает один дизайн-слот (right-click по пустой руке). */
    public void clearDesignSlot(Player player, EditorSession session, int slot) {
        Gui gui = session.gui();
        if (slot < 0 || slot >= gui.slots() || gui.slotType(slot) != SlotType.DESIGN) {
            return;
        }
        gui.setDesignAt(slot, "");
        Inventory inv = currentEditorInventory(player, session, EditorHolder.Screen.DESIGN);
        if (inv != null) {
            inv.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, lang.raw("editor.design.empty")));
        }
        registry.save(gui);
    }

    public void setStorage(Player player, EditorSession session, StorageType type) {
        session.gui().storage(type);
        registry.save(session.gui());
        player.sendMessage(lang.msg("editor.storageChanged", storageName(type)));
        openMain(player, session.gui());
    }

    public void setTitle(Player player, EditorSession session, String title) {
        session.gui().title(title);
        registry.save(session.gui());
        player.sendMessage(lang.msg("editor.titleSet", title));
        openMain(player, session.gui());
    }

    public void addBlockId(Player player, EditorSession session, String id) {
        Gui gui = session.gui();
        if (gui.blockIds().contains(id) || gui.blockIds().stream().anyMatch(b -> b.equalsIgnoreCase(id))) {
            player.sendMessage(lang.msg("editor.blockAlready", id));
            openScreen(player, session, EditorHolder.Screen.BLOCKS);
            return;
        }
        gui.addBlockId(id);
        registry.save(gui);
        player.sendMessage(lang.msg("editor.blockAdded", id));
        openMain(player, session.gui());
    }

    public void removeBlockId(Player player, EditorSession session, String id) {
        session.gui().removeBlockId(id);
        registry.save(session.gui());
        player.sendMessage(lang.msg("editor.blockRemoved", id));
        openScreen(player, session, EditorHolder.Screen.BLOCKS);
    }

    // ================= утилиты =================

    private static SlotType nextType(SlotType current) {
        SlotType[] all = SlotType.values();
        return all[(current.ordinal() + 1) % all.length];
    }

    private String storageName(StorageType type) {
        return switch (type) {
            case BLOCK -> lang.raw("storage.block.name");
            case PERSONAL -> lang.raw("storage.personal.name");
            case GLOBAL -> lang.raw("storage.global.name");
            case TEAM -> lang.raw("storage.team.name");
            case TEMPORARY -> lang.raw("storage.temporary.name");
        };
    }

    private String slotKey(SlotType type) {
        return switch (type) {
            case DESIGN -> "slot.design";
            case CONTAINER -> "slot.container";
            case CRAFT -> "slot.craft";
            case RESULT -> "slot.result";
            case FUEL -> "slot.fuel";
        };
    }

    private Inventory currentEditorInventory(Player player, EditorSession session, EditorHolder.Screen screen) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof EditorHolder holder
                && holder.screen() == screen
                && holder.gui() == session.gui()) {
            return top;
        }
        return null;
    }

    private boolean isPlaceholder(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName()
                && legacy(lang.raw("editor.design.empty")).equals(meta.displayName());
    }

    private ItemStack skeletonItem(SlotType type) {
        List<Component> hint = List.of(
                legacy(lang.raw("editor.skeleton.hint1")),
                legacy(lang.raw("editor.skeleton.hint2")));
        return loreItem(typeMaterial(type), lang.raw(slotKey(type)), hint);
    }

    private Material typeMaterial(SlotType type) {
        return switch (type) {
            case DESIGN -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case CONTAINER -> Material.GREEN_STAINED_GLASS_PANE;
            case CRAFT -> Material.ORANGE_STAINED_GLASS_PANE;
            case RESULT -> Material.BLACK_STAINED_GLASS_PANE;
            case FUEL -> Material.RED_STAINED_GLASS_PANE;
        };
    }

    private Inventory createScreen(EditorSession session, EditorHolder.Screen screen, int size, String titleKey) {
        Gui gui = session.gui();
        String title = lang.raw(titleKey).replace("%s", gui.name());
        EditorHolder holder = new EditorHolder(gui, screen);
        Inventory inv = Bukkit.createInventory(holder, size, legacy(title));
        holder.attach(inv);
        return inv;
    }

    private ItemStack infoItem(Gui gui) {
        List<Component> lore = new ArrayList<>();
        lore.add(legacy(lang.raw("editor.info.size").replace("%s", String.valueOf(gui.slots()))));
        lore.add(legacy(lang.raw("editor.info.storage").replace("%s", storageName(gui.storage()))));
        for (SlotType type : SlotType.values()) {
            long count = gui.skeleton().stream().filter(t -> t == type).count();
            if (count > 0) {
                lore.add(legacy(lang.raw("editor.info.type")
                        .replace("%s", lang.raw(slotKey(type)))
                        .replace("%d", String.valueOf(count))));
            }
        }
        lore.add(legacy(lang.raw("editor.info.commands").replace("%s", String.valueOf(gui.commands().size()))));
        lore.add(legacy(lang.raw("editor.info.blocks").replace("%s", String.valueOf(gui.blockIds().size()))));
        return loreItem(Material.BOOK, lang.raw("editor.info.name"), lore);
    }

    private ItemStack button(Material material, String key) {
        return item(material, lang.raw(key));
    }

    private ItemStack item(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(legacy(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack loreItem(Material material, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(legacy(name));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Component legacy(String text) {
        return SERIALIZER.deserialize(text == null ? "" : text);
    }
}
