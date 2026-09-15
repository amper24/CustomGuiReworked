package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code [close|cancel] [the] cgui (of|for) %player%}
 *
 * <p>Закрывает открытое GUI игрока (работает только если верхний
 * инвентарь — наш GUI; данные сохраняются как обычно).
 */
@SuppressWarnings("deprecation")
public class EffCguiClose extends Effect {


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
        if (plugin == null) {
            return;
        }
        Gui gui = plugin.opener().guiOf(p.getUniqueId());
        if (gui != null) {
            p.closeInventory();
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "close cgui of " + player.toString(event, debug);
    }
}
