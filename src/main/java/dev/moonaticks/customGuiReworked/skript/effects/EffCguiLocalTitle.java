package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code set cgui local title of %player% to %string%}
 *
 * <p>Локальный заголовок окна (legacy §-коды поддерживаются).
 */
@SuppressWarnings("deprecation")
public class EffCguiLocalTitle extends Effect {

    private Expression<Player> player;
    private Expression<String> title;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.player = (Expression<Player>) exprs[0];
        this.title = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player p = player.getSingle(event);
        String t = title.getSingle(event);
        if (p == null || t == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return;
        }
        plugin.service().setLocalTitle(p, t);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "set cgui local title of " + player.toString(event, debug) + " to " + title.toString(event, debug);
    }
}
