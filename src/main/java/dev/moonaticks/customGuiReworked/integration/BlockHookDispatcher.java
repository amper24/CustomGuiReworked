package dev.moonaticks.customGuiReworked.integration;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.animation.DesignAnimation;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockHandler;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка входа для хуков кастомных блоков:
 * поиск GUI по ID блока, открытие интерфейса,
 * обработка разрушения блока (дроп предметов + закрытие интерфейсов),
 * диспетчеризация функциональных блоков (canOpen/onOpen/onClick/onClose/
 * onTick/onBlockBroken — см. {@link FunctionalBlockHandler}).
 */
public class BlockHookDispatcher {

    /** Период onTick функциональных блоков (тики). */
    public static final int FUNCTIONAL_TICK_INTERVAL = 5;

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
     * <p>Если на ID зарегистрирован функциональный обработчик — сначала
     * спрашивается {@code canOpen}; при отказе клик «съедается».
     *
     * @param player  игрок
     * @param block   блок
     * @param blockId ID кастомного блока (itemsadder:custom_block / craftengine:custom_block)
     * @return true, если действие перехвачено (событие нужно отменить)
     */
    public boolean onBlockInteract(Player player, Block block, String blockId) {
        FunctionalBlockHandler handler = plugin.functionalBlocks().getHandler(blockId);
        if (handler != null) {
            Location location = block.getLocation();
            try {
                if (!handler.canOpen(player, location)) {
                    player.sendMessage(lang.msg("interface.blockDenied"));
                    return true; // действие перехвачено, GUI не открываем
                }
            } catch (Exception e) {
                plugin.getLogger().warning("FunctionalBlockHandler.canOpen('" + blockId
                        + "') failed: " + e.getMessage());
                return true; // не рискуем открывать при ошибке проверки
            }
            // GUI мог быть создан после регистрации обработчика —
            // дособыливаем привязку на лету.
            plugin.functionalBlocks().bindGui(blockId, handler.getGuiName());
        }

        Gui gui = registry.getByBlockId(blockId);
        if (gui == null) {
            return false;
        }
        plugin.opener().openForPlayer(player, gui, block.getLocation(), null, blockId);
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
        if (key != null && key.type() == StorageType.BLOCK) {
            openBlockKeys.put(player.getUniqueId(), key.owner());
        }
    }

    /**
     * Вызывается из GuiOpener после GuiOpenEvent и до {@code openInventory}:
     * колбэк {@code onOpen} функционального обработчика (тут удобно сетаить
     * локальный title/design — окно ещё не показано).
     *
     * @param blockId ID блока (может быть null, если GUI открыт программно —
     *                тогда обработчик ищется по имени GUI)
     */
    public void onFunctionalOpen(Player player, GuiHolder holder, Location block, String blockId) {
        FunctionalBlockHandler handler = handlerForSession(holder, blockId);
        if (handler == null) {
            return;
        }
        try {
            handler.onOpen(player, block, holder.getInventory());
        } catch (Exception e) {
            plugin.getLogger().warning("FunctionalBlockHandler.onOpen('" + handler.getGuiName()
                    + "') failed: " + e.getMessage());
        }
    }

    /**
     * Вызывается из слушателя кликов (ДО внешних слушателей
     * {@code GuiSlotClickEvent}): обработчик может пометить
     * {@code event.setInteractionCancelled(true)} — ванильный клик отменится.
     */
    public void onGuiClick(Player player, GuiHolder holder, int slot, SlotType type, GuiSlotClickEvent event) {
        if (holder.key().type() != StorageType.BLOCK) {
            return;
        }
        Location block = StorageKey.blockLocation(holder.key().owner());
        if (block == null) {
            return;
        }
        for (FunctionalBlockHandler handler : plugin.functionalBlocks().handlersForGui(holder.gui().name())) {
            try {
                handler.onClick(player, block, slot, type, event);
            } catch (Exception e) {
                plugin.getLogger().warning("FunctionalBlockHandler.onClick('" + handler.getGuiName()
                        + "') failed: " + e.getMessage());
            }
        }
    }

