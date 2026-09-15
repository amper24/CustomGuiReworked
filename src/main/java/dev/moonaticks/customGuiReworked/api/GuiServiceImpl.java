package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.api.functional.CraftingRecipe;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import dev.moonaticks.customGuiReworked.storage.BlockStorageBackend;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import dev.moonaticks.customGuiReworked.util.DesignItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public Gui registerGui(Gui gui) {
        return registerGui(gui, true);
    }

    @Override
    public Gui registerGui(Gui gui, boolean persist) {
        if (gui == null) {
            return null;
        }
        plugin.registry().register(gui, persist);
        return gui;
    }

    @Override
    public boolean unregisterGui(String name, boolean deleteFile) {
        return plugin.registry().unregister(name, deleteFile);
    }

    @Override
    public String sourceOf(String name) {
        return plugin.registry().sourceOf(name);
    }

    @Override
    public List<Gui> getGuis() {
        return plugin.registry().all();
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

    @Override
    public void openGui(Player player, String name, StorageType storageOverride) {
        Gui gui = getGui(name);
        if (gui == null) {
            player.sendMessage(plugin.lang().msg("cmd.guiNotFound", name));
            return;
        }
        plugin.opener().openForPlayer(player, gui, null, storageOverride);
    }

    @Override
    public Gui getOpenGui(Player player) {
        return plugin.opener().guiOf(player == null ? null : player.getUniqueId());
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

    // ================= локальные оверрайды (per-viewer) =================

    /** Holder открытой сессии игрока, либо null (GUI не открыт). */
    private GuiHolder currentHolder(Player player) {
        if (player == null) {
            return null;
        }
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof GuiHolder guiHolder ? guiHolder : null;
    }

    /**
     * Holder сессии игрока, открытой на конкретном блоке, либо null
     * (GUI не открыт / не на этом блоке).
     */
    private GuiHolder blockHolder(Player player, Location block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        GuiHolder holder = currentHolder(player);
        if (holder == null || holder.key().type() != StorageType.BLOCK) {
            return null;
        }
        return holder.key().owner().equals(BlockStorageBackend.ownerKey(block)) ? holder : null;
    }

    @Override
    public void setLocalTitle(Player player, String title) {
        GuiHolder holder = currentHolder(player);
        if (holder == null) {
            return;
        }
        holder.setLocalTitle(title);
        if (title != null) {
            plugin.opener().applyLocalTitle(player, holder, title);
        }
    }

    @Override
    public String getLocalTitle(Player player) {
        GuiHolder holder = currentHolder(player);
        return holder == null ? null : holder.getLocalTitle();
    }

    @Override
    public void clearLocalTitle(Player player) {
        GuiHolder holder = currentHolder(player);
        if (holder != null) {
            holder.clearLocalTitle();
        }
    }

    @Override
    public void setLocalDesign(Player player, int slot, ItemStack item) {
        GuiHolder holder = currentHolder(player);
        if (holder == null) {
            return;
        }
        GuiOpener.applyLocalDesign(holder, slot, item);
    }

    @Override
    public void setLocalDesigns(Player player, Map<Integer, ItemStack> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        GuiHolder holder = currentHolder(player);
        if (holder == null) {
            return;
        }
        // Сначала валидация всех (диапазон + DESIGN/RESULT), потом применение:
        // либо всё, либо ничего.
        for (Map.Entry<Integer, ItemStack> entry : slots.entrySet()) {
            holder.setLocalDesign(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, ItemStack> entry : slots.entrySet()) {
            GuiOpener.applyLocalDesign(holder, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clearLocalDesign(Player player, int slot) {
        GuiHolder holder = currentHolder(player);
        if (holder == null || !holder.localDesigns().containsKey(slot)) {
            return;
        }
        holder.clearLocalDesign(slot);
        Inventory inventory = holder.getInventory();
        if (inventory != null) {
            inventory.setItem(slot, GuiOpener.defaultForSlot(holder, slot));
        }
    }

    @Override
    public void clearAllLocalDesigns(Player player) {
        GuiHolder holder = currentHolder(player);
        if (holder == null) {
            return;
        }
        List<Integer> overridden = new ArrayList<>(holder.localDesigns().keySet());
        holder.clearAllLocalDesigns();
        Inventory inventory = holder.getInventory();
        if (inventory != null) {
            for (int slot : overridden) {
                inventory.setItem(slot, GuiOpener.defaultForSlot(holder, slot));
            }
        }
    }

    @Override
    public ItemStack getLocalDesign(Player player, int slot) {
        GuiHolder holder = currentHolder(player);
        return holder == null ? null : holder.getLocalDesign(slot);
    }

    @Override
    public void setLocalDesign(Player player, Location block, int slot, ItemStack item) {
        GuiHolder holder = blockHolder(player, block);
        if (holder == null) {
            return;
        }
        GuiOpener.applyLocalDesign(holder, slot, item);
    }

    @Override
    public void setLocalTitle(Player player, Location block, String title) {
        GuiHolder holder = blockHolder(player, block);
        if (holder == null) {
            return;
        }
        holder.setLocalTitle(title);
        if (title != null) {
            plugin.opener().applyLocalTitle(player, holder, title);
        }
    }

    // ================= утилиты =================

    @Override
    public ItemStack prepareDesignItem(ItemStack item) {
        return DesignItems.prepare(item);
    }

    @Override
    public Location getOpenBlockLocation(Player player) {
        GuiHolder holder = currentHolder(player);
        return holder == null ? null : holder.blockLocation();
    }

    @Override
    public List<Player> getViewers(Location block) {
        return GuiOpener.getViewers(block);
    }

    // ================= крафт / топливо / результат =================

    @Override
    public boolean matchesCraft(Inventory inventory, CraftingRecipe recipe) {
        if (inventory == null || recipe == null) {
            return false;
        }
        if (!(inventory.getHolder() instanceof GuiHolder holder)) {
            return false;
        }
        return recipe.matches(inventory, holder.gui());
    }

    @Override
    public int consumeFuel(Inventory inventory, int amount) {
        if (inventory == null || amount <= 0
                || !(inventory.getHolder() instanceof GuiHolder holder)) {
            return 0;
        }
        Gui gui = holder.gui();
        int consumed = 0;
        for (int i = 0; i < gui.slots() && consumed < amount; i++) {
            if (gui.slotType(i) != SlotType.FUEL) {
                continue;
            }
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            int take = Math.min(amount - consumed, item.getAmount());
            if (take <= 0) {
                continue;
            }
            if (take >= item.getAmount()) {
                inventory.setItem(i, null);
            } else {
                ItemStack rest = item.clone();
                rest.setAmount(item.getAmount() - take);
                inventory.setItem(i, rest);
            }
            consumed += take;
        }
        if (consumed > 0) {
            holder.addAllCandidates();
            plugin.opener().scheduleReconcile(holder);
        }
        return consumed;
    }

    @Override
    public boolean produceResult(Inventory inventory, Map<Integer, ItemStack> results) {
        if (inventory == null || results == null || results.isEmpty()
                || !(inventory.getHolder() instanceof GuiHolder holder)) {
            return false;
        }
        Gui gui = holder.gui();
        // Проход 1: проверка всех целевых слотов (all-or-nothing).
        for (Map.Entry<Integer, ItemStack> entry : results.entrySet()) {
            int slot = entry.getKey();
            ItemStack produced = entry.getValue();
            if (produced == null || produced.getType() == Material.AIR) {
                continue;
            }
            if (slot < 0 || slot >= gui.slots() || gui.slotType(slot) != SlotType.RESULT) {
                return false;
            }
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                continue; // пусто — влезет
            }
            if (!current.isSimilar(produced)) {
                return false; // другой предмет — не смешиваем
            }
            int maxStack = Math.max(1, produced.getMaxStackSize());
            if (current.getAmount() + produced.getAmount() > maxStack) {
                return false; // под стек не хватает места
            }
        }
        // Проход 2: выдача.
        for (Map.Entry<Integer, ItemStack> entry : results.entrySet()) {
            ItemStack produced = entry.getValue();
            if (produced == null || produced.getType() == Material.AIR) {
                continue;
            }
            int slot = entry.getKey();
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, produced.clone());
            } else {
                current.setAmount(current.getAmount() + produced.getAmount());
            }
        }
        return true;
    }

    // ================= функциональные блоки =================

    @Override
    public FunctionalBlockRegistry getFunctionalBlocks() {
        return plugin.functionalBlocks();
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
