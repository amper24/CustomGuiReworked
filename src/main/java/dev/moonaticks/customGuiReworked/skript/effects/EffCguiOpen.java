package dev.moonaticks.customGuiReworked.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code open [the] cgui %string% (to|for) %player%}
 * {@code open [the] cgui %string% (to|for) %player% with storage %string%}
 *
 * <p>Вторая форма временно подменяет тип хранилища при открытии.
 */
@SuppressWarnings("deprecation")
public class EffCguiOpen extends Effect {


    private Expression<String> name;
    private Expression<Player> player;
    private Expression<String> storage;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.name = (Expression<String>) exprs[0];
        this.player = (Expression<Player>) exprs[1];
        this.storage = matchedPattern == 1 ? (Expression<String>) exprs[2] : null;
        return true;
    }

    @Override
    protected void execute(Event event) {
        String s = name.getSingle(event);
        Player p = player.getSingle(event);
        if (s == null || p == null) {
            return;
        }
        CustomGuiReworked plugin = SkriptSupport.plugin();
        if (plugin == null) {
            return;
        }
        Gui gui = plugin.registry().get(s);
        if (gui == null) {
            LanguageManager lang = plugin.lang();
            p.sendMessage(lang.msg("cmd.guiNotFound", s));
            return;
        }
        StorageType override = null;
        if (storage != null) {
            String storageId = storage.getSingle(event);
            if (storageId != null) {
                override = StorageType.fromId(storageId);
            }
        }
        plugin.opener().openForPlayer(p, gui, null, override);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "open cgui " + name.toString(event, debug) + " to " + player.toString(event, debug);
    }
}
