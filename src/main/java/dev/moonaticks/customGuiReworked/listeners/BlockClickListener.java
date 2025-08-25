package dev.moonaticks.customGuiReworked.listeners;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;
import dev.moonaticks.customGuiReworked.tools.TableGUI;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import dev.moonaticks.customGuiReworked.tools.Gui;

public class BlockClickListener implements Listener {
    CustomGuiReworked plugin;
    TableGUI tableGUI;
    TableEditorManager manager;
    BlockBreakListener blockBreakListener;

    public BlockClickListener(TableGUI tableGUI, CustomGuiReworked plugin, TableEditorManager tableEditorManager, BlockBreakListener blockBreakListener) {
        this.plugin = plugin;
        this.tableGUI = tableGUI;
        this.manager = tableEditorManager;
        this.blockBreakListener = blockBreakListener;
    }
    @EventHandler (priority = EventPriority.LOWEST)
    public void onPlayerInteract(CustomBlockInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
        if(customBlock != null) {
            Location blockLocation = block.getLocation();
            Gui gui = manager.blocksToGui.get(customBlock.getNamespacedID());
            if (gui == null) return;
            player.swingHand(player.getActiveItemHand());
            tableGUI.getCustomInventory(player, gui, blockLocation);
            blockBreakListener.addOpenInterface(player, gui.getFile(), blockLocation);
            event.setCancelled(true);
        }
    }
}
