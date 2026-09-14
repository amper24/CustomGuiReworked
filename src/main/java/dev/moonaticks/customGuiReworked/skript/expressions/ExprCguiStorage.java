package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.event.Event;

/**
 * {@code [the] cgui storage of %string%} — тип хранилища GUI
 * (block / personal / global / team / temporary).
 */
@SuppressWarnings("deprecation")
public class ExprCguiStorage extends SimpleExpression<String> {

    static {
        Skript.registerExpression(ExprCguiStorage.class, String.class, ExpressionType.COMBINED,
                "[the] cgui storage of %string%",
                "[the] storage of cgui %string%");
    }

    private Expression<?> name;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.name = exprs[0];
        return true;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui storage of " + name.toString(event, debug);
    }

    @Override
    protected String[] get(Event event) {
        String s = name.getSingle(event);
        if (s == null) {
            return null;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return null;
        }
        Gui gui = plugin.registry().get(s);
        return gui == null ? null : new String[]{gui.storage().id()};
    }
}
