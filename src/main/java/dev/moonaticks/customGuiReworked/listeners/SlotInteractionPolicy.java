package dev.moonaticks.customGuiReworked.listeners;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Проверки всех ванильных способов положить/забрать предмет в кастомный слот. */
final class SlotInteractionPolicy {

    private SlotInteractionPolicy() {
    }

    static boolean allowed(Player player, Gui gui, Inventory top, int slot, InventoryClickEvent event) {
        SlotType type = gui.slotType(slot);
        ItemStack current = event.getCurrentItem();
        InventoryAction action = event.getAction();
        if (action == null || action == InventoryAction.UNKNOWN) {
            return false; // неизвестное действие нельзя пропускать через ограничения
        }
        return switch (action) {
            case NOTHING, DROP_ALL_CURSOR, DROP_ONE_CURSOR -> true;
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                    DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY, CLONE_STACK ->
                    empty(current) || type.canTake(gui, top, slot, player, current);
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> {
                ItemStack cursor = event.getCursor();
                yield empty(cursor) || type.canInsert(gui, top, slot, player, cursor);
            }
            case SWAP_WITH_CURSOR -> swapAllowed(type, gui, top, slot, player, current, event.getCursor());
            // Bukkit может положить вытесненный хотбар-предмет в ЛЮБОЙ
            // свободный слот сверху, минуя правила конкретного типа.
            case HOTBAR_MOVE_AND_READD -> false;
            case HOTBAR_SWAP -> {
                ItemStack incoming = event.getClick() == ClickType.SWAP_OFFHAND
                        ? player.getInventory().getItemInOffHand()
                        : event.getHotbarButton() >= 0 && event.getHotbarButton() < 9
                        ? player.getInventory().getItem(event.getHotbarButton()) : null;
                yield swapAllowed(type, gui, top, slot, player, current, incoming);
            }
            case COLLECT_TO_CURSOR -> canCollect(player, gui, top, event.getCursor());
            default -> false;
        };
    }

    /** Double-click может забрать предмет из ДРУГОГО слота, даже при клике в инвентаре игрока. */
    static boolean canCollect(Player player, Gui gui, Inventory top, ItemStack cursor) {
        if (empty(cursor)) {
            return true;
        }
        for (int i = 0; i < gui.slots(); i++) {
            ItemStack item = top.getItem(i);
            if (!empty(item) && item.isSimilar(cursor)
                    && !gui.slotType(i).canTake(gui, top, i, player, item)) {
                return false;
            }
        }
        return true;
    }

    private static boolean swapAllowed(SlotType type, Gui gui, Inventory top, int slot,
                                       Player player, ItemStack current, ItemStack incoming) {
        return (empty(current) || type.canTake(gui, top, slot, player, current))
                && (empty(incoming) || type.canInsert(gui, top, slot, player, incoming));
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
