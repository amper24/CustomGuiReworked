package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * {@code cgui old item (of|from) %event%} / {@code cgui new item (of|from) %event%}
 *
 * <p>Предметы «было/стало» события {@code cgui slot changed}
 * (пусто, если слота было/стало пусто).
 */
@SuppressWarnings("deprecation")
public class ExprCguiSlotItemChanged extends SimpleExpression<ItemStack> {

    private Expression<Event> event;
    private boolean oldItem;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.oldItem = matchedPattern == 0;
        this.event = (Expression<Event>) exprs[0];
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
        return (oldItem ? "cgui old item of " : "cgui new item of ") + this.event.toString(event, debug);
    }

    @Override
    protected ItemStack[] get(Event event) {
        Event e = this.event.getSingle(event);
        if (!(e instanceof GuiSlotChangedEvent changed)) {
            return null;
        }
        ItemStack item = oldItem ? changed.getOldItem() : changed.getNewItem();
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        return new ItemStack[]{item};
    }
}
