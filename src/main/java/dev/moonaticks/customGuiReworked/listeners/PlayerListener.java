package dev.moonaticks.customGuiReworked.listeners;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Обработка выхода игроков: чистка сессий редактора и
 * сохранение личных данных.
 */
public class PlayerListener implements Listener {

    private final CustomGuiReworked plugin;

    public PlayerListener(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.editor().removeSession(player.getUniqueId());
        plugin.dispatcher().onPlayerQuit(player);
        plugin.storage().saveAllForPlayer(player);
    }
}
