package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.event.Event;

/**
 * {@code cgui block data of %location% key %string%}
 * {@code cgui block data of %location% (id|blockid) %string% key %string%}
 *
 * <p>Значение из персистентных данных функционального блока
 * (пусто, если ключа нет или блок не функциональный).
 */
@SuppressWarnings("deprecation")
public class ExprCguiBlockData extends SimpleExpression<String> {

    private Expression<Location> location;
    private Expression<String> blockId;
    private Expression<String> key;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.location = (Expression<Location>) exprs[0];
        this.blockId = matchedPattern == 1 ? (Expression<String>) exprs[1] : null;
        this.key = (Expression<String>) exprs[matchedPattern == 1 ? 2 : 1];
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
        return "cgui block data of " + location.toString(event, debug) + " key " + key.toString(event, debug);
    }

    @Override
    protected String[] get(Event event) {
        Location loc = location.getSingle(event);
        String k = key.getSingle(event);
        if (loc == null || k == null) {
            return null;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return null;
        }
        String id = blockId == null ? null : blockId.getSingle(event);
        FunctionalBlockData data = id == null
                ? plugin.service().blockData(loc)
                : plugin.service().blockData(id, loc);
        if (data == null) {
            return null;
        }
        String v = data.getString(k, null);
        return v == null ? null : new String[]{v};
    }
}
