package dev.moonaticks.customGuiReworked.integration;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import dev.moonaticks.customGuiReworked.storage.BlockStorageBackend;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка входа для хуков кастомных блоков:
 * поиск GUI по ID блока, открытие интерфейса,
 * обработка разрушения блока (дроп предметов + закрытие интерфейсов).
 */
public class BlockHookDispatcher {

    private final CustomGuiReworked plugin;
    private final GuiRegistry registry;
    private final LanguageManager lang;
    /** Игроки с открытым блок-GUI: uuid → owner ключа («world:x,y,z»). */
    private final Map<UUID, String> openBlockKeys = new ConcurrentHashMap<>();

    public BlockHookDispatcher(CustomGuiReworked plugin, GuiRegistry registry, LanguageManager lang) {
        this.plugin = plugin;
        this.registry = registry;
        this.lang = lang;
    }

    /**
     * Пытается открыть GUI, привязанный к ID кастомного блока.
     *
     * @param player  игрок
     * @param block   блок
     * @param blockId ID кастомного блока (itemsadder:custom_block / craftengine:custom_block)
     * @return true, если действие перехвачено (событие нужно отменить)
     */
    public boolean onBlockInteract(Player player, Block block, String blockId) {
        Gui gui = registry.getByBlockId(blockId);
        if (gui == null) {
            return false;
        }
        Location location = block.getLocation();
        plugin.opener().openForPlayer(player, gui, location);
        player.swingHand(player.getActiveItemHand());
        openBlockKeys.put(player.getUniqueId(), StorageKey.forBlock(location, gui.fileName()).owner());
        return true;
    }

    /**
     * Разрушение кастомного блока: все сохранённые предметы блока
     * выпадают на месте, данные удаляются, открытые интерфейсы закрываются.
     */
    public void onBlockBroken(Block block) {
        Location location = block.getLocation();
        String owner = BlockStorageBackend.ownerKey(location);
        List<String> payloads = plugin.storage().blockBackend().allPayloads(location);
        for (String payload : payloads) {
            ItemStack item = Codecs.decode(payload);
            if (item != null && item.getType() != Material.AIR) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
        plugin.storage().blockBackend().removeBlock(location);
        plugin.storage().removeBlockCache(owner);

        List<UUID> toClose = new ArrayList<>();
        openBlockKeys.forEach((uuid, key) -> {
            if (key.equals(owner)) {
                toClose.add(uuid);
            }
        });
        for (UUID uuid : toClose) {
            openBlockKeys.remove(uuid, owner);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        plugin.getLogger().info("Custom block broken at " + owner + " — " + payloads.size() + " stored item(s) dropped");
    }

    /** Вызывается при закрытии инвентаря игрока. */
    public void onInventoryClosed(Player player, StorageKey key) {
        if (key == null || key.type() != StorageType.BLOCK) {
            return;
        }
        openBlockKeys.remove(player.getUniqueId(), key.owner());
    }

    /** Вызывается при выходе игрока. */
    public void onPlayerQuit(Player player) {
        openBlockKeys.remove(player.getUniqueId());
    }
}
