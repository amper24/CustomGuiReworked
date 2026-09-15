package dev.moonaticks.customGuiReworked.integration;

import org.bukkit.event.Listener;

/**
 * Хук интеграции с плагином кастомных блоков (ItemsAdder / CraftEngine).
 *
 * <p>Реализации ссылаются на API соответствующего плагина и
 * загружаются <b>только</b> при его наличии — без установленного
 * плагина классы хуков в памяти не появляются (см.
 * {@link BlockHookManager}).
 */
public interface BlockPluginHook extends Listener {

    /** Имя плагина Bukkit, с которым работает хук. */
    String pluginName();

    /** Подключение к диспетчеру (вызывается перед регистрацией слушателей). */
    void attach(BlockHookDispatcher dispatcher);
}
