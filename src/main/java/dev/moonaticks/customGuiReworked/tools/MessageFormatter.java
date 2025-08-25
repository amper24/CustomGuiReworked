package dev.moonaticks.customGuiReworked.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Утилитный класс для форматирования сообщений с полной поддержкой KyoriAPI (Adventure API)
 */
public class MessageFormatter {
    
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();
    
    private static final LegacyComponentSerializer LEGACY_AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    
    /**
     * Форматирует строку с цветовыми кодами § в Component для Adventure API
     * @param message исходное сообщение с кодами §
     * @return Component с форматированием
     */
    public static Component formatComponent(String message) {
        if (message == null) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(message);
    }
    
    /**
     * Форматирует строку с цветовыми кодами & в Component для Adventure API
     * @param message исходное сообщение с кодами &
     * @return Component с форматированием
     */
    public static Component formatComponentFromAmpersand(String message) {
        if (message == null) return Component.empty();
        return LEGACY_AMPERSAND_SERIALIZER.deserialize(message);
    }
    
    /**
     * Форматирует сообщение с подстановкой параметров в Component
     * @param message исходное сообщение с плейсхолдерами %s
     * @param args аргументы для подстановки
     * @return Component с форматированием
     */
    public static Component formatComponent(String message, Object... args) {
        if (message == null) return Component.empty();
        String formatted = String.format(message, args);
        return LEGACY_SERIALIZER.deserialize(formatted);
    }
    
    /**
     * Создает Component с указанным цветом
     * @param text текст
     * @param color цвет
     * @return Component с цветом
     */
    public static Component colored(String text, NamedTextColor color) {
        return Component.text(text, color);
    }
    
    /**
     * Создает Component с указанным hex цветом
     * @param text текст
     * @param hexColor hex цвет (например, "#ff0000")
     * @return Component с hex цветом
     */
    public static Component colored(String text, String hexColor) {
        TextColor color = TextColor.fromHexString(hexColor);
        return Component.text(text, color);
    }
    
    /**
     * Создает Component с жирным шрифтом
     * @param text текст
     * @return Component с жирным шрифтом
     */
    public static Component bold(String text) {
        return Component.text(text, Style.style(TextDecoration.BOLD));
    }
    
    /**
     * Создает Component с курсивом
     * @param text текст
     * @return Component с курсивом
     */
    public static Component italic(String text) {
        return Component.text(text, Style.style(TextDecoration.ITALIC));
    }
    
    /**
     * Создает Component с подчеркиванием
     * @param text текст
     * @return Component с подчеркиванием
     */
    public static Component underlined(String text) {
        return Component.text(text, Style.style(TextDecoration.UNDERLINED));
    }
    
    /**
     * Создает Component с зачеркиванием
     * @param text текст
     * @return Component с зачеркиванием
     */
    public static Component strikethrough(String text) {
        return Component.text(text, Style.style(TextDecoration.STRIKETHROUGH));
    }
    
    /**
     * Создает Component с обфускацией (случайные символы)
     * @param text текст
     * @return Component с обфускацией
     */
    public static Component obfuscated(String text) {
        return Component.text(text, Style.style(TextDecoration.OBFUSCATED));
    }
    
    /**
     * Создает Component с цветом и форматированием
     * @param text текст
     * @param color цвет
     * @param decorations декорации
     * @return Component с цветом и форматированием
     */
    public static Component formatted(String text, NamedTextColor color, TextDecoration... decorations) {
        return Component.text(text, Style.style(color, decorations));
    }
    
    /**
     * Создает Component с hex цветом и форматированием
     * @param text текст
     * @param hexColor hex цвет
     * @param decorations декорации
     * @return Component с hex цветом и форматированием
     */
    public static Component formatted(String text, String hexColor, TextDecoration... decorations) {
        TextColor color = TextColor.fromHexString(hexColor);
        return Component.text(text, Style.style(color, decorations));
    }
    
    /**
     * Создает градиентный текст (упрощенная версия)
     * @param text текст
     * @param startColor начальный цвет
     * @param endColor конечный цвет
     * @return Component с градиентом
     */
    public static Component gradient(String text, String startColor, String endColor) {
        // Создаем простой градиент, разделяя текст на части
        TextColor start = TextColor.fromHexString(startColor);
        TextColor end = TextColor.fromHexString(endColor);
        
        if (text.length() <= 1) {
            return Component.text(text, start);
        }
        
        TextComponent.Builder builder = Component.text();
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            // Простая интерполяция между цветами
            double ratio = (double) i / (length - 1);
            int r = (int) (start.red() + (end.red() - start.red()) * ratio);
            int g = (int) (start.green() + (end.green() - start.green()) * ratio);
            int b = (int) (start.blue() + (end.blue() - start.blue()) * ratio);
            
            TextColor interpolatedColor = TextColor.color(r, g, b);
            builder.append(Component.text(String.valueOf(c), interpolatedColor));
        }
        
