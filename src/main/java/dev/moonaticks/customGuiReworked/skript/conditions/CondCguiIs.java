package dev.moonaticks.customGuiReworked.skript.conditions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code [the] cgui (of|for) %player% is %string%}
 */
@SuppressWarnings("deprecation")
public class CondCguiIs extends Condition {


    private Expression<Player> player;
    private Expression<String> name;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.player = (Expression<Player>) exprs[0];
        this.name = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui of " + player.toString(event, debug) + " is " + name.toString(event, debug);
    }

    @Override
    public boolean check(Event event) {
        Player p = player.getSingle(event);
        String expected = name.getSingle(event);
        if (p == null || expected == null) {
            return false;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return false;
        }
        Gui gui = plugin.opener().guiOf(p.getUniqueId());
        return gui != null && gui.name().equalsIgnoreCase(expected);
    }
}
