package dev.moonaticks.customGuiReworked.skript.conditions;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.event.Event;

/**
 * {@code %location% [is] cgui working}
 *
 * <p>true, если для функционального блока в этой локации включена
 * «работа» (серверная логика тикает, даже когда GUI закрыт).
 */
@SuppressWarnings("deprecation")
public class CondCguiWorking extends Condition {

    private Expression<Location> location;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.location = (Expression<Location>) exprs[0];
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui working of " + location.toString(event, debug);
    }

    @Override
    public boolean check(Event event) {
        Location loc = location.getSingle(event);
        if (loc == null) {
            return false;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        return plugin != null && plugin.service() != null && plugin.service().isWorking(loc);
    }
}
