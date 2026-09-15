package dev.moonaticks.customGuiReworked.denizen;

import com.denizenscript.denizencore.events.ScriptEvent;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.denizen.events.CguiClickScriptEvent;
import dev.moonaticks.customGuiReworked.denizen.events.CguiCloseScriptEvent;
import dev.moonaticks.customGuiReworked.denizen.events.CguiDragScriptEvent;
import dev.moonaticks.customGuiReworked.denizen.events.CguiOpenScriptEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.logging.Level;

/**
 * Поддержка Denizen: регистрация событий ({@code on cgui open/close/click})
 * и тегов (<cgui.*>).
 *
 * <p>Регистрация происходит один раз, когда плагин Denizen включён
 * (softdepend гарантирует порядок; если Denizen включается позже —
 * слушаем {@link PluginEnableEvent}).
 */
public final class CguiDenizenSupport implements Listener {

    public static final String DENIZEN_PLUGIN = "Denizen";

    private static volatile CustomGuiReworked instance;
    private static volatile boolean registered;

    private final CustomGuiReworked plugin;
    private final boolean enabled;

    public CguiDenizenSupport(CustomGuiReworked plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("integration.denizen", true);
    }

    /** Плагин для тегов Denizen (устанавливается при регистрации). */
    public static CustomGuiReworked plugin() {
        return instance;
    }

    public static boolean isRegistered() {
        return registered;
    }

    public void init() {
        if (!enabled) {
            plugin.getLogger().info("Denizen support is disabled in config (integration.denizen)");
            return;
        }
        instance = plugin;
        if (denizenPresent()) {
            registerNow();
        } else {
            plugin.getLogger().info("Denizen not found — support will activate when Denizen is enabled");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean denizenPresent() {
        return plugin.getServer().getPluginManager().getPlugin(DENIZEN_PLUGIN) != null;
    }

    private synchronized void registerNow() {
        if (registered || !denizenPresent()) {
            return;
        }
        try {
            ScriptEvent.registerScriptEvent(CguiOpenScriptEvent.class);
            ScriptEvent.registerScriptEvent(CguiCloseScriptEvent.class);
            ScriptEvent.registerScriptEvent(CguiClickScriptEvent.class);
            ScriptEvent.registerScriptEvent(CguiDragScriptEvent.class);
            new CguiTagBase(plugin);
            registered = true;
            plugin.getLogger().info("Denizen support: events and tags registered");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register Denizen support", t);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (enabled && DENIZEN_PLUGIN.equalsIgnoreCase(event.getPlugin().getName())) {
            registerNow();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (DENIZEN_PLUGIN.equalsIgnoreCase(event.getPlugin().getName())) {
            registered = false;
        }
    }
}
