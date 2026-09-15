package dev.moonaticks.customGuiReworked.denizen.events;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import dev.moonaticks.customGuiReworked.api.event.GuiDragEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Denizen-событие: {@code on cgui drag}
 *
 * <p>Контекст: {@code context.player}, {@code context.gui},
 * {@code context.slots} (список затронутых слотов верхнего инвентаря).
 */
public class CguiDragScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // cgui drag
    //
    // @Group CustomGuiReworked
    //
    // @Triggers when a player distributes an item with a drag over a CustomGuiReworked interface.
    //
    // @Cancellable true
    //
    // @Context
    // <context.player> Returns the player.
    // <context.gui> Returns the name of the GUI.
    // <context.slots> Returns the list of affected top-inventory slots.
    //
    // @Player Always.
    //
    // @Plugin CustomGuiReworked
    //
    // -->

    public CguiDragScriptEvent() {
        registerCouldMatcher("cgui drag");
    }

    public PlayerTag player;
    public ElementTag guiName;
    public ListTag slots;

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
        if (name.equals("slots")) {
            return slots;
        }
        return super.getContext(name);
    }

    @EventHandler
    public void onDrag(GuiDragEvent event) {
        this.player = new PlayerTag(event.getPlayer());
        this.guiName = new ElementTag(event.getGui() == null ? "" : event.getGui().name());
        this.slots = new ListTag();
        for (int slot : event.getTopSlots()) {
            this.slots.addObject(new ElementTag(slot));
        }
        fire(event);
    }
}
