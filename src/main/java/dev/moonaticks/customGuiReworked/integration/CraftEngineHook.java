package dev.moonaticks.customGuiReworked.integration;

import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Интеграция с CraftEngine.
 *
 * <p>GUI открывается по правому клику по кастомному блоку.
 *
 * <p><b>Важно:</b> класс ссылается на API CraftEngine и должен
 * загружаться только при установленном плагине CraftEngine
 * (см. {@link BlockHookManager}).
 */
public class CraftEngineHook implements BlockPluginHook {

    private BlockHookDispatcher dispatcher;

    @Override
    public String pluginName() {
        return "CraftEngine";
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
        if (event.action() != CustomBlockInteractEvent.Action.RIGHT_CLICK) {
            return;
        }
        Player player = event.player();
        if (player == null) {
            return;
        }
        Block block = event.bukkitBlock();
        if (block == null) {
            return;
        }
        if (dispatcher.onBlockInteract(player, block, blockId(event.customBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(CustomBlockBreakEvent event) {
        if (dispatcher == null || event.isCancelled()) {
            return;
        }
        Block block = event.bukkitBlock();
        if (block != null) {
            dispatcher.onBlockBroken(block);
        }
    }

    private static String blockId(BlockDefinition definition) {
        if (definition == null) {
            return null;
        }
        Key id = definition.id();
        if (id == null) {
            return null;
        }
        return id.namespace() + ":" + id.value();
    }
}
