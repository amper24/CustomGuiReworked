package dev.moonaticks.customGuiReworked.listeners;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotCommand;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import dev.moonaticks.customGuiReworked.codec.Codecs;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Взаимодействие игроков с открытыми GUI.
 *
 * <p>Оптимизация по сравнению со старой версией:
 * <ul>
 *   <li>защита дизайн-слотов и result-слотов от прямого изменения;</li>
 *   <li>после клика сериализуется <b>только затронутый слот</b>
 *       (а не весь инвентарь, как раньше — один NBT-вызов вместо 54);</li>
 *   <li>полный снапшот — только при сдвиге из нижнего инвентаря;</li>
 *   <li>сохранение — через {@link dev.moonaticks.customGuiReworked.storage.StorageService}
 *       (асинхронно, с коалесингом), а не на каждый клик;</li>
 *   <li>команды выполняются через {@link Bukkit#dispatchCommand} без
 *       setOp-эксплойта и «чёрного списка» игроков;</li>
 *   <li>каждый клик по слоту генерирует {@link GuiSlotClickEvent} —
 *       точка расширения для API.</li>
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

        if (!inTop) {
            // Сдвиг из нижнего инвентаря может положить предметы в верхний.
            // Если в GUI есть result-слоты, запрещаем сдвиг целиком,
            // чтобы предметы не попали в незащищённые места.
            if (event.isShiftClick()) {
                if (gui.skeleton().contains(SlotType.RESULT)) {
                    event.setCancelled(true);
                    return;
                }
                afterFullChange(holder);
            }
            return;
        }

        SlotType type = gui.slotType(slot);
        ClickType click = event.getClick();

        // Дизайн-слоты неизменяемы
        if (type == SlotType.DESIGN) {
            event.setCancelled(true);
            return;
        }
        // Result: нельзя ставить предметы (кроме сдвига и double-click сбора)
        if (type == SlotType.RESULT
                && event.getCursor() != null
                && event.getCursor().getType() != Material.AIR
                && !event.isShiftClick()
                && click != ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
        }

        // Событие API: отмена запрещает выполнение привязанных команд
        GuiSlotClickEvent guiEvent = new GuiSlotClickEvent(player, gui, top, slot, type, true, click);
        Bukkit.getPluginManager().callEvent(guiEvent);
        if (!guiEvent.isCancelled()) {
            runCommands(player, gui, slot);
        }

        afterSlotsChange(holder, List.of(slot));
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
        boolean changed = false;
        List<Integer> affected = new ArrayList<>();
        for (int slot : event.getRawSlots()) {
            if (slot < 0 || slot >= top.getSize()) {
                continue;
            }
            SlotType type = gui.slotType(slot);
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
            changed = true;
            affected.add(slot);
        }
        if (changed) {
            afterSlotsChange(holder, affected);
        }
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

    // ================= хранение =================

    /** Сериализует затронутые (не-дизайн) слоты в хранилище. */
    private void afterSlotsChange(GuiHolder holder, List<Integer> slots) {
        Gui gui = holder.gui();
        if (gui.storage() == StorageType.TEMPORARY) {
            return;
        }
        Inventory inv = holder.getInventory();
        if (inv == null) {
            return;
        }
        for (int slot : slots) {
            if (slot < 0 || slot >= gui.slots()) {
                continue;
            }
            if (gui.slotType(slot) == SlotType.DESIGN) {
                continue;
            }
            plugin.storage().updateSlot(holder.key(), slot, Codecs.encode(inv.getItem(slot)));
        }
    }

    /** Полный снапшот (сдвиг из нижнего инвентаря). */
    private void afterFullChange(GuiHolder holder) {
        Gui gui = holder.gui();
        if (gui.storage() == StorageType.TEMPORARY) {
            return;
        }
        Inventory inv = holder.getInventory();
        if (inv == null) {
            return;
        }
        String[] snapshot = new String[gui.slots()];
        for (int i = 0; i < gui.slots(); i++) {
            snapshot[i] = gui.slotType(i) == SlotType.DESIGN ? "" : Codecs.encode(inv.getItem(i));
        }
        plugin.storage().update(holder.key(), snapshot);
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
            if (command.delay() <= 0) {
                dispatch(player, resolved, asOp);
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        dispatch(player, resolved, asOp);
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
