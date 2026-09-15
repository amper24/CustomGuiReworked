package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.event.Event;

/**
 * {@code set cgui block data of %location% key %string% to %string%}
 * {@code set cgui block data of %location% (id|blockid) %string% key %string% to %string%}
 *
 * <p>Пишет значение в персистентные данные функционального блока
 * (прогресс, флаги). Первая форма определяет ID блока через CraftEngine,
 * вторая — принимает его явно.
 */
@SuppressWarnings("deprecation")
public class EffCguiBlockData extends Effect {

    private Expression<Location> location;
    private Expression<String> blockId;
    private Expression<String> key;
    private Expression<String> value;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.location = (Expression<Location>) exprs[0];
        this.blockId = matchedPattern == 1 ? (Expression<String>) exprs[1] : null;
        this.key = (Expression<String>) exprs[matchedPattern == 1 ? 2 : 1];
        this.value = (Expression<String>) exprs[matchedPattern == 1 ? 3 : 2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Location loc = location.getSingle(event);
        String k = key.getSingle(event);
        String v = value.getSingle(event);
        if (loc == null || k == null || v == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return;
        }
        String id = blockId == null ? null : blockId.getSingle(event);
        FunctionalBlockData data = id == null
                ? plugin.service().blockData(loc)
                : plugin.service().blockData(id, loc);
        if (data != null) {
            data.set(k, v);
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "set cgui block data of " + location.toString(event, debug)
                + " key " + key.toString(event, debug) + " to " + value.toString(event, debug);
    }
}
