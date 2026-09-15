package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code all [the] cgui [block] viewers of %location%}
 *
 * <p>Игроки, у которых прямо сейчас открыт GUI на данном блоке.
 */
@SuppressWarnings("deprecation")
public class ExprCguiViewers extends SimpleExpression<Player> {

    private Expression<Location> location;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.location = (Expression<Location>) exprs[0];
        return true;
    }

    @Override
    public Class<? extends Player> getReturnType() {
        return Player.class;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "all cgui viewers of " + location.toString(event, debug);
    }

    @Override
    protected Player[] get(Event event) {
        Location loc = location.getSingle(event);
        if (loc == null) {
            return null;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return null;
        }
        return plugin.service().getViewers(loc).toArray(new Player[0]);
    }
}
