package dev.moonaticks.customGuiReworked;

import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.GuiService;
import dev.moonaticks.customGuiReworked.api.GuiServiceImpl;
import dev.moonaticks.customGuiReworked.command.GuiCommand;
import dev.moonaticks.customGuiReworked.command.GuiTabCompleter;
import dev.moonaticks.customGuiReworked.codec.BukkitItemCodec;
import dev.moonaticks.customGuiReworked.codec.LegacyBukkitMapItemCodec;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.editor.EditorHolder;
import dev.moonaticks.customGuiReworked.editor.EditorListener;
import dev.moonaticks.customGuiReworked.editor.EditorManager;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import dev.moonaticks.customGuiReworked.integration.BlockHookDispatcher;
import dev.moonaticks.customGuiReworked.integration.BlockHookManager;
import dev.moonaticks.customGuiReworked.codec.NbtApiItemCodec;
import dev.moonaticks.customGuiReworked.denizen.CguiDenizenSupport;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import dev.moonaticks.customGuiReworked.listeners.GuiInteractionListener;
import dev.moonaticks.customGuiReworked.listeners.PlayerListener;
import dev.moonaticks.customGuiReworked.manager.ManagerHolder;
import dev.moonaticks.customGuiReworked.manager.ManagerListener;
import dev.moonaticks.customGuiReworked.manager.ManagerMenu;
import dev.moonaticks.customGuiReworked.skript.SkriptSupport;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import dev.moonaticks.customGuiReworked.util.DesignItems;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * CustomGuiReworked — skeleton-based GUI framework для Paper 26.2.
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
    private ManagerMenu manager;
    private BlockHookDispatcher dispatcher;
    private BlockHookManager hookManager;
    private GuiService registeredService;
    private FunctionalBlockRegistry functionalBlocks;
    /** Тикер onTick функциональных блоков (каждые 5 тиков). */
    private BukkitTask functionalTickTask;

    /** Публичный сервис (GuiService) плагина (используется Skript/Denizen-обёртками). */
    public GuiService service() {
        return registeredService;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultLanguageFiles();

        languageManager = new LanguageManager(this);
        languageManager.load();

        // Утилита маркера/max-stack дизайн-предметов — инициализируем до GUI-opener,
        // чтобы applyDesign и rescueDesignItems видели NamespacedKey.
        DesignItems.init(this);

        // Кодек предметов: NBTAPI (эталонный формат экосистемы) или Bukkit-fallback.
        // Вторичные кодеки регистрируются всегда, чтобы читать payload любого формата.
        Codecs.register(new LegacyBukkitMapItemCodec());
        Codecs.register(new BukkitItemCodec());
        if (getServer().getPluginManager().getPlugin("NBTAPI") != null) {
            Codecs.initialize(new NbtApiItemCodec());
            getLogger().info("Item codec: NBTAPI (tag n1)");
        } else {
            Codecs.initialize(new BukkitItemCodec());
            getLogger().warning("NBTAPI not found — using native Paper item codec (tag b2). "
                    + "Install NBTAPI for full NBT fidelity.");
        }

        registry = new GuiRegistry(this);
        registry.loadAll();

        storage = new StorageService(this);

        opener = new GuiOpener(this, registry, storage, languageManager);
        editor = new EditorManager(this, registry, languageManager);
        manager = new ManagerMenu(this, registry, opener, editor, languageManager);
        dispatcher = new BlockHookDispatcher(this, registry, languageManager);
        hookManager = new BlockHookManager(this);
        hookManager.init(dispatcher);

        // Функциональные блоки: реестр обработчиков + тикер onTick
        // (каждые 5 тиков — анимации прогресса, крафты, топливо) +
        // данные блоков (data/functional) с восстановлением «работы».
        functionalBlocks = new FunctionalBlockRegistry(this);
        functionalBlocks.loadData(new File(getDataFolder(), "data/functional"));
        functionalTickTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    dispatcher.tickFunctionalBlocks();
                } catch (Exception e) {
                    getLogger().warning("Functional blocks tick failed: " + e.getMessage());
                }
            }
        }.runTaskTimer(this, BlockHookDispatcher.FUNCTIONAL_TICK_INTERVAL,
                BlockHookDispatcher.FUNCTIONAL_TICK_INTERVAL);

        getServer().getPluginManager().registerEvents(new GuiInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new EditorListener(this, editor), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ManagerListener(this, manager), this);
        getServer().getPluginManager().registerEvents(storage.blockBackend(), this);

        // Скриптовые интеграции (мягкие зависимости: Skript, Denizen)
        new SkriptSupport(this).init();
        new CguiDenizenSupport(this).init();

        PluginCommand guiCommand = getCommand("gui");
        if (guiCommand != null) {
            guiCommand.setExecutor(new GuiCommand(this));
            guiCommand.setTabCompleter(new GuiTabCompleter(this));
        }

        // Публичный API как библиотека: Bukkit Services + статический фасад
        GuiService service = new GuiServiceImpl(this);
        getServer().getServicesManager().register(GuiService.class, service, this, ServicePriority.Normal);
        registeredService = service;
        CustomGuiAPI.initialize(service);

        hello();
    }

    @Override
    public void onDisable() {
        // Тикер функциональных блоков — до закрытия меню (иначе onTick
        // может дёрнуть уже закрывающиеся инвентари).
        if (functionalTickTask != null) {
            functionalTickTask.cancel();
            functionalTickTask = null;
        }
        // Сначала закрываем все наши меню (на основном потоке, пока I/O жив):
        // закрытие само делает финальную реконсиляцию и saveNow, плюс из GUI
        // возвращаются/дропаются предметы. Иначе после выключения листенеры
        // сняты, а меню остаётся открытым — риск дюпа предметов.
        closeOpenMenus();
        if (functionalBlocks != null) {
            functionalBlocks.saveData();
        }
        if (storage != null) {
            storage.stopAutosave();
            storage.flushAll();
        }
        if (registeredService != null) {
            getServer().getServicesManager().unregister(registeredService);
            registeredService = null;
        }
        CustomGuiAPI.shutdown();
        Codecs.reset();
    }

    /**
     * Закрывает открытые у игроков меню плагина (рантайм-GUI, редактор,
     * менеджер) — события закрытия отрабатывают штатно (флэш хранилища,
     * возврат временных предметов).
     */
    private void closeOpenMenus() {
        for (Player player : getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            InventoryHolder holder = top.getHolder();
            if (holder instanceof GuiHolder
                    || holder instanceof EditorHolder
                    || holder instanceof ManagerHolder) {
                player.closeInventory();
            }
        }
    }

    /** Перезагрузка конфигурации, языка и GUI (команда /gui reload). */
    public void reloadPluginData() {
        reloadConfig();
        // Язык мог быть переключён вместе с language-auto-update —
        // перепроверяем версию файлов на диске перед чтением строк.
        saveDefaultLanguageFiles();
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
                continue;
            }
            refreshLanguageFile(langDir, langFile, fileName);
        }
    }

    /**
     * Обновляет файл языка на диске, если в jar лежит более новая версия
     * строк (ключ {@code lang-version}). Файлы на диске создаются один раз
     * и сами не перезаписываются, поэтому без этой проверки старая копия
     * показывала бы старые формулировки и символы.
     *
     * <p>Прежний файл сохраняется рядом как {@code <имя>.bak}. Проверку
     * можно выключить через {@code language-auto-update: false} в config.yml.
     */
    private void refreshLanguageFile(File langDir, File langFile, String fileName) {
        int bundled = bundledLangVersion(fileName);
        int onDisk = YamlConfiguration.loadConfiguration(langFile).getInt("lang-version", 1);
        if (bundled <= onDisk) {
            return;
        }
        if (!getConfig().getBoolean("language-auto-update", true)) {
            getLogger().info("Language file " + fileName + " is outdated (v" + onDisk
                    + " -> v" + bundled + "), auto-update is disabled in config.yml");
            return;
        }
        File backup = new File(langDir, fileName + ".bak");
        try {
            Files.copy(langFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            saveResource("lang/" + fileName, true);
            getLogger().info("Updated language file " + fileName + " (v" + onDisk + " -> v" + bundled
                    + "), previous copy kept as " + backup.getName());
        } catch (IOException | IllegalArgumentException e) {
            getLogger().warning("Could not update language file " + fileName + ": " + e.getMessage());
        }
    }

    /** Версия строк во встроенном ресурсе lang/<имя> (1, если ключа нет). */
    private int bundledLangVersion(String fileName) {
        try (InputStream in = getResource("lang/" + fileName)) {
            if (in == null) {
                return 0;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return config.getInt("lang-version", 1);
        } catch (IOException e) {
            getLogger().warning("Could not read bundled lang/" + fileName + ": " + e.getMessage());
            return 0;
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

    public ManagerMenu manager() {
        return manager;
    }

    public BlockHookDispatcher dispatcher() {
        return dispatcher;
    }

    public BlockHookManager hooks() {
        return hookManager;
    }

    /** Реестр функциональных блоков (печь/верстак/бочка/генератор). */
    public FunctionalBlockRegistry functionalBlocks() {
        return functionalBlocks;
    }
}
