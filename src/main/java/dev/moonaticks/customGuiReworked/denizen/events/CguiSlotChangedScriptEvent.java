package dev.moonaticks.customGuiReworked.denizen.events;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Denizen-событие: {@code on cgui slot changed}
 *
 * <p>Срабатывает, когда предмет в слоте GUI изменился (игрок
 * положил/забрал/перенёс, либо плагин изменил слот серверно) —
 * на следующий тик, когда изменения уже применены.
 */
public class CguiSlotChangedScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // cgui slot changed
    //
    // @Group CustomGuiReworked
    //
    // @Triggers when an item in a CustomGuiReworked slot changed
    // (player placed/took/moved it, or the server changed it).
    // Fires on the next tick, so old/new items are the ACTUAL state.
    //
    // @Context
    // <context.player> Returns the player.
    // <context.gui> Returns the name of the GUI.
    // <context.slot> Returns the slot index.
    // <context.slot_type> Returns the slot type (container, craft, fuel, result).
    // <context.block> Returns the location of the block (empty if not a block-GUI).
    // <context.old_item> Returns the previous item (empty if the slot was empty).
    // <context.new_item> Returns the new item (empty if the slot became empty).
    //
    // @Player Always.
    //
    // @Plugin CustomGuiReworked
    //
    // -->

    public CguiSlotChangedScriptEvent() {
        registerCouldMatcher("cgui slot changed");
    }

    public PlayerTag player;
    public ElementTag guiName;
    public ElementTag slot;
    public ElementTag slotType;
    public LocationTag block;
    public ItemTag oldItem;
    public ItemTag newItem;

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(player, null);
    }

    @Override
    public ObjectTag getContext(String name) {
        if (name.equals("player")) {
            return player;
        }
        if (name.equals("gui")) {
            return guiName;
        }
        if (name.equals("slot")) {
            return slot;
        }
        if (name.equals("slot_type")) {
            return slotType;
        }
        if (name.equals("block")) {
            return block;
        }
        if (name.equals("old_item")) {
            return oldItem;
        }
        if (name.equals("new_item")) {
            return newItem;
        }
        return super.getContext(name);
    }

    @EventHandler
    public void onSlotChanged(GuiSlotChangedEvent event) {
        this.player = event.getPlayer() == null ? null : new PlayerTag(event.getPlayer());
        this.guiName = new ElementTag(event.getGui() == null ? "" : event.getGui().name());
        this.slot = new ElementTag(String.valueOf(event.getSlot()));
        this.slotType = new ElementTag(String.valueOf(event.getSlotType()).toLowerCase(java.util.Locale.ROOT));
        Inventory inventory = event.getInventory();
        this.block = null;
        if (inventory != null && inventory.getHolder() instanceof GuiHolder holder
                && holder.key().type() == StorageType.BLOCK) {
            this.block = holder.blockLocation() == null ? null : new LocationTag(holder.blockLocation());
        }
        this.oldItem = event.getOldItem() == null ? null : new ItemTag(event.getOldItem());
        this.newItem = event.getNewItem() == null ? null : new ItemTag(event.getNewItem());
        fire(event);
    }
}
