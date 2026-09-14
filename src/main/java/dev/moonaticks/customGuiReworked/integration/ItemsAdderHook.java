package dev.moonaticks.customGuiReworked.integration;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Интеграция с ItemsAdder.
 *
 * <p><b>Важно:</b> класс ссылается на API ItemsAdder и должен
 * загружаться только при установленном плагине ItemsAdder
 * (см. {@link BlockHookManager}).
 */
public class ItemsAdderHook implements BlockPluginHook {

    private BlockHookDispatcher dispatcher;

    @Override
    public String pluginName() {
        return "ItemsAdder";
    }

    @Override
    public void attach(BlockHookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(CustomBlockInteractEvent event) {
        if (event.isCancelled() || dispatcher == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Block block = event.getBlockClicked();
        CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
        if (customBlock == null) {
            return;
        }
        if (dispatcher.onBlockInteract(player, block, customBlock.getNamespacedID())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(CustomBlockBreakEvent event) {
        if (dispatcher == null) {
            return;
        }
        Block block = event.getBlock();
        if (block == null) {
            return;
        }
        if (CustomBlock.byAlreadyPlaced(block) != null) {
            dispatcher.onBlockBroken(block);
        }
    }
}
