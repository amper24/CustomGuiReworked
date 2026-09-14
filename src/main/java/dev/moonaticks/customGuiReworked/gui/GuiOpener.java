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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Открытие и закрытие GUI: сборка инвентаря по скелету/дизайну/данным,
 * события API, снапшот при закрытии.
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
        StorageKey key = null;
        switch (type) {
            case BLOCK -> {
                if (blockLocation == null || blockLocation.getWorld() == null) {
                    player.sendMessage(lang.msg("interface.blockNoLocation"));
                    return;
                }
                key = StorageKey.forBlock(blockLocation, gui.fileName());
            }
            case PERSONAL -> key = StorageKey.forPlayer(player, gui.fileName());
            case TEAM -> key = StorageKey.forTeam(teamOf(player), gui.fileName());
            case GLOBAL -> key = StorageKey.global(gui.fileName());
            case TEMPORARY -> key = StorageKey.temporary(player.getUniqueId(), gui.fileName());
            default -> {
                player.sendMessage(lang.msg("interface.broken.skeleton"));
                return;
            }
        }

        String[] stored = type == StorageType.TEMPORARY ? null : storage.load(key);

        GuiHolder holder = new GuiHolder(gui, key);
        Inventory inventory = Bukkit.createInventory(holder, gui.slots(), SERIALIZER.deserialize(gui.title()));
        holder.attach(inventory);
        applyDesign(inventory, gui);
        if (stored != null) {
            applyStorage(inventory, gui, stored);
        }

        GuiOpenEvent event = new GuiOpenEvent(player, gui, inventory);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(inventory);
    }

    private void applyDesign(Inventory inventory, Gui gui) {
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) != SlotType.DESIGN) {
                continue;
            }
            ItemStack item = Codecs.decode(gui.designAt(i));
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
            ItemStack item = Codecs.decode(payload);
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(i, item);
            }
        }
    }

    /**
     * Обработка закрытия GUI: для TEMPORARY возвращает предметы,
     * для остальных типов делает снапшот и планирует асинхронное сохранение.
     */
    public void handleClose(Player player, GuiHolder holder) {
        Gui gui = holder.gui();
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        openGuis.remove(player.getUniqueId(), gui);
        StorageKey key = holder.key();
        if (key.type() == StorageType.TEMPORARY) {
            drops.returnToPlayer(player, inventory, gui);
            player.sendMessage(lang.msg("interface.temporaryReturned"));
        } else {
            String[] snapshot = new String[gui.slots()];
            for (int i = 0; i < gui.slots(); i++) {
                snapshot[i] = gui.slotType(i) == SlotType.DESIGN ? "" : Codecs.encode(inventory.getItem(i));
            }
            storage.update(key, snapshot);
            storage.saveNow(key);
        }
        Bukkit.getPluginManager().callEvent(new GuiCloseEvent(player, gui, inventory));
    }

    /** Команда игрока по scoreboard (fallback — «default»). */
    public static String teamOf(Player player) {
        Team team = player.getScoreboard().getPlayerTeam(player);
        return team == null ? "default" : team.getName();
    }
}
