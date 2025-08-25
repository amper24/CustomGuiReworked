package dev.moonaticks.customGuiReworked.managers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.tools.MessageFormatter;

public class LanguageManager {
    CustomGuiReworked plugin;
    
    public LanguageManager(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }
    
    public Map<String, String> getLanguageMap() {
        Map<String, String> languageMap = new HashMap<>();
        
        try {
            // Проверяем существование config.yml
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                plugin.getLogger().warning("Файл config.yml не найден! Создаем стандартный...");
                createDefaultConfig();
            }
            
            // Загружаем config.yml
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String language = config.getString("language");
            
            if (language == null) {
                plugin.getLogger().warning("Язык не указан в config.yml, используем 'en'");
                language = "en";
            }
            
            // Проверяем существование файла языка
            File langFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
            if (!langFile.exists()) {
                plugin.getLogger().warning("Файл языка '" + language + ".yml' не найден! Используем английский...");
                language = "en";
                langFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
                
                if (!langFile.exists()) {
                    plugin.getLogger().severe("Файл языка 'en.yml' не найден! Создаем стандартный...");
                    createDefaultLanguageFile();
                }
            }
            
            // Загружаем файл языка
            YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            
            if (langConfig.contains("language")) {
                for (String key : langConfig.getConfigurationSection("language").getKeys(false)) {
                    String value = langConfig.getString("language." + key);
                    if (value != null) {
                        // Сохраняем сообщение как есть (с § кодами) для Adventure API
                        languageMap.put(key, value);
                    }
                }
                plugin.getLogger().info("Загружено " + languageMap.size() + " переводов из файла " + language + ".yml");
            } else {
                plugin.getLogger().warning("Секция 'language' не найдена в файле " + language + ".yml");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при загрузке переводов: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Добавляем fallback значения, если переводы не загружены
        if (languageMap.isEmpty()) {
            plugin.getLogger().warning("Переводы не загружены, используем fallback значения");
            addFallbackTranslations(languageMap);
        }
        
        return languageMap;
    }
    
    private void createDefaultConfig() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("language", "en");
            config.save(new File(plugin.getDataFolder(), "config.yml"));
            plugin.getLogger().info("Создан стандартный config.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось создать config.yml: " + e.getMessage());
        }
    }

    private void createDefaultLanguageFile() {
        try {
            // Создаем папку lang если её нет
            File langDir = new File(plugin.getDataFolder(), "lang");
            if (!langDir.exists()) {
                langDir.mkdirs();
            }
            
            File langFile = new File(langDir, "en.yml");
            
            // Проверяем, не существует ли уже файл из ресурсов
            if (!langFile.exists()) {
                // Пытаемся скопировать из ресурсов
                plugin.saveResource("lang/en.yml", false);
                plugin.getLogger().info("Скопирован файл перевода en.yml из ресурсов");
            } else {
                // Если файл существует, создаем минимальный fallback
                YamlConfiguration config = new YamlConfiguration();
                
                // Добавляем базовые переводы с § кодами
                config.set("language.error", "§c§lERROR");
                config.set("language.mainEditorTitle", "§8[ⒼⓊⓘ ⒺⒹⓘⓉⓄⓇ]: §f");
                config.set("language.PlayersOnly", "§cThis command can only be run by players.");
                config.set("language.CommandError", "§cUse: §f/gui [create/open/delete/edit/reload]");
                config.set("language.NoPermission", "§cYou don't have permission to use this command");
                
                config.save(langFile);
                plugin.getLogger().info("Создан стандартный файл переводов en.yml");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось создать файл переводов: " + e.getMessage());
        }
    }
    
    private void addFallbackTranslations(Map<String, String> languageMap) {
        // Базовые fallback переводы с § кодами
        languageMap.put("error", "§c§lERROR");
        languageMap.put("mainEditorTitle", "§8[ⒼⓊⓘ ⒺⒹⓘⓉⓄⓇ]: §f");
        languageMap.put("PlayersOnly", "§cЭта команда может быть выполнена только игроками.");
        languageMap.put("CommandError", "§cИспользуйте: §f/gui [create/open/delete/edit/reload]");
        languageMap.put("NoPermission", "§cУ вас нет прав для использования этой команды");
        languageMap.put("InterfaceNotFound", "§cИнтерфейс не найден!");
        languageMap.put("SlotsError", "§cСлот должен быть числом!");
        languageMap.put("DelayError", "§cЗадержка должна быть числом!");
        languageMap.put("IndexError", "§cИндекс должен быть числом!");
        languageMap.put("CommandAddSuccess", "§aКоманда добавлена в слот: §f%s");
        languageMap.put("CommandAddError", "§cИспользуйте: §f/gui command add <слот> <имя_интерфейса> \"<команда>\"");
        languageMap.put("CommandDeleteSuccess", "§aКоманда удалена из слота: §f%s");
        languageMap.put("CommandDeleteError", "§cИспользуйте: §f/gui command delete <слот> <имя_интерфейса> <индекс>");
        languageMap.put("CommandNotFound", "§cКоманда в слоте §f%s §aдля интерфейса §f%s §cне найдена!");
        languageMap.put("CommandListHeader", "§e################################");
        languageMap.put("CommandListTitle", "§aСписок команд для таблицы §f%s §aдля слота §f%s");
        languageMap.put("CommandListFormat", "§e%s §f- %s");
        languageMap.put("Delay", "§6Задержка: §f%s");
        languageMap.put("CommandListFooter", "§e################################");
        languageMap.put("CommandValue", "§aКоманда в слоте §f%s §aдля интерфейса §f%s §aимеет значение: §f%s");
        languageMap.put("CommandSetCommandError", "§cИспользуйте: §f/gui command [add/get/delete] <слот> <имя_интерфейса> \"<команда>\"");
        languageMap.put("WrongUsage", "§cНеверное использование, используйте: §f/gui [create/open/delete/edit/reload/command]");
        languageMap.put("CommandCreateCommandError", "§cИспользуйте: §f/gui create <имя_интерфейса>");
        languageMap.put("CommandEditCommandError", "§cИспользуйте: §f/gui edit <имя_интерфейса>");
        languageMap.put("CommandDeleteCommandError", "§cИспользуйте: §f/gui delete <имя_интерфейса> [confirm]");
        languageMap.put("ErrorSet", "§cИспользуйте: §f/gui edit <имя_интерфейса>");
        languageMap.put("ErrorOpen", "§cИнтерфейс не найден!");
        languageMap.put("DeleteSuccess", "§aИнтерфейс §f%s §aбыл удален!");
        languageMap.put("ReloadSuccess", "§aПереводы перезагружены успешно!");
        
        plugin.getLogger().info("Добавлено " + languageMap.size() + " fallback переводов");
    }
    
    public void loadLanguage() {
        plugin.getLogger().info("Загружаем переводы...");
        CustomGuiReworked.languageMap = getLanguageMap();
        plugin.getLogger().info("Переводы загружены успешно! Загружено " + CustomGuiReworked.languageMap.size() + " ключей");
    }
    
    public void reloadLanguage() {
        plugin.getLogger().info("Перезагружаем переводы...");
        loadLanguage();
        plugin.getLogger().info("Переводы перезагружены успешно!");
    }
}
