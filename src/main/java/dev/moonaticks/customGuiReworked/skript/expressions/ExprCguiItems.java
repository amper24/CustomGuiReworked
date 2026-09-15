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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code [all] [the] cgui items (of|from) %string% (for|of) %player%}
 *
 * <p>Все предметы из данных хранилища GUI (неизменяемые слоты
 * включаются, если в них что-то сохранено).
 */
@SuppressWarnings("deprecation")
public class ExprCguiItems extends SimpleExpression<ItemStack> {


    private Expression<String> name;
    private Expression<Player> player;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.name = (Expression<String>) exprs[0];
        this.player = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    public Class<? extends ItemStack> getReturnType() {
        return ItemStack.class;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui items of " + name.toString(event, debug);
    }

    @Override
    protected ItemStack[] get(Event event) {
        String s = name.getSingle(event);
        Player p = player.getSingle(event);
        if (s == null || p == null) {
            return null;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return null;
        }
        Gui gui = plugin.registry().get(s);
        if (gui == null) {
            return null;
        }
        String[] data = CguiStorageAccess.read(plugin, gui, p);
        List<ItemStack> items = new ArrayList<>();
        for (String payload : data) {
            ItemStack item = Codecs.decode(payload);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }
        return items.isEmpty() ? null : items.toArray(new ItemStack[0]);
    }
}
