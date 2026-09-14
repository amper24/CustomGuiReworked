package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Обработка взаимодействия с редактором GUI.
 *
 * <p>В отличие от старого «TableEditorListener»: все клики отменяются по
 * умолчанию (нет случайного переноса), экраны не «мигают» (обновление
 * на месте), AIR-слоты не бросают исключение, чат-промпты отменяются
 * командой /cancel.
 */
public class EditorListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final CustomGuiReworked plugin;
    private final EditorManager editor;

    public EditorListener(CustomGuiReworked plugin, EditorManager editor) {
        this.plugin = plugin;
        this.editor = editor;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        EditorSession session = editor.session(player.getUniqueId());
        event.setCancelled(true);
        if (session == null || session.gui() != holder.gui()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            // Сдвиг из нижнего инвентаря может положить предмет в дизайн-слот
            if (holder.screen() == EditorHolder.Screen.DESIGN && event.isShiftClick()) {
                scheduleCapture(player, session);
            }
            return;
        }
        switch (holder.screen()) {
            case MAIN -> onMain(player, session, slot);
            case SIZE -> onSize(player, session, slot);
            case SKELETON -> onSkeleton(player, session, slot, event);
            case DESIGN -> onDesign(player, session, slot, event);
            case STORAGE -> onStorage(player, session, slot);
            case BLOCKS -> onBlocks(player, session, slot, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        EditorSession session = editor.session(player.getUniqueId());
        if (session == null || session.gui() != holder.gui()) {
            event.setCancelled(true);
            return;
        }
        Gui gui = session.gui();
        if (holder.screen() == EditorHolder.Screen.DESIGN) {
            for (int slot : event.getRawSlots()) {
                if (slot < 0 || slot >= top.getSize()) {
                    continue;
                }
                if (gui.slotType(slot) != SlotType.DESIGN) {
                    event.setCancelled(true);
                    return;
                }
            }
            scheduleCapture(player, session);
            return;
        }
        event.setCancelled(true);
    }

    /** При закрытии редактора сбрасываем активный чат-промпт (без «фантомного» перехвата чата). */
    @EventHandler
    public void onEditorClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder)) {
            return;
        }
        EditorSession session = editor.session(player.getUniqueId());
        if (session != null) {
            session.prompt(EditorSession.Prompt.NONE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        EditorSession session = editor.session(player.getUniqueId());
        if (session == null || session.prompt() == EditorSession.Prompt.NONE) {
            return;
        }
        event.setCancelled(true);
        EditorSession.Prompt prompt = session.prompt();
        session.prompt(EditorSession.Prompt.NONE);
        String message = event.getMessage().trim();
        if (message.isEmpty() || message.equalsIgnoreCase("/cancel")) {
            player.sendMessage(editor.lang().msg("editor.promptCancelled"));
            return;
        }
        // Чат приходит асинхронно — действия выполняем на основном потоке
        Bukkit.getScheduler().runTask(plugin, () -> {
            EditorSession current = editor.session(player.getUniqueId());
            if (current == null) {
                return;
            }
            switch (prompt) {
                case TITLE -> editor.setTitle(player, current, message);
                case BLOCK_ID -> editor.addBlockId(player, current, message);
                case NONE -> {
                }
            }
        });
    }

    // ================= экраны =================

    private void onMain(Player player, EditorSession session, int slot) {
        switch (slot) {
            case 10 -> editor.openScreen(player, session, EditorHolder.Screen.SIZE);
            case 11 -> editor.openScreen(player, session, EditorHolder.Screen.SKELETON);
            case 12 -> editor.openScreen(player, session, EditorHolder.Screen.DESIGN);
            case 13 -> {
                session.prompt(EditorSession.Prompt.TITLE);
                player.closeInventory();
                player.sendMessage(editor.lang().msg("editor.titlePrompt"));
            }
            case 14 -> editor.openScreen(player, session, EditorHolder.Screen.STORAGE);
            case 15 -> plugin.opener().openForPlayer(player, session.gui());
            case 16 -> editor.openScreen(player, session, EditorHolder.Screen.BLOCKS);
            case 22 -> player.closeInventory();
            case 25 -> {
                if (player.hasPermission("cgui.delete")) {
                    editor.deleteGui(player, session);
                } else {
                    player.sendMessage(editor.lang().msg("noPermission"));
                }
            }
            default -> {
            }
        }
    }

    private void onSize(Player player, EditorSession session, int slot) {
        int[] sizes = {9, 18, 27, 36, 45, 54};
        if (slot >= 10 && slot <= 15) {
            editor.setSlots(player, session, sizes[slot - 10]);
        } else if (slot == 22) {
            editor.openMain(player, session.gui());
        }
    }

    private void onSkeleton(Player player, EditorSession session, int slot, InventoryClickEvent event) {
        SlotType forced = event.isShiftClick() ? SlotType.DESIGN : null;
        editor.cycleSlotType(player, session, slot, forced);
    }

    private void onDesign(Player player, EditorSession session, int slot, InventoryClickEvent event) {
        Gui gui = session.gui();
        if (gui.slotType(slot) != SlotType.DESIGN) {
            player.sendMessage(editor.lang().msg("editor.design.lockedClick"));
            return;
        }
        event.setCancelled(false); // разрешаем работу с предметом
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            if (event.getClick() == ClickType.RIGHT) {
                // Пустая рука + правый клик — очистить дизайн-слот
                editor.clearDesignSlot(player, session, slot);
            }
            // Предмет могли забрать из слота — переснимаем дизайн
            scheduleCapture(player, session);
            return;
        }
        // Поставили/подержали предмет — переснимаем дизайн на следующем тике
        scheduleCapture(player, session);
    }

    private void onStorage(Player player, EditorSession session, int slot) {
        StorageType[] types = {StorageType.BLOCK, StorageType.PERSONAL, StorageType.GLOBAL, StorageType.TEAM, StorageType.TEMPORARY};
        if (slot >= 11 && slot <= 15) {
            editor.setStorage(player, session, types[slot - 11]);
        } else if (slot == 22) {
            editor.openMain(player, session.gui());
        }
    }

    private void onBlocks(Player player, EditorSession session, int slot, InventoryClickEvent event) {
        if (slot == 10) {
            session.prompt(EditorSession.Prompt.BLOCK_ID);
            player.closeInventory();
            player.sendMessage(editor.lang().msg("editor.blockPrompt"));
            return;
        }
        if (slot >= 11 && slot <= 16) {
            ItemStack item = event.getView().getTopInventory().getItem(slot);
            String id = plainName(item);
            if (id != null && !id.isEmpty()) {
                editor.removeBlockId(player, session, id);
            }
            return;
        }
        if (slot == 22) {
            editor.openMain(player, session.gui());
        }
    }

    private void scheduleCapture(Player player, EditorSession session) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && editor.session(player.getUniqueId()) == session) {
                editor.captureDesign(player, session);
            }
        }, 1L);
    }

    private String plainName(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        Component name = meta.displayName();
        return name == null ? null : PLAIN.serialize(name);
    }
}
