package dev.moonaticks.customGuiReworked.skript.conditions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.event.Event;

/**
 * {@code cgui %string% exists}
 * {@code %string% is [a] cgui}
 */
@SuppressWarnings("deprecation")
public class CondCguiExists extends Condition {


    private Expression<String> name;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.name = (Expression<String>) exprs[0];
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui " + name.toString(event, debug) + " exists";
    }

    @Override
    public boolean check(Event event) {
        String s = name.getSingle(event);
        if (s == null) {
            return false;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        return plugin != null && plugin.registry().get(s) != null;
    }
}
