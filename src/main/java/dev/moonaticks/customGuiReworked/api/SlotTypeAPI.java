package dev.moonaticks.customGuiReworked.api;

import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * API для определения типов слотов в инвентаре
 * Предоставляет удобные методы для работы с различными типами слотов
 */
public class SlotTypeAPI {
    
    /**
     * Типы слотов
     */
    public enum SlotType {
        DESIGN,      // Слот дизайна (декоративный)
        CONTAINER,   // Слот контейнера (хранилище)
        CRAFT,       // Слот крафта (рецепт)
        RESULT,      // Слот результата (выход крафта)
        FUEL,        // Слот топлива
        UNKNOWN      // Неизвестный тип
    }
    
    /**
     * Класс для хранения информации о слоте
     */
    public static class SlotInfo {
        private final int slot;
        private final SlotType type;
        private final boolean isEditable;
        private final boolean isClickable;
        private final String description;
        
        public SlotInfo(int slot, SlotType type, boolean isEditable, boolean isClickable, String description) {
            this.slot = slot;
            this.type = type;
            this.isEditable = isEditable;
            this.isClickable = isClickable;
            this.description = description;
        }
        
        public int getSlot() { return slot; }
        public SlotType getType() { return type; }
        public boolean isEditable() { return isEditable; }
        public boolean isClickable() { return isClickable; }
        public String getDescription() { return description; }
        
        @Override
        public String toString() {
            return String.format("Slot[%d, %s, editable=%s, clickable=%s, desc='%s']", 
                slot, type, isEditable, isClickable, description);
        }
    }
    
    // Кэш для быстрого определения типов слотов
    private static final Map<String, SlotInfo> slotCache = new HashMap<>();
    
    /**
     * Определить тип слота по его позиции и содержимому
     */
    public static SlotType getSlotType(int slot, Inventory inventory) {
        String key = inventory.getType().name() + "_" + slot;
        
        if (slotCache.containsKey(key)) {
            return slotCache.get(key).getType();
        }
        
        SlotType type = determineSlotType(slot, inventory);
        SlotInfo info = new SlotInfo(slot, type, isSlotEditable(slot, inventory), 
                                   isSlotClickable(slot, inventory), getSlotDescription(slot, inventory));
        slotCache.put(key, info);
        
        return type;
    }
    
    /**
     * Получить полную информацию о слоте
     */
    public static SlotInfo getSlotInfo(int slot, Inventory inventory) {
        String key = inventory.getType().name() + "_" + slot;
        
        if (slotCache.containsKey(key)) {
            return slotCache.get(key);
        }
        
        SlotType type = determineSlotType(slot, inventory);
        SlotInfo info = new SlotInfo(slot, type, isSlotEditable(slot, inventory), 
                                   isSlotClickable(slot, inventory), getSlotDescription(slot, inventory));
        slotCache.put(key, info);
        
        return info;
    }
    
    /**
     * Проверить, является ли слот слотом дизайна
     */
    public static boolean isDesignSlot(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.DESIGN;
    }
    
    /**
     * Проверить, является ли слот слотом контейнера
     */
    public static boolean isContainerSlot(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.CONTAINER;
    }
    
    /**
     * Проверить, является ли слот слотом крафта
     */
    public static boolean isCraftSlot(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.CRAFT;
    }
    
    /**
     * Проверить, является ли слот слотом результата
     */
    public static boolean isResultSlot(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.RESULT;
    }
    
    /**
     * Проверить, является ли слот слотом топлива
     */
    public static boolean isFuelSlot(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.FUEL;
    }
    
    /**
     * Проверить, можно ли редактировать слот
     */
    public static boolean isSlotEditable(int slot, Inventory inventory) {
        SlotType type = getSlotType(slot, inventory);
        return type == SlotType.CONTAINER || type == SlotType.CRAFT || type == SlotType.FUEL;
    }
    
    /**
     * Проверить, можно ли кликать по слоту
     */
    public static boolean isSlotClickable(int slot, Inventory inventory) {
        SlotType type = getSlotType(slot, inventory);
        return type != SlotType.DESIGN;
    }
    
    /**
     * Получить описание слота
     */
    public static String getSlotDescription(int slot, Inventory inventory) {
        SlotType type = getSlotType(slot, inventory);
        switch (type) {
            case DESIGN: return "Слот дизайна (декоративный)";
            case CONTAINER: return "Слот контейнера (хранилище)";
            case CRAFT: return "Слот крафта (рецепт)";
            case RESULT: return "Слот результата (выход крафта)";
            case FUEL: return "Слот топлива";
            default: return "Неизвестный слот";
        }
    }
    
    /**
     * Очистить кэш слотов
     */
    public static void clearCache() {
        slotCache.clear();
    }
    
    /**
     * Получить все слоты определенного типа в инвентаре
     */
    public static int[] getSlotsByType(SlotType type, Inventory inventory) {
        int[] slots = new int[inventory.getSize()];
        int count = 0;
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (getSlotType(i, inventory) == type) {
                slots[count++] = i;
            }
        }
        
