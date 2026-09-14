package dev.moonaticks.customGuiReworked.skript.conditions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code %player% has [a] cgui open}
 * {@code %player% [is|was] viewing a cgui}
 */
@SuppressWarnings("deprecation")
public class CondCguiOpen implements Condition {

    static {
        Skript.registerCondition(CondCguiOpen.class,
                "%player% has [a] cgui open",
                "%player% [is|was] viewing a cgui");
    }

    private Expression<?> player;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.player = exprs[0];
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui open of " + player.toString(event, debug);
    }

    @Override
    public boolean check(Event event) {
        Player p = player.getSingle(event);
        if (p == null) {
            return false;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        return plugin != null && plugin.opener().guiOf(p.getUniqueId()) != null;
    }
}
