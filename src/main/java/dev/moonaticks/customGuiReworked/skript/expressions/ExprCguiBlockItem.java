package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * {@code [the] cgui block item (in|at) slot %number% of %location%}
 *
 * <p>Предмет из персистентного слота функционального блока
 * (работает, даже когда GUI закрыт; пусто — слот пуст).
 */
@SuppressWarnings("deprecation")
public class ExprCguiBlockItem extends SimpleExpression<ItemStack> {

    private Expression<Number> slot;
    private Expression<Location> location;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.slot = (Expression<Number>) exprs[0];
        this.location = (Expression<Location>) exprs[1];
        return true;
    }

    @Override
    public Class<? extends ItemStack> getReturnType() {
        return ItemStack.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui block item at slot " + slot.toString(event, debug)
                + " of " + location.toString(event, debug);
    }

    @Override
    protected ItemStack[] get(Event event) {
        Number n = slot.getSingle(event);
        Location loc = location.getSingle(event);
        if (n == null || loc == null) {
            return null;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return null;
        }
        ItemStack item = plugin.service().getBlockSlotItem(loc, n.intValue());
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        return new ItemStack[]{item};
    }
}
