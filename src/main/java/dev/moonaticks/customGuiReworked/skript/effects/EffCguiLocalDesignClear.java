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
 * {@code [clear|remove] [the] cgui local design[s] of %player%}
 *
 * <p>Сбрасывает все локальные дизайн-оверрайды игрока (окно остаётся,
 * слоты возвращают обычный дизайн GUI).
 */
@SuppressWarnings("deprecation")
public class EffCguiLocalDesignClear extends Effect {

    private Expression<Player> player;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.player = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player p = player.getSingle(event);
        if (p == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return;
        }
        plugin.service().clearAllLocalDesigns(p);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "clear cgui local designs of " + player.toString(event, debug);
    }
}
