package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * {@code [the] cgui item (in|at) slot %number% (of|from) %string% (for|of) %player%}
 *
 * <p>Предмет из данных хранилища GUI (не из дизайна). Для
 * TEMPORARY/BLOCK-хранилищ возвращает пусто.
 */
@SuppressWarnings("deprecation")
public class ExprCguiItem extends SimpleExpression<ItemStack> {


    private Expression<Number> slot;
    private Expression<String> name;
    private Expression<Player> player;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.slot = (Expression<Number>) exprs[0];
        this.name = (Expression<String>) exprs[1];
        this.player = (Expression<Player>) exprs[2];
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
        return "cgui item at slot " + slot.toString(event, debug) + " of " + name.toString(event, debug);
    }

    @Override
    protected ItemStack[] get(Event event) {
        Number n = slot.getSingle(event);
        String s = name.getSingle(event);
        Player p = player.getSingle(event);
        if (n == null || s == null || p == null) {
            return null;
        }
        int slotIndex = n.intValue();
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return null;
        }
        Gui gui = plugin.registry().get(s);
        if (gui == null) {
            return null;
        }
        String[] data = CguiStorageAccess.read(plugin, gui, p);
        if (slotIndex < 0 || slotIndex >= data.length) {
            return null;
        }
        ItemStack item = Codecs.decode(data[slotIndex]);
        return item == null || item.getType() == Material.AIR ? null : new ItemStack[]{item};
    }
}
