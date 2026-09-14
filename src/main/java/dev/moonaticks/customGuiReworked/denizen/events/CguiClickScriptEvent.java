package dev.moonaticks.customGuiReworked.denizen.events;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Denizen-событие: {@code on cgui click}
 *
 * <p>Контекст: {@code context.player} (PlayerTag),
 * {@code context.gui} (ElementTag — имя GUI),
 * {@code context.slot} (ElementTag — номер слота).
 */
public class CguiClickScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // cgui click
    //
    // @Group CustomGuiReworked
    //
    // @Triggers when a player clicks a slot of a CustomGuiReworked interface.
    //
    // @Context
    // <context.player> Returns the player.
    // <context.gui> Returns the name of the GUI.
    // <context.slot> Returns the slot index that was clicked.
    //
    // @Player Always.
    //
    // @Plugin CustomGuiReworked
    //
    // -->

    public CguiClickScriptEvent() {
        registerCouldMatcher("cgui click");
    }

    public PlayerTag player;
    public ElementTag guiName;
    public ElementTag slot;

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(player);
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
        return super.getContext(name);
    }

    @EventHandler
    public void onClick(GuiSlotClickEvent event) {
        this.player = new PlayerTag(event.getPlayer());
        this.guiName = new ElementTag(event.getGui() == null ? "" : event.getGui().name());
        this.slot = new ElementTag(String.valueOf(event.getSlot()));
        fire(event);
    }
}
