package dev.moonaticks.customGuiReworked.util;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Возврат предметов игроку при закрытии временного (TEMPORARY) GUI.
 */
public class ItemDrops {

    private final Random random = new Random();

    /**
     * Все не-дизайн предметы инвентаря возвращаются игроку:
     * сначала в инвентарь, лишнее — дропом рядом с игроком.
     */
    public void returnToPlayer(Player player, Inventory inventory, Gui gui) {
        if (player == null || inventory == null || gui == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Vector direction = player.getLocation().getDirection();
        direction.add(new Vector(
                (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.1));
        direction.multiply(0.3);

        // Возвращаем только то, что игрок мог положить сам:
        // CONTAINER/CRAFT/FUEL. DESIGN — часть GUI; RESULT (остатки
        // незабранного «результата») игроку не принадлежит.
        for (int i = 0; i < inventory.getSize(); i++) {
            SlotType type = gui.slotType(i);
            if (type == SlotType.DESIGN || type == SlotType.RESULT) {
                continue;
            }
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack rest : leftover.values()) {
                if (rest != null && rest.getType() != Material.AIR && rest.getAmount() > 0) {
                    world.dropItem(player.getLocation(), rest).setVelocity(direction);
                }
            }
        }
        // Чистим инвентарь, чтобы предметы не «проявились» повторно
        for (int i = 0; i < inventory.getSize(); i++) {
            SlotType type = gui.slotType(i);
            if (type != SlotType.DESIGN && type != SlotType.RESULT) {
                inventory.setItem(i, null);
            }
        }
    }
}