        return builder.build();
    }
    
    /**
     * Создает текст с переходом между цветами (упрощенная версия)
     * @param text текст
     * @param colors массив цветов
     * @return Component с переходами цветов
     */
    public static Component rainbow(String text, String... colors) {
        if (colors.length == 0 || text.isEmpty()) {
            return Component.text(text);
        }
        
        TextComponent.Builder builder = Component.text();
        int length = text.length();
        int colorCount = colors.length;
        
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            int colorIndex = (i * colorCount) / length;
            TextColor color = TextColor.fromHexString(colors[colorIndex]);
            builder.append(Component.text(String.valueOf(c), color));
        }
        
        return builder.build();
    }
    
    /**
     * Создает Component с кликабельной ссылкой
     * @param text текст
     * @param url URL
     * @return Component с кликабельной ссылкой
     */
    public static Component clickableUrl(String text, String url) {
        return Component.text(text).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url));
    }
    
    /**
     * Создает Component с командой при клике
     * @param text текст
     * @param command команда
     * @return Component с командой при клике
     */
    public static Component clickableCommand(String text, String command) {
        return Component.text(text).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command));
    }
    
    /**
     * Создает Component с подсказкой при наведении
     * @param text текст
     * @param tooltip подсказка
     * @return Component с подсказкой
     */
    public static Component withTooltip(String text, String tooltip) {
        return Component.text(text).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(tooltip)));
    }
    
    /**
     * Создает Component с подсказкой при наведении (Component)
     * @param text текст
     * @param tooltip подсказка как Component
     * @return Component с подсказкой
     */
    public static Component withTooltip(String text, Component tooltip) {
        return Component.text(text).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(tooltip));
    }
    
    /**
     * Форматирует строку с цветовыми кодами § в строку с Bukkit цветовыми кодами
     * @param message исходное сообщение с кодами §
     * @return отформатированное сообщение
     */
    public static String format(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('§', message);
    }
    
    /**
     * Форматирует строку с цветовыми кодами & в строку с Bukkit цветовыми кодами
     * @param message исходное сообщение с кодами &
     * @return отформатированное сообщение
     */
    public static String formatFromAmpersand(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Форматирует сообщение с подстановкой параметров
     * @param message исходное сообщение с плейсхолдерами %s
     * @param args аргументы для подстановки
     * @return отформатированное сообщение
     */
    public static String format(String message, Object... args) {
        if (message == null) return "";
        String formatted = String.format(message, args);
        return ChatColor.translateAlternateColorCodes('§', formatted);
    }
    
    /**
     * Отправляет сообщение игроку с поддержкой Adventure API
     * @param player игрок
     * @param message сообщение с кодами §
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null) return;
        player.sendMessage(formatComponent(message));
    }
    
    /**
     * Отправляет сообщение игроку с подстановкой параметров
     * @param player игрок
     * @param message сообщение с плейсхолдерами %s
     * @param args аргументы для подстановки
     */
    public static void sendMessage(Player player, String message, Object... args) {
        if (player == null || message == null) return;
        player.sendMessage(formatComponent(message, args));
    }
    
    /**
     * Отправляет Component игроку
     * @param player игрок
     * @param component компонент
     */
    public static void sendMessage(Player player, Component component) {
        if (player == null || component == null) return;
        player.sendMessage(component);
    }
    
    /**
     * Отправляет сообщение CommandSender с поддержкой Adventure API
     * @param sender отправитель команды
     * @param message сообщение с кодами §
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        sender.sendMessage(formatComponent(message));
    }
    
    /**
     * Отправляет сообщение CommandSender с подстановкой параметров
     * @param sender отправитель команды
     * @param message сообщение с плейсхолдерами %s
     * @param args аргументы для подстановки
     */
    public static void sendMessage(CommandSender sender, String message, Object... args) {
        if (sender == null || message == null) return;
        sender.sendMessage(formatComponent(message, args));
    }
    
    /**
     * Отправляет Component CommandSender
     * @param sender отправитель команды
     * @param component компонент
     */
    public static void sendMessage(CommandSender sender, Component component) {
        if (sender == null || component == null) return;
        sender.sendMessage(component);
    }
    
    /**
     * Конвертирует Component в обычную строку (без форматирования)
     * @param component компонент
     * @return строка без форматирования
     */
    public static String componentToString(Component component) {
        if (component == null) return "";
        return PLAIN_SERIALIZER.serialize(component);
    }
    
    /**
     * Создает Component из строки с поддержкой hex цветов
     * @param message сообщение с hex кодами (например, &#ff0000)
     * @return Component с hex цветами
     */
    public static Component formatComponentWithHex(String message) {
        if (message == null) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(message);
    }
    
    /**
     * Создает заголовок с красивым форматированием
     * @param title заголовок
     * @return Component с заголовком
     */
    public static Component createHeader(String title) {
        return Component.text("§8[§6" + title + "§8]§f ");
    }
    
    /**
     * Создает разделитель
     * @param length длина разделителя
     * @return Component с разделителем
     */
    public static Component createSeparator(int length) {
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < length; i++) {
            separator.append("§8-");
        }
        return formatComponent(separator.toString());
    }
    
    /**
     * Создает рамку вокруг текста
     * @param text текст
     * @return Component с рамкой
     */
    public static Component createBox(String text) {
        return Component.text()
                .append(formatComponent("§8┌─"))
                .append(formatComponent("§6" + text))
                .append(formatComponent("§8─┐"))
                .build();
    }
}
