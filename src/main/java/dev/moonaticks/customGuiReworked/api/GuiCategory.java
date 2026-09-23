package dev.moonaticks.customGuiReworked.api;

import org.bukkit.Material;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Описание категории для меню GUI. Сам GUI хранит только {@link #id()},
 * поэтому категории можно регистрировать после загрузки файлов GUI.
 * Незарегистрированные категории продолжают отображаться по ID.
 */
public record GuiCategory(String id, String displayName, Material icon, String description) {

    public static final String NONE = "none";
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+(?::[a-z0-9_.-]+)?");

    public GuiCategory {
        id = normalizeId(id);
        if (NONE.equals(id)) {
            throw new IllegalArgumentException("'none' is reserved for GUIs without a category");
        }
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        icon = icon == null || !icon.isItem() ? Material.BOOK : icon;
        description = description == null ? "" : description;
    }

    public GuiCategory(String id, String displayName) {
        this(id, displayName, Material.BOOK, "");
    }

    /** Нормализует ID; null/пустая строка означает отсутствие категории. */
    public static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid category ID: " + raw);
        }
        return id;
    }
}
