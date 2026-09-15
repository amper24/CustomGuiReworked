package dev.moonaticks.customGuiReworked.integration;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** Блоки, которые прямо сейчас уничтожаются (закрытие не должно их пересохранить). */
    private final Set<String> destroyed = ConcurrentHashMap.newKeySet();

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
        plugin.opener().openForPlayer(player, gui, block.getLocation());
        EquipmentSlot hand = player.getActiveItemHand();
        if (hand != null) {
            player.swingHand(hand);
        }
        // Трекинг зрителей выполняет GuiOpener (он знает фактический
        // тип хранилища — блок-GUI открывается даже с override).
        return true;
    }

    /** Регистрирует открытый блок-GUI (вызывается из GuiOpener). */
    public void onInventoryOpened(Player player, StorageKey key) {
        if (key != null && key.type() == dev.moonaticks.customGuiReworked.api.StorageType.BLOCK) {
            openBlockKeys.put(player.getUniqueId(), key.owner());
        }
    }

    /**
     * Разрушение кастомного блока:
     * <ol>
     *   <li>помечаем блок уничтожаемым и закрываем открытые на нём GUI
     *       (их обработчик закрытия НЕ пересохранит данные);</li>
     *   <li>выбрасываем сохранённые предметы;</li>
     *   <li>удаляем данные региона и кэш.</li>
     * </ol>
     */
    public void onBlockBroken(Block block) {
        Location location = block.getLocation();
        String owner = BlockStorageBackend.ownerKey(location);

        // 1. Фиксируем АКТУАЛЬНОЕ содержимое открытых инвентарей в кэше
        //    региона ДО маркера: предмет, забранный игроком на курсор
        //    за последнее окно коалесинга, уже не в слоте и не должен
        //    повторно выпасть дропом (иначе дюп).
        List<UUID> viewers = new ArrayList<>();
        openBlockKeys.forEach((uuid, key) -> {
            if (key.equals(owner)) {
                viewers.add(uuid);
            }
        });
        for (UUID uuid : viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            org.bukkit.inventory.Inventory top =
                    player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof dev.moonaticks.customGuiReworked.gui.GuiHolder holder
                    && owner.equals(holder.key().owner())) {
                // reconcile синхронно правит region-кэш (файл дозапишется
                // позже фоном, а удаление ниже всё равно перезапишет кэш).
                plugin.opener().reconcile(holder, null);
            }
        }

        // 2. Маркер + закрытие зрителей (close-событие синхронно и уже не
        //    пересохранит данные этого блока).
        destroyed.add(owner);
        for (UUID uuid : viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }

        // 3. Дроп предметов (из актуализированного кэша региона).
        List<String> payloads = plugin.storage().blockBackend().allPayloads(location);
        for (String payload : payloads) {
            ItemStack item = Codecs.decode(payload);
            if (item != null && item.getType() != Material.AIR) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }

        // 4. Удаление данных и кэша.
        plugin.storage().blockBackend().removeBlock(location);
        plugin.storage().removeBlockCache(owner);
        openBlockKeys.values().removeIf(owner::equals);
        destroyed.remove(owner);
        plugin.getLogger().info("Custom block broken at " + owner + " — " + payloads.size() + " stored item(s) dropped");
    }

    /** Вызывается при закрытии инвентаря игрока. */
    public void onInventoryClosed(Player player, StorageKey key) {
        if (key != null && key.type() == dev.moonaticks.customGuiReworked.api.StorageType.BLOCK) {
            openBlockKeys.remove(player.getUniqueId(), key.owner());
        }
    }

    /** true, если блок с данным owner сейчас уничтожается (закрытие не сохраняет данные). */
    public boolean isDestroyed(String owner) {
        return owner != null && destroyed.contains(owner);
    }

    /** Вызывается при выходе игрока. */
    public void onPlayerQuit(Player player) {
        openBlockKeys.remove(player.getUniqueId());
    }
}
