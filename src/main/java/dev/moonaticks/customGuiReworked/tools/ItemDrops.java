package dev.moonaticks.customGuiReworked.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;


import java.util.Random;

public class ItemDrops {
    CustomGuiReworked plugin;
    public ItemDrops(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    public void dropItems(Location location, JsonArray storage) {
        if (location == null || storage == null) {
            return; // Проверка на null
        }
        for (JsonElement element : storage) {
            ItemStack itemStacks = GetItemNBT.jsonToItemStack(element.toString());
            if (itemStacks.getType().equals(Material.AIR)) {
                continue; // Пропускаем некорректные элементы
            }
            location.getWorld().dropItemNaturally(location, itemStacks); // Выбрасываем предмет на землю
        }// Запускаем задачу синхронно на основном потоке
    }
    public void dropItems(Location location, Inventory storage) {
        if (location == null || storage == null) {
            return; // Проверка на null
        }
        for (ItemStack itemStacks : storage) {
            if (itemStacks.getType().equals(Material.AIR)) {
                continue; // Пропускаем некорректные элементы
            }
            location.getWorld().dropItemNaturally(location, itemStacks); // Выбрасываем предмет на землю
        }// Запускаем задачу синхронно на основном потоке
    }
    public void ReturnItems(Player player, Inventory storage) {
        // Получаем направление взгляда игрока
        Vector direction = player.getLocation().getDirection();

        // Добавляем небольшое случайное отклонение
        Random random = new Random();
        double offsetX = (random.nextDouble() - 0.5) * 0.1; // Случайное отклонение по X
        double offsetY = (random.nextDouble() - 0.5) * 0.1; // Случайное отклонение по Y
        double offsetZ = (random.nextDouble() - 0.5) * 0.1; // Случайное отклонение по Z

        // Применяем отклонение к направлению
        direction.add(new Vector(offsetX, offsetY, offsetZ));

        // Устанавливаем стандартную скорость выброса (0.3)
        direction.multiply(0.3);

        for (ItemStack item : storage) {
            // Проверяем, что предмет не null и не воздух
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            // Пытаемся добавить предмет в инвентарь игрока
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            // Если в инвентаре не хватило места, выбрасываем остаток на землю
            if (!leftover.isEmpty()) {
                for (ItemStack remainingItem : leftover.values()) {
                    // Проверяем, что остаток не null
                    if (remainingItem != null && remainingItem.getType() != Material.AIR) {
                        // Выбрасываем предмет в сторону взгляда игрока
                        Item droppedItem = player.getWorld().dropItem(player.getLocation(), remainingItem);

                        // Задаем скорость выброшенного предмета
                        droppedItem.setVelocity(direction);
                    }
                }
            }
        }
    }
}
