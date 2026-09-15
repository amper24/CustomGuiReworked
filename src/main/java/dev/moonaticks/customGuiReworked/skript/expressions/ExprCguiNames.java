package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.event.Event;

/**
 * {@code all [the] cgui[s]} / {@code all [the] cgui names} — список имён всех GUI.
 */
@SuppressWarnings("deprecation")
public class ExprCguiNames extends SimpleExpression<String> {

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        return true;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    protected String[] get(Event event) {
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return new String[0];
        }
        return plugin.registry().names().toArray(new String[0]);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "all cgui names";
    }
}
