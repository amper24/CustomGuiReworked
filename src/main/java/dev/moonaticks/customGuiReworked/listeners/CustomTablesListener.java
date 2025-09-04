package dev.moonaticks.customGuiReworked.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.moonaticks.customGuiReworked.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.tools.Gui;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;
import dev.moonaticks.customGuiReworked.tools.ItemDrops;
import dev.moonaticks.customGuiReworked.tools.TableGUI;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CustomTablesListener implements Listener {
    CustomGuiReworked plugin;
    TableGUI tableGUI;
    TableEditorManager manager;
    DatabaseManager databaseManager;
    ItemDrops itemDrops;
    BlockBreakListener blockBreakListener;
    File dir;

    private static final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();

    public CustomTablesListener(CustomGuiReworked plugin, TableGUI tableGUI, TableEditorManager manager,
                                DatabaseManager databaseManager, ItemDrops itemDrops, File dir, BlockBreakListener blockBreakListener) {
        this.plugin = plugin;
        this.tableGUI = tableGUI;
        this.manager = manager;
        this.databaseManager = databaseManager;
        this.itemDrops = itemDrops;
        this.blockBreakListener = blockBreakListener;

        this.dir = new File(dir, "tables");
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {

    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        blockBreakListener.removeOpenInterface(player);

        Inventory inventory = event.getView().getTopInventory();
        Gui gui = manager.getGuiByFile(tableGUI.getFileNameByInventory(inventory));
        if (gui == null) {
            return;
        }
        if(gui.getSaveDataMethode() == 5) {
            tableGUI.removeTemporaryInventory(inventory);
            itemDrops.ReturnItems(player, inventory, gui);
        } else {
            tableGUI.saveTo(player, gui, inventory);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();
        if (tableGUI.sharedInventories.containsValue(inventory)) {
            String fileName = tableGUI.getFileNameByInventory(inventory);
            Gui gui = manager.getGuiByFile(fileName);
            if (gui == null) {
                String message = CustomGuiReworked.languageMap.getOrDefault("ClickInInventoryButNoGuiFound", "Клик в инвентаре '%s', но объект Gui не найден (игрок=%s)");
                plugin.getLogger().warning(String.format(message, fileName, player.getName()));
                return;
            }

            int slotCount = gui.getSlots();
            if (inventory.getSize() == slotCount) {
                List<String> skeleton = gui.getSkeleton();

                int clickedSlot = event.getSlot();
                String slotData = (clickedSlot >= 0 && clickedSlot < skeleton.size()) ? skeleton.get(clickedSlot) : "aboba";

                if (slotData.contains("design") && clickedInventory != null && clickedInventory.equals(inventory)) {
                        event.setCancelled(true);
                }
                if (slotData.contains("result") && event.getCursor().getType() != Material.AIR && !event.isShiftClick() && clickedInventory != null && clickedInventory.equals(inventory)) {
                        event.setCancelled(true);
                }

                JsonArray execution = gui.getCommandExecutor();
                for (JsonElement e : execution) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.get("slot").getAsInt() == clickedSlot && clickedInventory != null && clickedInventory.equals(inventory)) {
                        executeCommandFromJson(player, o);
                    }
                }
                if(gui.getSaveDataMethode() != 5) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                        tableGUI.saveTo(player, gui, inventory)
                    );
                }
            }
        }
    }
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getView().getTopInventory();
        // Проверяем, содержится ли инвентарь в sharedInventories
        if (tableGUI.sharedInventories.containsValue(inventory)) {
            String fileName = tableGUI.getFileNameByInventory(inventory);
            Gui gui = manager.getGuiByFile(fileName);
            if (fileName != null) {
                int slotCount = gui.getSlots();
                if (inventory.getSize() == slotCount) {
                    List<String> skeleton = gui.getSkeleton();
                    for (int slot : event.getRawSlots()) {
                        String slotType = (slot >= 0 && slot < skeleton.size()) ? skeleton.get(slot) : "aboba";
                        if (slotType.contains("design") || (slotType.contains("result") && event.getOldCursor().getType() != Material.AIR)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    if(gui.getSaveDataMethode() != 5) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                                tableGUI.saveTo(player, gui, inventory)
                        );
                    }
                }
            }
        }
    }
    public static void executeCommandFromJson(Player player, JsonObject json) {
        String command = json.get("command").getAsString().replaceFirst("/", ""); // Убираем "/"
        int delay = json.get("delay").getAsInt();
        blockedPlayers.add(player.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean opTurner = false;
                try {
                    if (player.isOp()) {
                        opTurner = true;
                    }
                    // Временно даем игроку права оператора
                    if (!opTurner)
                    {
                        player.setOp(true);
                    }
                    // Выполняем команду
                    Bukkit.dispatchCommand(player, command);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // Забираем права оператора
                    if (!opTurner)
                    {
                        player.setOp(false);
                    }
                    blockedPlayers.remove(player.getUniqueId());
                }
            }
        }.runTaskLater(JavaPlugin.getPlugin(CustomGuiReworked.class), delay);
    }
    @EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (blockedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}