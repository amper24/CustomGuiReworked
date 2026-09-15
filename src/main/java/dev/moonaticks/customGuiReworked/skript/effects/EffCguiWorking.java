package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.event.Event;

/**
 * {@code set cgui working of %location% to %boolean%}
 *
 * <p>Включает/выключает «работу» функционального блока: пока включена,
 * его серверная логика (обработчик, зарегистрированный плагином) тикает
 * каждые 5 тиков даже когда GUI закрыт. ID блока определяется через
 * CraftEngine.
 */
@SuppressWarnings("deprecation")
public class EffCguiWorking extends Effect {

    private Expression<Location> location;
    private Expression<Boolean> working;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.location = (Expression<Location>) exprs[0];
        this.working = (Expression<Boolean>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Location loc = location.getSingle(event);
        Boolean w = working.getSingle(event);
        if (loc == null || w == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return;
        }
        plugin.service().setWorking(loc, w);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "set cgui working of " + location.toString(event, debug) + " to " + working.toString(event, debug);
    }
}