        int[] result = new int[count];
        System.arraycopy(slots, 0, result, 0, count);
        return result;
    }
    
    /**
     * Получить количество слотов определенного типа
     */
    public static int getSlotCountByType(SlotType type, Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (getSlotType(i, inventory) == type) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Проверить, есть ли в инвентаре слоты определенного типа
     */
    public static boolean hasSlotType(SlotType type, Inventory inventory) {
        return getSlotCountByType(type, inventory) > 0;
    }
    
    /**
     * Получить первый слот определенного типа
     */
    public static int getFirstSlotByType(SlotType type, Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (getSlotType(i, inventory) == type) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Определить тип слота на основе его позиции и типа инвентаря
     */
    private static SlotType determineSlotType(int slot, Inventory inventory) {
        String inventoryType = inventory.getType().name();
        
        switch (inventoryType) {
            case "CRAFTING":
                return determineCraftingSlotType(slot);
            case "FURNACE":
            case "BLAST_FURNACE":
            case "SMOKER":
                return determineFurnaceSlotType(slot);
            case "BREWING":
                return determineBrewingSlotType(slot);
            case "ANVIL":
                return determineAnvilSlotType(slot);
            case "ENCHANTING":
                return determineEnchantingSlotType(slot);
            case "BEACON":
                return determineBeaconSlotType(slot);
            case "HOPPER":
                return SlotType.CONTAINER;
            case "DISPENSER":
            case "DROPPER":
                return SlotType.CONTAINER;
            case "CHEST":
            case "ENDER_CHEST":
            case "TRAPPED_CHEST":
                return SlotType.CONTAINER;
            case "SHULKER_BOX":
                return SlotType.CONTAINER;
            case "BARREL":
                return SlotType.CONTAINER;
            default:
                // Для кастомных инвентарей используем эвристику
                return determineCustomSlotType(slot, inventory);
        }
    }
    
    /**
     * Определить тип слота для верстака
     */
    private static SlotType determineCraftingSlotType(int slot) {
        if (slot == 0) return SlotType.RESULT; // Результат крафта
        if (slot >= 1 && slot <= 9) return SlotType.CRAFT; // Слоты крафта
        return SlotType.CONTAINER; // Остальные слоты
    }
    
    /**
     * Определить тип слота для печи
     */
    private static SlotType determineFurnaceSlotType(int slot) {
        if (slot == 0) return SlotType.CRAFT; // Ингредиент
        if (slot == 1) return SlotType.FUEL; // Топливо
        if (slot == 2) return SlotType.RESULT; // Результат
        return SlotType.CONTAINER;
    }
    
    /**
     * Определить тип слота для зельевара
     */
    private static SlotType determineBrewingSlotType(int slot) {
        if (slot >= 0 && slot <= 2) return SlotType.CRAFT; // Зелья
        if (slot == 3) return SlotType.FUEL; // Топливо
        if (slot >= 4 && slot <= 6) return SlotType.CONTAINER; // Бутылки
        return SlotType.CONTAINER;
    }
    
    /**
     * Определить тип слота для наковальни
     */
    private static SlotType determineAnvilSlotType(int slot) {
        if (slot == 0) return SlotType.CRAFT; // Первый предмет
        if (slot == 1) return SlotType.CRAFT; // Второй предмет
        if (slot == 2) return SlotType.RESULT; // Результат
        return SlotType.CONTAINER;
    }
    
    /**
     * Определить тип слота для стола зачарований
     */
    private static SlotType determineEnchantingSlotType(int slot) {
        if (slot == 0) return SlotType.CRAFT; // Предмет для зачарования
        if (slot == 1) return SlotType.FUEL; // Лазурит
        return SlotType.CONTAINER;
    }
    
    /**
     * Определить тип слота для маяка
     */
    private static SlotType determineBeaconSlotType(int slot) {
        if (slot == 0) return SlotType.CRAFT; // Минерал
        return SlotType.CONTAINER;
    }
    
    /**
     * Определить тип слота для кастомных инвентарей
     */
    private static SlotType determineCustomSlotType(int slot, Inventory inventory) {
        // Эвристика для кастомных инвентарей
        int size = inventory.getSize();
        
        // Если инвентарь маленький (до 9 слотов), считаем все контейнером
        if (size <= 9) {
            return SlotType.CONTAINER;
        }
        
        // Для больших инвентарей используем паттерны
        if (size == 27) { // 3 ряда
            // Первый ряд может быть дизайном
            if (slot < 9) return SlotType.DESIGN;
            return SlotType.CONTAINER;
        }
        
        if (size == 54) { // 6 рядов
            // Первые 2 ряда могут быть дизайном
            if (slot < 18) return SlotType.DESIGN;
            return SlotType.CONTAINER;
        }
        
        // По умолчанию считаем контейнером
        return SlotType.CONTAINER;
    }
    

    
    /**
     * Утилитарные методы для быстрой проверки
     */
    
    /**
     * Проверить, является ли слот результатом крафта
     */
    public static boolean isCraftingResult(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.RESULT;
    }
    
    /**
     * Проверить, является ли слот ингредиентом крафта
     */
    public static boolean isCraftingIngredient(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.CRAFT;
    }
    
    /**
     * Проверить, является ли слот топливом
     */
    public static boolean isFuel(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.FUEL;
    }
    
    /**
     * Проверить, является ли слот декоративным
     */
    public static boolean isDecorative(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.DESIGN;
    }
    
    /**
     * Проверить, является ли слот хранилищем
     */
    public static boolean isStorage(int slot, Inventory inventory) {
        return getSlotType(slot, inventory) == SlotType.CONTAINER;
    }
}
