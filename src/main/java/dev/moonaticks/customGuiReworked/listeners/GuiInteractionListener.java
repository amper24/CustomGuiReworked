package dev.moonaticks.customGuiReworked.listeners;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotCommand;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.event.GuiDragEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Взаимодействие игроков с открытыми GUI.
 *
 * <p>Гарантии:
 * <ul>
 *   <li>дизайн-слоты неизменяемы, но клики по ним (кнопки) запускают
 *       привязанные команды и генерируют {@link GuiSlotClickEvent};</li>
 *   <li>result-слоты — только «на выход»: любые попытки положить туда
 *       предмет (курсор, цифровые клавиши, свап с офхендом, драг,
 *       сдвиг из нижнего инвентаря) заблокированы;</li>
 *   <li>изменения читаются со снапшота <b>следующего тика</b> (внутри
 *       InventoryClickEvent инвентарь ещё содержит старые предметы),
 *       дифом по baseline — в хранилище пишутся только изменившиеся
 *       слоты, не затирая параллельные сессии других игроков;</li>
 *   <li>команды выполняются через {@link Bukkit#dispatchCommand} без
 *       setOp-эксплойта;</li>
 *   <li>каждый клик по верхнему инвентарю и каждый драг генерируют
 *       события API — точки расширения для других плагинов.</li>
 * </ul>
 */
public class GuiInteractionListener implements Listener {

    private final CustomGuiReworked plugin;

    public GuiInteractionListener(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Gui gui = holder.gui();
        Inventory clicked = event.getClickedInventory();
        boolean inTop = clicked != null && clicked == top;
        int slot = event.getSlot();
        ClickType click = event.getClick();

        if (!inTop) {
            // Клик по своему инвентарю. Верхний инвентарь меняют только
            // shift-перенос и double-click (сбор стака на курсор).
            if (event.isShiftClick()) {
                // Предмет может распределиться в result-слот — запрещаем,
                // если result-слоты вообще есть в GUI.
                if (gui.skeleton().contains(SlotType.RESULT)) {
                    event.setCancelled(true);
                    return;
                }
                holder.addAllCandidates();
                plugin.opener().scheduleReconcile(holder);
            } else if (click == ClickType.DOUBLE_CLICK) {
                // Double-click собирает совпадающие стаки из ВСЕГО верхнего
                // инвентаря, включая дизайн-предметы (кража оформления) —
                // запрещаем при любых дизайн-слотах, иначе реконсилируем всё.
                if (gui.skeleton().contains(SlotType.DESIGN)) {
                    event.setCancelled(true);
                    return;
                }
                holder.addAllCandidates();
                plugin.opener().scheduleReconcile(holder);
            }
            return;
        }

        // Double-click по верхнему инвентарю тоже может собрать дизайн —
        // блокируем его при наличии дизайн-слотов.
        if (click == ClickType.DOUBLE_CLICK && gui.skeleton().contains(SlotType.DESIGN)) {
            event.setCancelled(true);
            return;
        }

        SlotType type = gui.slotType(slot);

        // Дизайн-слоты неизменяемы, но клик по кнопке работает.
        if (type == SlotType.DESIGN) {
            event.setCancelled(true);
            boolean runCommands = fireClickEvent(player, gui, top, slot, type, click, event, true);
            if (runCommands) {
                runCommands(player, gui, slot);
            }
            return;
        }

        // Result: кладка предмета запрещена всеми способами.
        boolean placementBlocked = false;
        if (type == SlotType.RESULT) {
            placementBlocked = isPlacementIntoResult(player, event);
            if (placementBlocked) {
                event.setCancelled(true);
            }
        }

        boolean runCommands = fireClickEvent(player, gui, top, slot, type, click, event, !placementBlocked);

        // Кандидаты на запись: одиночный клик затрагивает один слот,
        // shift/double могут перераспределить предметы по всему GUI.
        if (event.isShiftClick() || click == ClickType.DOUBLE_CLICK) {
            holder.addAllCandidates();
        } else {
            holder.addCandidate(slot);
        }
        plugin.opener().scheduleReconcile(holder);

        // Команды result-слота при заблокированной кладке не запускаем
        // (это «ошибочный» клик, а не настоящее нажатие кнопки).
        if (runCommands && !placementBlocked) {
            runCommands(player, gui, slot);
        }
    }

    /** true, если клик пытается положить предмет в result-слот. */
    private boolean isPlacementIntoResult(Player player, InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (event.isShiftClick() || click == ClickType.DOUBLE_CLICK) {
            return false; // take-only операции
        }
        if (click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && hotbar < 9) {
                ItemStack hot = player.getInventory().getItem(hotbar);
                return hot != null && hot.getType() != Material.AIR;
            }
            return false;
        }
        if (click == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            return offhand != null && offhand.getType() != Material.AIR;
        }
        // LEFT/RIGHT/MIDDLE: прямое размещение с курсора
        ItemStack cursor = event.getCursor();
        return cursor != null && cursor.getType() != Material.AIR;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Gui gui = holder.gui();
        List<Integer> affected = new ArrayList<>();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                continue;
            }
            SlotType type = gui.slotType(rawSlot);
            if (type == SlotType.DESIGN) {
                event.setCancelled(true);
                return;
            }
            if (type == SlotType.RESULT
                    && event.getOldCursor() != null
                    && event.getOldCursor().getType() != Material.AIR) {
                event.setCancelled(true);
                return;
            }
            affected.add(rawSlot);
        }
        if (affected.isEmpty()) {
            return;
        }
        GuiDragEvent dragEvent = new GuiDragEvent(player, gui, top, affected, event);
        Bukkit.getPluginManager().callEvent(dragEvent);
        if (dragEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        for (int slot : affected) {
            holder.addCandidate(slot);
        }
        plugin.opener().scheduleReconcile(holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        plugin.opener().handleClose(player, holder);
        plugin.dispatcher().onInventoryClosed(player, holder.key());
    }

    // ================= событие API =================

    /**
     * Вызывает {@link GuiSlotClickEvent}.
     *
     * @param commandsEnabled разрешены ли привязанные команды при незапамятном
     *                        состоянии события (false — заблокированная кладка)
     * @return true, если команды нужно выполнить
     */
    private boolean fireClickEvent(Player player, Gui gui, Inventory top, int slot, SlotType type,
                                   ClickType click, InventoryClickEvent handle, boolean commandsEnabled) {
        GuiSlotClickEvent guiEvent = new GuiSlotClickEvent(player, gui, top, slot, type, true, click, handle);
        Bukkit.getPluginManager().callEvent(guiEvent);
        if (guiEvent.isInteractionCancelled()) {
            handle.setCancelled(true);
        }
        return commandsEnabled && !guiEvent.isCancelled();
    }

    // ================= команды =================

    private void runCommands(Player player, Gui gui, int slot) {
        boolean asOp = plugin.getConfig().getBoolean("commands.execute-as-op", false);
        for (SlotCommand command : gui.commandsForSlot(slot)) {
            String resolved = command.command()
                    .replace("%player%", player.getName())
                    .replace("%slot%", String.valueOf(slot));
            if (resolved.startsWith("/")) {
                resolved = resolved.substring(1);
            }
            if (resolved.isBlank()) {
                continue;
            }
            final String commandLine = resolved;
            if (command.delay() <= 0) {
                dispatch(player, commandLine, asOp);
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        dispatch(player, commandLine, asOp);
                    }
                }.runTaskLater(plugin, command.delay());
            }
        }
    }

    private void dispatch(Player player, String command, boolean asOp) {
        if (!player.isOnline()) {
            return;
        }
        boolean wasOp = player.isOp();
        try {
            if (asOp && !wasOp) {
                player.setOp(true);
            }
            Bukkit.dispatchCommand(player, command);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute bound command '" + command
                    + "' for " + player.getName() + ": " + e.getMessage());
        } finally {
            if (asOp && !wasOp) {
                player.setOp(false);
            }
        }
    }
}
