package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.event.GuiCloseEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiOpenEvent;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import dev.moonaticks.customGuiReworked.util.ItemDrops;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Открытие и закрытие GUI: сборка инвентаря по скелету/дизайну/данным,
 * события API, дифная реконсиляция слотов при закрытии.
 *
 * <p>Чтение диска при открытии выполняется асинхронно ({@link StorageService#preload}),
 * сам инвентарь собирается и открывается строго на основном потоке.
 */
public class GuiOpener {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final CustomGuiReworked plugin;
    private final GuiRegistry registry;
    private final StorageService storage;
    private final LanguageManager lang;
    private final ItemDrops drops = new ItemDrops();

    /** GUI, открытые прямо сейчас (игрок → GUI). */
    private final Map<UUID, Gui> openGuis = new ConcurrentHashMap<>();
    /** Монотонный номер запроса на открытие (защита от устаревших async-preload). */
    private final Map<UUID, Long> openRequests = new ConcurrentHashMap<>();

    /** GUI, который игрок открыл сейчас, или null. */
    public Gui guiOf(UUID playerId) {
        return playerId == null ? null : openGuis.get(playerId);
    }

    public GuiOpener(CustomGuiReworked plugin, GuiRegistry registry, StorageService storage, LanguageManager lang) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.lang = lang;
    }

    /**
     * Открывает GUI. Для {@link StorageType#BLOCK} без локации — отказ.
     */
    public void openForPlayer(Player player, Gui gui) {
        openForPlayer(player, gui, null);
    }

    /**
     * Открывает GUI.
     *
     * @param player        игрок
     * @param gui           GUI
     * @param blockLocation локация блока (обязательна для BLOCK-хранилища, игнорируется иначе)
     */
    public void openForPlayer(Player player, Gui gui, Location blockLocation) {
        openForPlayer(player, gui, blockLocation, null);
    }

    /**
     * Открывает GUI.
     *
     * @param player          игрок
     * @param gui             GUI
     * @param blockLocation   локация блока (обязательна для BLOCK-хранилища, игнорируется иначе)
     * @param storageOverride временный тип хранилища (null — тип самого GUI)
     */
    public void openForPlayer(Player player, Gui gui, Location blockLocation, StorageType storageOverride) {
        if (player == null || gui == null) {
            return;
        }
        if (gui.skeleton().isEmpty() || gui.skeleton().size() != gui.slots()) {
            player.sendMessage(lang.msg("interface.broken.skeleton"));
            return;
        }

        StorageType type = storageOverride != null ? storageOverride : gui.storage();
        StorageKey key = buildKey(player, gui, blockLocation, type);
        if (key == null) {
            return; // сообщение уже отправлено
        }

        long requestId = openRequests.merge(player.getUniqueId(), 1L, Long::sum);
        // Диск читается на I/O-потоке, сборка/открытие — на основном.
        storage.preload(key, () -> {
            if (!player.isOnline()) {
                openRequests.remove(player.getUniqueId(), requestId);
                return;
            }
            // Устаревший запрос (игрок успел запросить другой GUI) — игнорируем
            if (!openRequests.getOrDefault(player.getUniqueId(), -1L).equals(requestId)) {
                return;
            }
            finishOpen(player, gui, key);
        });
    }

    private StorageKey buildKey(Player player, Gui gui, Location blockLocation, StorageType type) {
        return switch (type) {
            case BLOCK -> {
                if (blockLocation == null || blockLocation.getWorld() == null) {
                    player.sendMessage(lang.msg("interface.blockNoLocation"));
                    yield null;
                }
                yield StorageKey.forBlock(blockLocation, gui.fileName());
            }
            case PERSONAL -> StorageKey.forPlayer(player, gui.fileName());
            case TEAM -> StorageKey.forTeam(teamOf(player), gui.fileName());
            case GLOBAL -> StorageKey.global(gui.fileName());
            case TEMPORARY -> StorageKey.temporary(player.getUniqueId(), gui.fileName());
        };
    }

    private void finishOpen(Player player, Gui gui, StorageKey key) {
        String[] stored = key.type() == StorageType.TEMPORARY ? null : storage.load(key);

        GuiHolder holder = new GuiHolder(gui, key);
        Inventory inventory = Bukkit.createInventory(holder, gui.slots(), SERIALIZER.deserialize(gui.title()));
        holder.attach(inventory);
        applyDesign(inventory, gui);
        if (stored != null) {
            applyStorage(inventory, gui, stored);
        }
        holder.initBaseline(snapshotPersistable(inventory, gui));

        GuiOpenEvent event = new GuiOpenEvent(player, gui, inventory, key);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            openRequests.remove(player.getUniqueId());
            return;
        }
        openRequests.remove(player.getUniqueId());
        openGuis.put(player.getUniqueId(), gui);
        if (key.type() == StorageType.BLOCK) {
            plugin.dispatcher().onInventoryOpened(player, key);
        }
        player.openInventory(inventory);
    }

    private void applyDesign(Inventory inventory, Gui gui) {
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) != SlotType.DESIGN) {
                continue;
            }
            org.bukkit.inventory.ItemStack item = Codecs.decode(gui.designAt(i));
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(i, item);
            }
        }
    }

    private void applyStorage(Inventory inventory, Gui gui, String[] stored) {
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) == SlotType.DESIGN) {
                continue;
            }
            String payload = i < stored.length ? stored[i] : null;
            if (payload == null || payload.isBlank()) {
                continue;
            }
            org.bukkit.inventory.ItemStack item = Codecs.decode(payload);
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(i, item);
            }
        }
    }

    /** Кодирует персистентные слоты; не-персистентные позиции = null. */
    private String[] snapshotPersistable(Inventory inventory, Gui gui) {
        String[] snapshot = new String[gui.slots()];
        for (int i = 0; i < gui.slots(); i++) {
            if (GuiHolder.isPersistable(gui.slotType(i))) {
                snapshot[i] = Codecs.encode(inventory.getItem(i));
            }
        }
        return snapshot;
    }

    /**
     * Возвращает игрокам предметы, которые ванильная механика могла
     * занести в дизайн-слоты (shift-перенос из нижнего инвентаря
     * раскладывает предметы по любым слотам, в т.ч. декоративным).
     * Если стаак «слился» с декоративным предметом — возвращается
     * только дельта, сам дизайн восстанавливается как был.
     */
    private void rescueDesignItems(GuiHolder holder) {
        Gui gui = holder.gui();
        Inventory inv = holder.getInventory();
        if (inv == null) {
            return;
        }
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) != SlotType.DESIGN) {
                continue;
            }
            ItemStack current = inv.getItem(i);
            ItemStack expected = Codecs.decode(gui.designAt(i));
            if (current == null || current.getType() == Material.AIR) {
                continue;
            }
            if (expected != null && expected.getType() != Material.AIR && current.isSimilar(expected)) {
                int delta = current.getAmount() - expected.getAmount();
                inv.setItem(i, expected.clone());
                if (delta > 0) {
                    giveBack(holder, new ItemStack(current.getType(), delta));
                }
            } else {
                ItemStack moved = current.clone();
                inv.setItem(i, expected == null ? null : expected.clone());
                giveBack(holder, moved);
            }
        }
    }

    private void giveBack(GuiHolder holder, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        for (org.bukkit.entity.HumanEntity human : holder.getInventory().getViewers()) {
            if (!(human instanceof org.bukkit.entity.Player player)) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                if (rest != null && rest.getType() != Material.AIR && rest.getAmount() > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
            return; // предмет возвращается одному зрителю
        }
    }

    /**
     * Планирует дифную реконсиляцию на следующий тик: к этому моменту
     * Bukkit уже применил изменения клика к инвентарю (внутри
     * InventoryClickEvent/DragEvent содержимое ещё старое — читать
     * его синхронно нельзя).
     */
    public void scheduleReconcile(GuiHolder holder) {
        synchronized (holder) {
            if (holder.isReconcileQueued()) {
                return;
            }
            holder.setReconcileQueued(true);
        }
        holder.reconcileTask(new BukkitRunnable() {
            @Override
            public void run() {
                holder.reconcileTask(null);
                holder.setReconcileQueued(false);
                reconcile(holder, holder.drainCandidates());
            }
        }.runTask(plugin));
    }

    /**
     * Сравнивает текущее содержимое персистентных слотов с baseline
     * и пишет в хранилище только изменившиеся слоты.
     *
     * @param candidates null — проверить все персистентные слоты;
     *                   иначе только перечисленные
     */
    public void reconcile(GuiHolder holder, Collection<Integer> candidates) {
        Gui gui = holder.gui();
        rescueDesignItems(holder);
        if (holder.key().type() == StorageType.TEMPORARY) {
            return;
        }
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        String[] baseline = holder.baseline();
        if (baseline == null) {
            return;
        }
        for (int i = 0; i < gui.slots(); i++) {
            if (!GuiHolder.isPersistable(gui.slotType(i))) {
                continue;
            }
            if (candidates != null && !candidates.contains(i)) {
                continue;
            }
            String encoded = Codecs.encode(inventory.getItem(i));
            String before = i < baseline.length ? baseline[i] : null;
            if (!java.util.Objects.equals(encoded, before == null ? "" : before)) {
                storage.updateSlot(holder.key(), i, encoded);
                baseline[i] = encoded;
            }
        }
    }

    /**
     * Обработка закрытия GUI: для TEMPORARY возвращает предметы,
     * для остальных типов делает финальную реконсиляцию и немедленную запись.
     */
    public void handleClose(Player player, GuiHolder holder) {
        Gui gui = holder.gui();
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        BukkitTask task = holder.reconcileTask();
        if (task != null) {
            task.cancel();
            holder.reconcileTask(null);
        }
        holder.setReconcileQueued(false);
        openGuis.remove(player.getUniqueId(), gui);
        StorageKey key = holder.key();

        // Предметы, занесённые ванилью в дизайн-слоты, возвращаем
        // игроку до обработки хранилища/возврата.
        rescueDesignItems(holder);

        boolean destroyedBlock = key.type() == StorageType.BLOCK
                && plugin.dispatcher().isDestroyed(key.owner());
        if (key.type() == StorageType.TEMPORARY) {
            drops.returnToPlayer(player, inventory, gui);
            player.sendMessage(lang.msg("interface.temporaryReturned"));
        } else if (!destroyedBlock) {
            // InventoryCloseEvent читает уже финальное содержимое —
            // здесь диф корректен, в отличие от ClickEvent.
            reconcile(holder, null);
            storage.saveNow(key);
        }
        Bukkit.getPluginManager().callEvent(new GuiCloseEvent(player, gui, inventory, key));
    }

    /** Команда игрока по scoreboard (fallback — «default»). */
    public static String teamOf(Player player) {
        Team team = player.getScoreboard().getPlayerTeam(player);
        return team == null ? "default" : team.getName();
    }
}
