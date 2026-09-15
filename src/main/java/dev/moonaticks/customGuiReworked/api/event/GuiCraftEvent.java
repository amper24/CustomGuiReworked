package dev.moonaticks.customGuiReworked.api.event;

import dev.moonaticks.customGuiReworked.api.Gui;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

/**
 * Вызывается, когда игрок кликает по RESULT-слоту GUI, в котором
 * хотя бы один CRAFT-слот заполнен (т.е. «крафт потенциально валиден»).
 *
 * <p>Типичный сценарий функциональных блоков (печь/верстак): результат
 * показывается в RESULT-слоте, игрок кликает по нему — тут можно
 * проверить рецепт ({@code GuiService#matchesCraft}), подтвердить крафт
 * (расход CRAFT/FUEL через {@code consumeFuel}/{@code produceResult})
 * или отменить забирание предмета.
 *
 * <p>Отмена события отменяет исходный клик (предмет не забирается).
 */
public class GuiCraftEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Gui gui;
    private final Inventory inventory;
    private final int slot;
    private final ClickType click;
    private final boolean hasCraftItems;
    private boolean cancelled;

    public GuiCraftEvent(Player player, Gui gui, Inventory inventory, int slot,
                         ClickType click, boolean hasCraftItems) {
        this.player = player;
        this.gui = gui;
        this.inventory = inventory;
        this.slot = slot;
        this.click = click;
        this.hasCraftItems = hasCraftItems;
    }

    /** Игрок, кликнувший по RESULT-слоту. */
    public Player getPlayer() {
        return player;
    }

    /** GUI, в котором произошло взаимодействие. */
    public Gui getGui() {
        return gui;
    }

    /** Верхний инвентарь GUI. */
    public Inventory getInventory() {
        return inventory;
    }

    /** RESULT-слот, по которому кликнули. */
    public int getSlot() {
        return slot;
    }

    /** Тип клика. */
    public ClickType getClick() {
        return click;
    }

    /** Заполнен ли хотя бы один CRAFT-слот этого GUI. */
    public boolean hasCraftItems() {
        return hasCraftItems;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Отмена запрещает забирание предмета (исходный клик отменяется). */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
