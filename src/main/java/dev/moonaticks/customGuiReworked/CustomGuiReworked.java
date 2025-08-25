package dev.moonaticks.customGuiReworked;

import dev.moonaticks.customGuiReworked.commands.GuiTableCommand;
import dev.moonaticks.customGuiReworked.commands.TableTabCompleter;
import dev.moonaticks.customGuiReworked.listeners.BlockBreakListener;
import dev.moonaticks.customGuiReworked.listeners.BlockClickListener;
import dev.moonaticks.customGuiReworked.listeners.CustomTablesListener;
import dev.moonaticks.customGuiReworked.listeners.TableEditorListener;
import dev.moonaticks.customGuiReworked.managers.DatabaseManager;
import dev.moonaticks.customGuiReworked.managers.EditorGUIs;
import dev.moonaticks.customGuiReworked.managers.LanguageManager;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;
import dev.moonaticks.customGuiReworked.tools.ItemDrops;
import dev.moonaticks.customGuiReworked.tools.TableGUI;
import dev.moonaticks.customGuiReworked.api.ApiManager;
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomGuiReworked extends JavaPlugin {

    DatabaseManager databaseManager;
    File pluginFolder = getDataFolder();
    ItemDrops itemDrops;

    public TableEditorManager manager;
    public EditorGUIs editorGUIs;
    public TableGUI tableGUI;
    public TableEditorListener tableEditorListener;
    public BlockBreakListener blockBreakListener;

    public GuiTableCommand guiTableCommand;
    public TableTabCompleter tableTabCompleter;

    public BlockClickListener blockClickListener;
    public CustomTablesListener customTablesListener;

    public LanguageManager languageManager;
    public static Map<String, String> languageMap = new ConcurrentHashMap<>();
    
    public ApiManager apiManager;
    @Override
    public void onEnable() {
        //Конфиг
        saveDefaultConfig();
        //Создаем файлы переводов
        saveDefaultLanguageFiles();
        //Язык
        languageManager = new LanguageManager(this);
        languageManager.loadLanguage();
        //Первая логика
        itemDrops = new ItemDrops(this);
        databaseManager = new DatabaseManager(pluginFolder, itemDrops, this);
        //редактор столов и активатор столов
        manager = new TableEditorManager(this, pluginFolder);
        editorGUIs = new EditorGUIs(manager);
        tableGUI = new TableGUI(this, manager, databaseManager);
        //листенеры
        tableEditorListener = new TableEditorListener(manager, editorGUIs, tableGUI, this);
        getServer().getPluginManager().registerEvents(tableEditorListener, this);
        blockBreakListener = new BlockBreakListener(databaseManager, tableGUI);
        getServer().getPluginManager().registerEvents(blockBreakListener, this);
        //Команда
        guiTableCommand = new GuiTableCommand(this, manager, editorGUIs, tableGUI, pluginFolder);
        tableTabCompleter = new TableTabCompleter(pluginFolder);
        Objects.requireNonNull(getCommand("gui")).setExecutor(guiTableCommand);
        Objects.requireNonNull(getCommand("gui")).setTabCompleter(tableTabCompleter);
        //#################################################################################################//
        //                     тут обработку самих столов и сделай её лучше чем раньше                     //
        //#################################################################################################//
        blockClickListener = new BlockClickListener(tableGUI, this, manager, blockBreakListener);
        getServer().getPluginManager().registerEvents(blockClickListener, this);
        customTablesListener = new CustomTablesListener(this, tableGUI, manager, databaseManager, itemDrops, pluginFolder, blockBreakListener);
        getServer().getPluginManager().registerEvents(customTablesListener, this);
        //#################################################################################################//
        // Инициализация API
        apiManager = new ApiManager(this);
        apiManager.initialize();
        CustomGuiAPI.initialize(this);
        //#################################################################################################//
        functional();
        hello();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        shutDown();
    }
    void hello() {
        //start image
        getLogger().info("---------------------------------------------------------------");
        getLogger().info("  █████ █   █ █████ █████ █████ ██   ██       █████ █   █ ███  ");
        getLogger().info("  █     █   █ █       █   █   █ █ █ █ █       █     █   █  █   ");
        getLogger().info("  █     █   █ █████   █   █   █ █  █  █       █ ███ █   █  █   ");
        getLogger().info("  █     █   █     █   █   █   █ █     █       █   █ █   █  █   ");
        getLogger().info("  █████ █████ █████   █   █████ █     █       █████ █████ ███  ");
        getLogger().info("---------------------------------------------------------------");
        getLogger().info("plugin is enabled");
    }
    void functional() {
        manager.loadAllGuis();
        manager.loadAllBlocks();
    }
    void shutDown() {
        tableGUI.closeAllInventories();
    }
    
    private void saveDefaultLanguageFiles() {
        // Создаем папку lang если её нет
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
            getLogger().info("Создана папка lang");
        }
        
        // Список файлов переводов для копирования
        String[] languageFiles = {"en.yml", "ru.yml"};
        
        for (String fileName : languageFiles) {
            File langFile = new File(langDir, fileName);
            if (!langFile.exists()) {
                saveResource("lang/" + fileName, false);
                getLogger().info("Создан файл перевода: " + fileName);
            }
        }
    }
}
