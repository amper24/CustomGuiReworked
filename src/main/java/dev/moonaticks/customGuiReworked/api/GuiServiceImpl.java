package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Реализация {@link GuiService} (внутренняя; плагин регистрирует
 * её через Bukkit Services API).
 */
public class GuiServiceImpl implements GuiService {

    private final CustomGuiReworked plugin;

    public GuiServiceImpl(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginName() {
        return "CustomGuiReworked";
    }

    @Override
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    // ================= GUI =================

    @Override
    public Gui getGui(String name) {
        return plugin.registry().get(name);
    }

    @Override
    public Set<String> getGuiNames() {
        return plugin.registry().names();
    }

    @Override
    public Gui createGui(String name) {
        return plugin.registry().create(name);
    }

    @Override
    public Gui loadGui(String name) {
        return plugin.registry().reload(name);
    }

    @Override
    public boolean deleteGui(String name) {
        return plugin.registry().delete(name);
    }

    @Override
    public void saveGui(Gui gui) {
        plugin.registry().save(gui);
    }

    // ================= открытие =================

    @Override
    public void openGui(Player player, String name) {
        Gui gui = getGui(name);
        if (gui == null) {
            player.sendMessage(plugin.lang().msg("cmd.guiNotFound", name));
            return;
        }
        plugin.opener().openForPlayer(player, gui);
    }

    @Override
    public void openGui(Player player, String name, Location blockLocation) {
        Gui gui = getGui(name);
        if (gui == null) {
            player.sendMessage(plugin.lang().msg("cmd.guiNotFound", name));
            return;
        }
        plugin.opener().openForPlayer(player, gui, blockLocation);
    }

    // ================= блоки =================

    @Override
    public void registerBlockGui(String blockId, String guiName) {
        Gui gui = getGui(guiName);
        if (gui == null || blockId == null || blockId.isBlank()) {
            return;
        }
        gui.addBlockId(blockId);
        plugin.registry().save(gui);
    }

    @Override
    public void unregisterBlockGui(String blockId) {
        if (blockId == null) {
            return;
        }
        for (String name : List.copyOf(plugin.registry().names())) {
            Gui gui = getGui(name);
            if (gui != null && gui.removeBlockId(blockId)) {
                plugin.registry().save(gui);
            }
        }
    }

    @Override
    public Gui getBlockGui(String blockId) {
        return plugin.registry().getByBlockId(blockId);
    }

    @Override
    public Set<String> getBlockIds(String guiName) {
        Gui gui = getGui(guiName);
        return gui == null ? Set.of() : gui.blockIds();
    }

    // ================= хранилище =================

    @Override
    public List<ItemStack> readStorage(StorageType type, String owner, String table) {
        String[] data = plugin.storage().load(keyOf(type, owner, table));
        List<ItemStack> out = new ArrayList<>(data.length);
        for (String payload : data) {
            ItemStack item = Codecs.decode(payload);
            out.add(item == null || item.getType() == Material.AIR ? new ItemStack(Material.AIR) : item);
        }
        return out;
    }

    @Override
    public void writeStorage(StorageType type, String owner, String table, List<ItemStack> items) {
        StorageKey key = keyOf(type, owner, table);
        String[] payloads = new String[items == null ? 0 : items.size()];
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                payloads[i] = Codecs.encode(items.get(i));
            }
        }
        plugin.storage().update(key, payloads);
        plugin.storage().saveNow(key);
    }

    @Override
    public void deleteStorage(StorageType type, String owner, String table) {
        plugin.storage().delete(keyOf(type, owner, table));
    }

    private StorageKey keyOf(StorageType type, String owner, String table) {
        String t = table.endsWith(".yml") ? table : table + ".yml";
        return switch (type) {
            case BLOCK -> {
                Location location = StorageKey.blockLocation(owner == null ? "" : owner);
                if (location == null) {
                    throw new IllegalArgumentException("Invalid block owner (expected \"world:x,y,z\"): " + owner);
                }
                yield StorageKey.forBlock(location, t);
            }
            case PERSONAL -> new StorageKey(type, owner == null ? "" : owner, t);
            case TEAM -> new StorageKey(type, owner == null ? "" : owner, t);
            case GLOBAL -> StorageKey.global(t);
            case TEMPORARY -> StorageKey.temporary(null, t);
        };
    }
}
