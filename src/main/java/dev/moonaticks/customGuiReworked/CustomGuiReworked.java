package dev.moonaticks.customGuiReworked;

import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.GuiService;
import dev.moonaticks.customGuiReworked.api.GuiServiceImpl;
import dev.moonaticks.customGuiReworked.command.GuiCommand;
import dev.moonaticks.customGuiReworked.command.GuiTabCompleter;
import dev.moonaticks.customGuiReworked.codec.BukkitItemCodec;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.editor.EditorListener;
import dev.moonaticks.customGuiReworked.editor.EditorManager;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import dev.moonaticks.customGuiReworked.integration.BlockHookDispatcher;
import dev.moonaticks.customGuiReworked.integration.BlockHookManager;
import dev.moonaticks.customGuiReworked.codec.NbtApiItemCodec;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import dev.moonaticks.customGuiReworked.listeners.GuiInteractionListener;
import dev.moonaticks.customGuiReworked.listeners.PlayerListener;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.service.ServiceRegistration;

import java.io.File;

/**
 * CustomGuiReworked — skeleton-based GUI framework для Paper 26.1.
 *
 * <p>Архитектура:
 * <ul>
 *   <li>{@link Codecs} — кодек предметов (NBTAPI-стандарт или Bukkit-fallback);</li>
 *   <li>{@link GuiRegistry} — файлы GUI (tables/*.yml) и индексы;</li>
 *   <li>{@link StorageService} — оптимизированное хранилище (кэш + асинхронные записи);</li>
 *   <li>{@link GuiOpener} — открытие/закрытие интерфейсов;</li>
 *   <li>{@link EditorManager} — визуальный редактор;</li>
 *   <li>{@link BlockHookManager} — интеграции ItemsAdder/CraftEngine;</li>
 *   <li>{@link GuiService} — публичный API для других плагинов.</li>
 * </ul>
 */
public final class CustomGuiReworked extends JavaPlugin {

    private LanguageManager languageManager;
    private GuiRegistry registry;
    private StorageService storage;
    private GuiOpener opener;
    private EditorManager editor;
    private BlockHookDispatcher dispatcher;
    private BlockHookManager hookManager;
    private ServiceRegistration<GuiService> serviceRegistration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultLanguageFiles();

        languageManager = new LanguageManager(this);
        languageManager.load();

        // Кодек предметов: NBTAPI (эталонный формат экосистемы) или Bukkit-fallback
        if (getServer().getPluginManager().getPlugin("NBTAPI") != null) {
            Codecs.initialize(new NbtApiItemCodec());
            getLogger().info("Item codec: NBTAPI (tag n1)");
        } else {
            Codecs.initialize(new BukkitItemCodec());
            getLogger().warning("NBTAPI not found — using Bukkit fallback item codec (tag b1). "
                    + "Install NBTAPI for full NBT fidelity.");
        }

        registry = new GuiRegistry(this);
        registry.loadAll();

        storage = new StorageService(this);

        opener = new GuiOpener(this, registry, storage, languageManager);
        editor = new EditorManager(this, registry, languageManager);
        dispatcher = new BlockHookDispatcher(this, registry, languageManager);
        hookManager = new BlockHookManager(this);
        hookManager.init(dispatcher);

        getServer().getPluginManager().registerEvents(new GuiInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new EditorListener(this, editor), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand guiCommand = getCommand("gui");
        if (guiCommand != null) {
            guiCommand.setExecutor(new GuiCommand(this));
            guiCommand.setTabCompleter(new GuiTabCompleter(this));
        }

        // Публичный API как библиотека: Bukkit Services + статический фасад
        GuiService service = new GuiServiceImpl(this);
        serviceRegistration = getServer().getServicesManager().register(GuiService.class, service, this);
        CustomGuiAPI.initialize(service);

        hello();
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.stopAutosave();
            storage.flushAll();
        }
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }
        CustomGuiAPI.shutdown();
        Codecs.reset();
    }

    /** Перезагрузка конфигурации, языка и GUI (команда /gui reload). */
    public void reloadPluginData() {
        reloadConfig();
        languageManager.load();
        registry.loadAll();
    }

    private void hello() {
        getLogger().info("-------------------------------------------------------------");
        getLogger().info(" CustomGuiReworked " + getDescription().getVersion() + " enabled");
        getLogger().info(" GUIs: " + registry.names().size() + ", language: " + languageManager.language());
        getLogger().info("-------------------------------------------------------------");
    }

    private void saveDefaultLanguageFiles() {
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists() && !langDir.mkdirs()) {
            getLogger().warning("Could not create lang folder " + langDir);
        }
        for (String fileName : new String[]{"en.yml", "ru.yml"}) {
            File langFile = new File(langDir, fileName);
            if (!langFile.exists()) {
                try {
                    saveResource("lang/" + fileName, false);
                    getLogger().info("Created language file: " + fileName);
                } catch (IllegalArgumentException ignored) {
                    // файл отсутствует в ресурсах
                }
            }
        }
    }

    // ================= доступ к компонентам =================

    public LanguageManager lang() {
        return languageManager;
    }

    public GuiRegistry registry() {
        return registry;
    }

    public StorageService storage() {
        return storage;
    }

    public GuiOpener opener() {
        return opener;
    }

    public EditorManager editor() {
        return editor;
    }

    public BlockHookDispatcher dispatcher() {
        return dispatcher;
    }

    public BlockHookManager hooks() {
        return hookManager;
    }
}
