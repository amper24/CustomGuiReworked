package dev.moonaticks.customGuiReworked.lang;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки встроенных (bundled) файлов языка.
 *
 * <p>Заголовки меню и подписи кнопок должны состоять из обычных символов:
 * клиентский шрифт рисует «ⒼⓊⓘ», «▶» и подобные глифы непредсказуемо
 * (часто квадратиками), поэтому в значениях допустимы только буквы,
 * цифры, обычная пунктуация и legacy-код {@code §}.
 *
 * <p>Дополнительно проверяется, что наборы ключей en/ru совпадают: иначе
 * часть строк выпадает в fallback и интерфейс становится смешанным.
 */
class LangResourceTest {

    private static final String[] FILES = {"lang/en.yml", "lang/ru.yml"};
    private static final int MIN_LANG_VERSION = 2;

    @Test
    @DisplayName("bundled lang files: only plain characters in values")
    void valuesHaveNoDecorativeGlyphs() {
        for (String file : FILES) {
            ConfigurationSection section = section(file);
            for (String key : section.getKeys(true)) {
                String value = section.getString(key);
                if (value == null) {
                    continue;
                }
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    assertFalse(isDecorative(c), file + " -> " + key
                            + ": символ U+" + Integer.toHexString(c) + " ('" + c
                            + "') декоративный — используйте обычный текст");
                }
            }
        }
    }

    @Test
    @DisplayName("bundled lang files: en and ru share the same keys")
    void keySetsMatch() {
        Set<String> en = section(FILES[0]).getKeys(true);
        Set<String> ru = section(FILES[1]).getKeys(true);
        Set<String> onlyEn = new LinkedHashSet<>(en);
        onlyEn.removeAll(ru);
        Set<String> onlyRu = new LinkedHashSet<>(ru);
        onlyRu.removeAll(en);
        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(),
                "расходятся ключи: только в en " + onlyEn + ", только в ru " + onlyRu);
    }

    @Test
    @DisplayName("bundled lang files: lang-version allows the disk copy to be refreshed")
    void langVersionIsPresent() {
        for (String file : FILES) {
            int version = config(file).getInt("lang-version", 1);
            assertTrue(version >= MIN_LANG_VERSION,
                    file + ": lang-version = " + version + ", ожидается >= " + MIN_LANG_VERSION);
        }
    }

    private static ConfigurationSection section(String file) {
        ConfigurationSection section = config(file).getConfigurationSection("language");
        assertNotNull(section, file + ": нет секции language");
        return section;
    }

    private static YamlConfiguration config(String file) {
        try (InputStream in = LangResourceTest.class.getClassLoader().getResourceAsStream(file)) {
            assertNotNull(in, "ресурс не найден: " + file);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("не читается " + file, e);
        }
    }

    /**
     * Декоративные диапазоны: стрелки, обведённые буквы/цифры, геометрические
     * фигуры, символы и дингбаты, вариационные селекторы, приватная зона
     * (кастомные глифы ресурспаков).
     */
    private static boolean isDecorative(char c) {
        return (c >= 0x2190 && c <= 0x2BFF)
                || (c >= 0xE000 && c <= 0xF8FF)
                || (c >= 0xFE00 && c <= 0xFE0F);
    }
}
