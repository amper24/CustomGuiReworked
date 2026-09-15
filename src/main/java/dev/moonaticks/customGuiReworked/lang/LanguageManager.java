package dev.moonaticks.customGuiReworked.lang;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Управление языками: загрузка файлов lang/*.yml (каталог в секции
 * «language»), fallback на английский, форматирование и вывод
 * через Adventure Component (без статической languageMap старой версии).
 */
public class LanguageManager {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final CustomGuiReworked plugin;
    private final Map<String, String> enMessages = new HashMap<>();
    /** Значения из jar — последний резерв для ключей, которых нет на диске. */
    private final Map<String, String> bundledMessages = new HashMap<>();
    private Map<String, String> messages = new HashMap<>();
    private String language = "en";

    public LanguageManager(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    /** Активный код языка. */
    public String language() {
        return language;
    }

    /** (Пере)загружает язык из config.yml и файлов lang/. */
    public void load() {
        enMessages.clear();
        bundledMessages.clear();
        // Резерв из jar: файлы на диске создаются один раз и не обновляются,
        // поэтому новые ключи иначе показывались бы как «editor.someKey».
        loadBundledDefaults();
        Map<String, String> current = new HashMap<>();
        loadFile(new File(plugin.getDataFolder(), "lang/en.yml"), enMessages);

        String requested = plugin.getConfig().getString("language", "en");
        language = "en".equals(requested)
                ? "en"
                : (new File(plugin.getDataFolder(), "lang/" + requested + ".yml").exists()
                        ? requested
                        : "en");
        if (!"en".equals(language)) {
            loadFile(new File(plugin.getDataFolder(), "lang/" + language + ".yml"), current);
        }
        if (current.isEmpty()) {
            current.putAll(enMessages);
        }
        this.messages = current;
    }

    /** Читает lang/en.yml прямо из ресурсов плагина (в обход файла на диске). */
    private void loadBundledDefaults() {
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            var section = config.getConfigurationSection("language");
            if (section == null) {
                return;
            }
            for (String key : section.getKeys(true)) {
                String value = config.getString("language." + key);
                if (value != null) {
                    bundledMessages.put(key, value);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled lang/en.yml: " + e.getMessage());
        }
    }

    private void loadFile(File file, Map<String, String> target) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        var section = config.getConfigurationSection("language");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(true)) {
            String value = config.getString("language." + key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    /**
     * Сырое сообщение с кодами § (fallback: файл языка → en на диске →
     * en из jar → сам ключ).
     */
    public String raw(String key) {
        String value = messages.get(key);
        if (value == null) {
            value = enMessages.get(key);
        }
        if (value == null) {
            value = bundledMessages.get(key);
        }
        return value == null ? key : value;
    }

    /**
     * Сообщение как {@link Component} (Adventure) с форматированием
     * аргументов {@code %s}.
     */
    public Component msg(String key, Object... args) {
        String text = raw(key);
        if (args != null && args.length > 0) {
            try {
                text = String.format(text, args);
            } catch (Exception ignored) {
                // несовпадение плейсхолдеров — показываем как есть
            }
        }
        return SERIALIZER.deserialize(text);
    }
}
