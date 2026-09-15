package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import io.papermc.paper.event.player.AsyncChatEvent;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Обработка взаимодействия с редактором GUI.
 *
 * <p>В отличие от старого «TableEditorListener»: клики по служебным панелям
 * отменяются по умолчанию (нет случайного переноса), экраны не «мигают»
 * (обновление на месте), AIR-слоты не бросают исключение, чат-промпты
 * отменяются командой /cancel.
 *
 * <p>Исключение — экран DESIGN: предметы дизайна берут из своего инвентаря
 * (клик, shift-клик, драг), поэтому работа с инвентарём игрока там разрешена,
 * а панели-заглушки на время переноса снимаются.
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
            onPlayerInventoryClick(player, session, holder, event, top);
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
            // Драг не отменяем: раскладку делает ваниль. Но служебные панели
            // игроку не принадлежат и мешают переносу:
            //  • «забор» предметов драгом утащил бы панели в курсор;
            //  • панель «Пусто» делает пустой дизайн-слот «занятым» —
            //    ваниль в такой слот ничего не положит.
            // Поэтому панели снимаем (captureDesign вернёт их на место).
            ItemStack cursor = event.getOldCursor();
            boolean taking = cursor == null || cursor.getType() == Material.AIR;
            for (int slot : event.getRawSlots()) {
                if (slot < 0 || slot >= top.getSize()) {
                    continue; // инвентарь игрока — обычный драг
                }
                if (taking || gui.slotType(slot) == SlotType.DESIGN) {
                    editor.releasePane(top, slot);
                }
            }
            scheduleCapture(player, session);
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Клик по инвентарю самого игрока.
     *
     * <p>Панели редактора служебные, поэтому «случайные» переносы по
     * умолчанию запрещены. Исключение — экран DESIGN, где предметы дизайна
     * как раз берут из своего инвентаря:
     * <ul>
     *   <li>обычный клик (взять предмет на курсор, вернуть обратно, цифровая
     *       клавиша, свап с офхендом) разрешён — взятый на курсор предмет
     *       затем кладётся в дизайн-слот обычным кликом;</li>
     *   <li>shift-клик переносит предмет в дизайн-слоты: панели «Пусто»
     *       снимаются (для ванили такой слот «занят», и перенос не сработал
     *       бы), после чего клик отдаётся ванили; панели скелета остаются
     *       на месте и закрывают служебные слоты от переноса;</li>
     *   <li>double-click запрещён: он собрал бы на курсор предметы из
     *       самого дизайна.</li>
     * </ul>
     */
    private void onPlayerInventoryClick(Player player, EditorSession session, EditorHolder holder,
                                        InventoryClickEvent event, Inventory top) {
        if (holder.screen() != EditorHolder.Screen.DESIGN) {
            return; // клик остаётся отменённым: панели редактора предметы не принимают
        }
        if (event.getRawSlot() < 0) {
            // Клик вне окна (сброс предмета с курсора) — обычное поведение.
            event.setCancelled(false);
            return;
        }
        if (event.isShiftClick()) {
            if (editor.prepareDesignTransfer(player, session, top, event.getCurrentItem())) {
                // Раскладку делает сама ваниль: освобождённые дизайн-слоты
                // она заполнит предметом, а панели скелета заняты и защищают
                // служебные слоты от переноса. Если места нет — клик так и
                // остаётся отменённым (игрок получил сообщение).
                event.setCancelled(false);
                scheduleCapture(player, session);
            }
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            return; // клик остаётся отменённым
        }
        event.setCancelled(false); // со своим инвентарём работаем как обычно
    }

    /** При закрытии редактора сбрасываем активный чат-промпт (без «фантомного» перехвата чата). */
    @EventHandler
    public void onEditorClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        EditorSession session = editor.session(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.prompt(EditorSession.Prompt.NONE);
        // Дизайн снимаем синхронно, пока инвентарь ещё открыт: отложенный
        // (на 1 тик) захват не успел бы, если игрок закрывает редактор сразу
        // после клика, а при выключении плагина не выполнился бы вовсе.
        if (holder.screen() == EditorHolder.Screen.DESIGN) {
            editor.captureDesign(player, session, top);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        EditorSession session = editor.session(player.getUniqueId());
        if (session == null || session.prompt() == EditorSession.Prompt.NONE) {
            return;
        }
        event.setCancelled(true);
        EditorSession.Prompt prompt = session.prompt();
        session.prompt(EditorSession.Prompt.NONE);
        String message = PLAIN.serialize(event.message()).trim();
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
                // Сначала закрываем инвентарь (его close-событие
                // сбрасывает промпт), и только потом включаем промпт.
                player.closeInventory();
                session.prompt(EditorSession.Prompt.TITLE);
                editor.schedulePromptTimeout(player, session);
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
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            // Double-click собрал бы на курсор предметы из всего окна,
            // включая сам дизайн, — не даём унести оформление случайно.
            return;
        }
        Inventory top = event.getView().getTopInventory();
        ItemStack current = top.getItem(slot);
        // Placeholder «пустого слота» забирать нельзя, но поверх него
        // можно ПОЛОЖИТЬ предмет (курсор/цифровая клавиша/офхенд) —
        // именно так пустой дизайн-слот заполняется предметом.
        if (editor.isPlaceholderItem(current)) {
            ClickType clickType = event.getClick();
            boolean wantsPlace;
            if (clickType == ClickType.NUMBER_KEY) {
                int btn = event.getHotbarButton();
                ItemStack hot = btn >= 0 && btn < 9 ? player.getInventory().getItem(btn) : null;
                wantsPlace = hot != null && hot.getType() != Material.AIR;
            } else if (clickType == ClickType.SWAP_OFFHAND) {
                ItemStack offhand = player.getInventory().getItemInOffHand();
                wantsPlace = offhand != null && offhand.getType() != Material.AIR;
            } else {
                ItemStack cursorOnPlaceholder = event.getCursor();
                wantsPlace = cursorOnPlaceholder != null && cursorOnPlaceholder.getType() != Material.AIR;
            }
            if (wantsPlace) {
                // Панель «Пусто» убираем сами: иначе ваниль не положит
                // предмет в «занятый» слот, а обменяет их (панель уехала бы
                // в хотбар). Свежую панель вернёт captureDesign, если слот
                // в итоге остался пустым.
                editor.releasePane(top, slot);
                event.setCancelled(false);
                scheduleCapture(player, session);
            } else if (clickType == ClickType.RIGHT) {
                editor.clearDesignSlot(player, session, slot);
            }
            return;
        }
        event.setCancelled(false); // разрешаем работу с реальным предметом
        ItemStack cursor = event.getCursor();
        // (cursor объявлен здесь, а не выше: ветка placeholder использует event.getCursor())
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
            player.closeInventory();
            session.prompt(EditorSession.Prompt.BLOCK_ID);
            editor.schedulePromptTimeout(player, session);
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
