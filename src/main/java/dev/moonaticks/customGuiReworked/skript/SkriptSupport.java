package dev.moonaticks.customGuiReworked.skript;

import ch.njol.skript.Skript;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.skript.conditions.CondCguiExists;
import dev.moonaticks.customGuiReworked.skript.conditions.CondCguiIs;
import dev.moonaticks.customGuiReworked.skript.conditions.CondCguiOpen;
import dev.moonaticks.customGuiReworked.skript.effects.EffCguiClose;
import dev.moonaticks.customGuiReworked.skript.effects.EffCguiOpen;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiItem;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiItems;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiNames;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiOfPlayer;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiSize;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiStorage;
import dev.moonaticks.customGuiReworked.skript.expressions.ExprCguiTitle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.logging.Level;

/**
 * Поддержка Skript: регистрация событий/условий/выражений/действий.
 *
 * <p>Вся регистрация происходит один раз, когда плагин Skript включён
 * (softdepend гарантирует порядок; если Skript включается позже —
 * слушаем {@link PluginEnableEvent}).
 */
@SuppressWarnings("deprecation")
public final class SkriptSupport implements Listener {

    public static final String SKRIPT_PLUGIN = "Skript";

    private static volatile CustomGuiReworked instance;
    private static volatile boolean registered;

    private final CustomGuiReworked plugin;
    private final boolean enabled;

    public SkriptSupport(CustomGuiReworked plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("integration.skript", true);
    }

    /** Плагин для элемента-класса Skript (устанавливается при регистрации). */
    public static CustomGuiReworked plugin() {
        return instance;
    }

    public static boolean isRegistered() {
        return registered;
    }

    public void init() {
        if (!enabled) {
            plugin.getLogger().info("Skript support is disabled in config (integration.skript)");
            return;
        }
        instance = plugin;
        if (skriptPresent()) {
            registerNow();
        } else {
            plugin.getLogger().info("Skript not found — support will activate when Skript is enabled");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean skriptPresent() {
        return plugin.getServer().getPluginManager().getPlugin(SKRIPT_PLUGIN) != null;
    }

    private synchronized void registerNow() {
        if (registered || !skriptPresent()) {
            return;
        }
        try {
            // порядок важен: сначала события, потом элементы
            CguiSkriptEvents.register();

            Skript.registerCondition(CondCguiOpen.class,
                    "%player% has [a] cgui open",
                    "%player% [is|was] viewing a cgui");
            Skript.registerCondition(CondCguiExists.class,
                    "cgui %string% exists",
                    "%string% is [a] cgui");
            Skript.registerCondition(CondCguiIs.class,
                    "[the] cgui (of|for) %player% is %string%");

            Skript.registerExpression(ExprCguiNames.class, String.class, ch.njol.skript.lang.ExpressionType.COMBINED,
                    "all [the] cgui[s]",
                    "all [the] cgui names");
            Skript.registerExpression(ExprCguiOfPlayer.class, String.class, ch.njol.skript.lang.ExpressionType.COMBINED,
                    "[the] cgui (of|for) %player%");
            Skript.registerExpression(ExprCguiSize.class, Number.class, ch.njol.skript.lang.ExpressionType.COMBINED,
                    "[the] cgui size of %string%",
                    "[the] size of cgui %string%");
            Skript.registerExpression(ExprCguiTitle.class, String.class, ch.njol.skript.lang.ExpressionType.COMBINED,
                    "[the] cgui title of %string%",
                    "[the] title of cgui %string%");
            Skript.registerExpression(ExprCguiStorage.class, String.class, ch.njol.skript.lang.ExpressionType.COMBINED,
                    "[the] cgui storage of %string%",
                    "[the] storage of cgui %string%");
            Skript.registerExpression(ExprCguiItem.class, org.bukkit.inventory.ItemStack.class,
                    ch.njol.skript.lang.ExpressionType.COMBINED,
                    "[the] cgui item (in|at) slot %number% (of|from) %string% (for|of) %player%");
            Skript.registerExpression(ExprCguiItems.class, org.bukkit.inventory.ItemStack.class,
                    ch.njol.skript.lang.ExpressionType.COMBINED,
                    "[all] [the] cgui items (of|from) %string% (for|of) %player%");

            Skript.registerEffect(EffCguiOpen.class,
                    "open [the] cgui %string% (to|for) %player%",
                    "open [the] cgui %string% (to|for) %player% with storage %string%");
            Skript.registerEffect(EffCguiClose.class,
                    "[close|cancel] [the] cgui (of|for) %player%");

            registered = true;
            plugin.getLogger().info("Skript support: events and syntax registered");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register Skript support", t);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (enabled && SKRIPT_PLUGIN.equalsIgnoreCase(event.getPlugin().getName())) {
            registerNow();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (SKRIPT_PLUGIN.equalsIgnoreCase(event.getPlugin().getName())) {
            registered = false;
        }
    }
}
