package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * {@code set cgui local design of %player% (in|at) slot %number% to %itemstack%}
 *
 * <p>Локальный дизайн-оверрайд слота для одного игрока (стрелки,
 * прогресс, замена предметов — ванильных и кастомных). Пустой предмет
 * очищает оверрайд слота.
 */
@SuppressWarnings("deprecation")
public class EffCguiLocalDesign extends Effect {

    private Expression<Player> player;
    private Expression<Number> slot;
    private Expression<ItemStack> item;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.player = (Expression<Player>) exprs[0];
        this.slot = (Expression<Number>) exprs[1];
        this.item = (Expression<ItemStack>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player p = player.getSingle(event);
        Number n = slot.getSingle(event);
        if (p == null || n == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null || plugin.service() == null) {
            return;
        }
        ItemStack it = item.getSingle(event);
        plugin.service().setLocalDesign(p, n.intValue(), it);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "set cgui local design of " + player.toString(event, debug)
                + " at slot " + slot.toString(event, debug) + " to " + item.toString(event, debug);
    }
}