    /**
     * Вызывается из слушателя закрытия (после реконсиляции/сохранения):
     * колбэк {@code onClose} обработчика.
     */
    public void onGuiClosed(Player player, GuiHolder holder) {
        if (holder.key().type() != StorageType.BLOCK) {
            return;
        }
        // Локация из owner-ключа, а не из holder.blockLocation():
        // handleClose уже мог очистить пер-вьювер состояние сессии.
        Location block = StorageKey.blockLocation(holder.key().owner());
        if (block == null) {
            return;
        }
        for (FunctionalBlockHandler handler : plugin.functionalBlocks().handlersForGui(holder.gui().name())) {
            try {
                handler.onClose(player, block);
            } catch (Exception e) {
                plugin.getLogger().warning("FunctionalBlockHandler.onClose('" + handler.getGuiName()
                        + "') failed: " + e.getMessage());
            }
        }
    }

    /**
     * Тик функциональных блоков (вызывается плагином каждые
     * {@link #FUNCTIONAL_TICK_INTERVAL} тиков): {@code onTick} для всех
     * открытых сессий каждого обработчика.
     */
    public void tickFunctionalBlocks() {
        List<FunctionalBlockHandler> handlers = plugin.functionalBlocks().getHandlers();
        if (handlers.isEmpty()) {
            return;
        }
        // Открытые блок-сессии: owner ключа → holder'ы.
        Map<String, List<GuiHolder>> sessionsByOwner = new HashMap<>();
        for (Map.Entry<UUID, String> entry : openBlockKeys.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof GuiHolder holder && entry.getValue().equals(holder.key().owner())) {
                sessionsByOwner.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(holder);
            }
        }
        for (Map.Entry<String, List<GuiHolder>> entry : sessionsByOwner.entrySet()) {
            Location location = StorageKey.blockLocation(entry.getKey());
            if (location == null) {
                continue;
            }
            for (FunctionalBlockHandler handler : handlers) {
                for (GuiHolder holder : entry.getValue()) {
                    if (!handler.getGuiName().equalsIgnoreCase(holder.gui().name())) {
                        continue;
                    }
                    try {
                        handler.onTick(location, holder.getInventory());
                    } catch (Exception e) {
                        plugin.getLogger().warning("FunctionalBlockHandler.onTick('" + handler.getGuiName()
                                + "') failed: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Обработчик сессии: точный (по ID блока, если он известен),
     * иначе — первый, работающий с этим GUI.
     */
    private FunctionalBlockHandler handlerForSession(GuiHolder holder, String blockId) {
        FunctionalBlockRegistry registry = plugin.functionalBlocks();
        if (blockId != null) {
            FunctionalBlockHandler handler = registry.getHandler(blockId);
            if (handler != null) {
                return handler;
            }
        }
        List<FunctionalBlockHandler> byGui = registry.handlersForGui(holder.gui().name());
        return byGui.isEmpty() ? null : byGui.get(0);
    }

    /**
     * Разрушение кастомного блока:
     * <ol>
     *   <li>помечаем блок уничтожаемым и закрываем открытые на нём GUI
     *       (их обработчик закрытия НЕ пересохранит данные; локальные
     *       оверрайды зрителей очищаются в handleClose);</li>
     *   <li>выбрасываем сохранённые предметы;</li>
     *   <li>удаляем данные региона и кэш;</li>
     *   <li>уведомляем функциональные обработчики и анимации дизайна.</li>
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
        Set<String> sessionGuis = ConcurrentHashMap.newKeySet();
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
                sessionGuis.add(holder.gui().name());
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

        // 5. Функциональные обработчики (по GUI открытых сессий)
        //    и анимации дизайна (per-блок) для этой локации.
        notifyFunctionalBroken(location, sessionGuis);
        DesignAnimation.onBlockBroken(location);
    }

    private void notifyFunctionalBroken(Location location, Set<String> sessionGuis) {
        if (sessionGuis.isEmpty()) {
            return;
        }
        for (FunctionalBlockHandler handler : plugin.functionalBlocks().getHandlers()) {
            boolean relevant = false;
            for (String guiName : sessionGuis) {
                if (handler.getGuiName().equalsIgnoreCase(guiName)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant) {
                continue;
            }
            try {
                handler.onBlockBroken(location);
            } catch (Exception e) {
                plugin.getLogger().warning("FunctionalBlockHandler.onBlockBroken('" + handler.getGuiName()
                        + "') failed: " + e.getMessage());
            }
        }
    }

    /** Вызывается при закрытии инвентаря игрока. */
    public void onInventoryClosed(Player player, StorageKey key) {
        if (key != null && key.type() == StorageType.BLOCK) {
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
