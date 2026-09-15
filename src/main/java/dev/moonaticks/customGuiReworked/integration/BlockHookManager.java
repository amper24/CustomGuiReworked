package dev.moonaticks.customGuiReworked.integration;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Регистрация хуков кастомных блоков.
 *
 * <p>Реализации хуков содержат прямые ссылки на API сторонних
 * плагинов и загружаются <b>рефлективно, только при установленном
 * плагине</b>. Без ItemsAdder/CraftEngine соответствующие классы
 * никогда не загружаются — жёсткой зависимости нет.
 */
public class BlockHookManager {

    private final CustomGuiReworked plugin;
    private final List<BlockPluginHook> hooks = new ArrayList<>();

    public BlockHookManager(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    /** регистрирует все доступные хуки. */
    public void init(BlockHookDispatcher dispatcher) {
        register("ItemsAdder", "dev.moonaticks.customGuiReworked.integration.ItemsAdderHook", dispatcher);
        register("CraftEngine", "dev.moonaticks.customGuiReworked.integration.CraftEngineHook", dispatcher);
        if (hooks.isEmpty()) {
            plugin.getLogger().info("No custom block plugin found (ItemsAdder/CraftEngine) — block GUIs disabled");
        }
    }

    private void register(String pluginName, String hookClassName, BlockHookDispatcher dispatcher) {
        if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(hookClassName);
            BlockPluginHook hook = (BlockPluginHook) clazz.getDeclaredConstructor().newInstance();
            hook.attach(dispatcher);
            Bukkit.getPluginManager().registerEvents(hook, plugin);
            hooks.add(hook);
            plugin.getLogger().info("Integrated with " + pluginName);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to integrate with " + pluginName + ": " + t.getMessage());
        }
    }

    /** Подключённые хуки. */
    public List<BlockPluginHook> hooks() {
        return hooks;
    }
}
