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
 * {@code [the] cgui block of %player%}
 *
 * <p>Локация функционального блока, GUI которого игрок открыл прямо
 * сейчас (пусто, если GUI не блок-GUI).
 */
@SuppressWarnings("deprecation")
public class ExprCguiBlockOfPlayer extends SimpleExpression<Location> {

    private Expression<Player> player;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.player = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    public Class<? extends Location> getReturnType() {
        return Location.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui block of " + player.toString(event, debug);
    }

    @Override
    protected Location[] get(Event event) {
        Player p = player.getSingle(event);
        if (p == null) {
            return null;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return null;
        }
        Location loc = plugin.service() == null ? null : plugin.service().getOpenBlockLocation(p);
        return loc == null ? null : new Location[]{loc};
    }
}
