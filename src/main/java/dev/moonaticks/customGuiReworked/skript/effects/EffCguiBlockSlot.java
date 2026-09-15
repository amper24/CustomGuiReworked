package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * {@code set cgui block item (in|at) slot %number% of %location% to %itemstack%}
 *
 * <p>Пишет предмет в персистентный слот функционального блока (GUI может
 * быть закрыт); открытые зрители перерисуются, вызовется
 * {@code cgui slot changed}. Пустой предмет очищает слот.
 */
@SuppressWarnings("deprecation")
public class EffCguiBlockSlot extends Effect {

    private Expression<Number> slot;
    private Expression<Location> location;
    private Expression<ItemStack> item;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.slot = (Expression<Number>) exprs[0];
        this.location = (Expression<Location>) exprs[1];
        this.item = (Expression<ItemStack>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Number n = slot.getSingle(event);
        Location loc = location.getSingle(event);
        if (n == null || loc == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return;
        }
        plugin.service().setBlockSlotItem(loc, n.intValue(), item.getSingle(event));
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "set cgui block item at slot " + slot.toString(event, debug)
                + " of " + location.toString(event, debug) + " to " + item.toString(event, debug);
    }
}
