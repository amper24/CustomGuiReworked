package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.GuiCategory;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Реестр описаний категорий. Привязки GUI хранятся отдельно, в их файлах. */
public class CategoryRegistry {

    private final File file;
    private final Logger logger;
    private final Map<String, GuiCategory> persistent = new LinkedHashMap<>();
    private final Map<String, GuiCategory> runtime = new LinkedHashMap<>();

    public CategoryRegistry(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "categories.yml");
        this.logger = logger;
    }

    /** Перечитывает диск, не удаляя описания категорий только в памяти. */
    public synchronized void loadAll() {
        persistent.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> entry : config.getMapList("categories")) {
            try {
                Object rawId = entry.get("id");
                if (rawId == null || rawId.toString().isBlank()) {
                    continue;
                }
                String id = rawId.toString();
                String name = entry.get("name") == null ? id : entry.get("name").toString();
                String description = entry.get("description") == null ? "" : entry.get("description").toString();
                String iconId = entry.get("icon") == null ? "BOOK" : entry.get("icon").toString();
                Material icon = Material.matchMaterial(iconId);
                GuiCategory category = new GuiCategory(id, name, icon, description);
                persistent.put(category.id(), category);
            } catch (IllegalArgumentException e) {
                if (logger != null) {
                    logger.warning("Skipping invalid category in categories.yml: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Регистрирует описание категории. persist=true сохраняет его в categories.yml;
     * false оставляет только до перезапуска. Повторная регистрация обновляет описание.
     */
    public synchronized GuiCategory register(GuiCategory category, boolean persist) {
        if (category == null) {
            throw new IllegalArgumentException("category cannot be null");
        }
        if (persist) {
            runtime.remove(category.id());
            persistent.put(category.id(), category);
            save();
        } else {
            boolean removedPersistent = persistent.remove(category.id()) != null;
            runtime.put(category.id(), category);
            if (removedPersistent) {
                save();
            }
        }
        return category;
    }

    /** Удаляет описание, не меняя category у GUI (ID остаётся видимым). */
    public synchronized boolean unregister(String id) {
        String key = GuiCategory.normalizeId(id);
        boolean removedPersistent = persistent.remove(key) != null;
        boolean removedRuntime = runtime.remove(key) != null;
        if (removedPersistent) {
            save();
        }
        return removedPersistent || removedRuntime;
    }

    public synchronized GuiCategory get(String id) {
        String key = GuiCategory.normalizeId(id);
        GuiCategory category = runtime.get(key);
        return category == null ? persistent.get(key) : category;
    }

    /** Снимок только зарегистрированных описаний; NONE — особый пункт меню. */
    public synchronized List<GuiCategory> all() {
        Map<String, GuiCategory> merged = new LinkedHashMap<>(persistent);
        merged.putAll(runtime);
        return List.copyOf(merged.values());
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (GuiCategory category : persistent.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", category.id());
            entry.put("name", category.displayName());
            entry.put("icon", category.icon().name());
            entry.put("description", category.description());
            entries.add(entry);
        }
        config.set("categories", entries);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create folder " + parent);
            }
            Path temporary = file.toPath().resolveSibling(file.getName() + ".tmp-" + UUID.randomUUID());
            Files.writeString(temporary, config.saveToString(), StandardCharsets.UTF_8);
            try {
                try {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Could not save categories.yml: " + e.getMessage());
            }
        }
    }
}
