package dev.moonaticks.customGuiReworked.listeners;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.moonaticks.customGuiReworked.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerQuitEvent;
import dev.moonaticks.customGuiReworked.tools.OpenInterfaceData;
import dev.moonaticks.customGuiReworked.tools.TableGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakListener implements Listener {
    private final DatabaseManager databaseManager;
    private final TableGUI tableGUI;

    public BlockBreakListener(DatabaseManager databaseManager, TableGUI tableGUI) {
        this.databaseManager = databaseManager;
        this.tableGUI = tableGUI;
    }

    @EventHandler
    public void onBlockBreak(CustomBlockBreakEvent event) {
        Block block = event.getBlock();
        Location blockLoc = block.getLocation();

        CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
        if(customBlock != null) {
            databaseManager.removeBlockData(blockLoc);
            closeInterfacesForBlock(blockLoc);
            tableGUI.removeInventoryByLocation(blockLoc);
        }
    }
    public void closeInterfacesForBlock(Location blockLocation) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, OpenInterfaceData> entry : openInterfaces.entrySet()) {
            OpenInterfaceData data = entry.getValue();
            Location dataLoc = data.blockLocation();
            if (dataLoc != null &&
                    dataLoc.getWorld().equals(blockLocation.getWorld()) &&
                    dataLoc.getBlockX() == blockLocation.getBlockX() &&
                    dataLoc.getBlockY() == blockLocation.getBlockY() &&
                    dataLoc.getBlockZ() == blockLocation.getBlockZ()) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            openInterfaces.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeOpenInterface(event.getPlayer());
    }
    private final Map<UUID, OpenInterfaceData> openInterfaces = new ConcurrentHashMap<>();

    public void addOpenInterface(Player player, String tableName, Location blockLocation) {
        openInterfaces.put(player.getUniqueId(), new OpenInterfaceData(tableName, blockLocation));
    }
    public void removeOpenInterface(Player player) {
        openInterfaces.remove(player.getUniqueId());
    }
}