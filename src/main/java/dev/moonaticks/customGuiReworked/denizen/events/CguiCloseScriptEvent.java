package dev.moonaticks.customGuiReworked.denizen.events;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.NPCTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import dev.moonaticks.customGuiReworked.api.event.GuiCloseEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Denizen-событие: {@code on cgui close}
 *
 * <p>Контекст: {@code context.player} (PlayerTag),
 * {@code context.gui} (ElementTag — имя GUI).
 */
public class CguiCloseScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // cgui close
    //
    // @Group CustomGuiReworked
    //
    // @Triggers when a player closes a CustomGuiReworked interface.
    //
    // @Context
    // <context.player> Returns the player.
    // <context.gui> Returns the name of the closed GUI.
    //
    // @Player Always.
    //
    // @Plugin CustomGuiReworked
    //
    // -->

    public CguiCloseScriptEvent() {
        registerCouldMatcher("cgui close");
    }

    public PlayerTag player;
    public ElementTag guiName;

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
        return super.getContext(name);
    }

    @EventHandler
    public void onClose(GuiCloseEvent event) {
        this.player = new PlayerTag(event.getPlayer());
        this.guiName = new ElementTag(event.getGui() == null ? "" : event.getGui().name());
        fire(event);
    }
}
