package dev.moonaticks.customGuiReworked.manager;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * События меню управления: клики по инвентарю и «живой» поиск в чате.
 */
public class ManagerListener implements Listener {

    private final CustomGuiReworked plugin;
    private final ManagerMenu manager;

    public ManagerListener(CustomGuiReworked plugin, ManagerMenu manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ManagerHolder holder)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getInventory();
        int slot = event.getRawSlot();

        if (holder.screen() == ManagerHolder.Screen.LIST) {
            String guiName = holder.guiAt(slot);
            if (guiName != null) {
                manager.onGuiClick(player, guiName, event.getClick());
                return;
            }
            switch (slot) {
                case 6 -> manager.onSearchClick(player);
                case 7 -> manager.onCreateClick(player);
                case 8 -> manager.onReloadClick(player);
                case 39 -> manager.onStatusClick(player);
                case 40 -> manager.onPaging(player, false);
                case 42 -> manager.onPaging(player, true);
                default -> {
                    // остальное — декор
                }
            }
            return;
        }

        // Экран опций
        String guiName = holder.optionGui();
        if (guiName != null) {
            manager.onOptionClick(player, guiName, slot);
        }
    }

    /**
     * Пока активен режим ввода (меню закрыто и ждёт текст) или открыто
     * само меню, чат = поиск/имя нового GUI.
     * Событие асинхронное — саму логику выполняем на основном потоке.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ManagerSession session = manager.session(player.getUniqueId());
        boolean awaitingInput = session != null
                && session.inputMode() != ManagerSession.InputMode.NONE;
        if (!awaitingInput && !manager.isOpen(player)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.onChat(player, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.closeSession(event.getPlayer().getUniqueId());
    }
}
