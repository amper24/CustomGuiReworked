package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.util.SimpleExpression;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.event.Event;

/**
 * {@code all [the] cgui[s]} / {@code all [the] cgui names} — список имён всех GUI.
 */
@SuppressWarnings("deprecation")
public class ExprCguiNames extends SimpleExpression<String> {

    static {
        Skript.registerExpression(ExprCguiNames.class, String.class, ExpressionType.COMBINED,
                "all [the] cgui[s]",
                "all [the] cgui names");
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
            return null;
        }
        return plugin.registry().names().toArray(new String[0]);
    }
}
