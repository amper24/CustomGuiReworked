package dev.moonaticks.customGuiReworked.denizen.events;

import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.NPCTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import dev.moonaticks.customGuiReworked.api.event.GuiOpenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Denizen-событие: {@code on cgui open}
 *
 * <p>Отменяемое (switch «cancelled»). Контекст:
 * {@code context.player} (PlayerTag), {@code context.gui} (ElementTag).
 */
public class CguiOpenScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // cgui open
    //
    // @Group CustomGuiReworked
    //
    // @Triggers when a player opens a CustomGuiReworked interface.
    //
    // @Cancellable true
    //
    // @Context
    // <context.player> Returns the player.
    // <context.gui> Returns the name of the opened GUI.
    //
    // @Player Always.
    //
    // @Plugin CustomGuiReworked
    //
    // -->

    public CguiOpenScriptEvent() {
        registerCouldMatcher("cgui open");
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
    public void onOpen(GuiOpenEvent event) {
        this.player = new PlayerTag(event.getPlayer());
        this.guiName = new ElementTag(event.getGui() == null ? "" : event.getGui().name());
        fire(event);
    }
}
