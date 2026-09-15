package dev.moonaticks.customGuiReworked.api;

/**
 * Команда, привязанная к слоту GUI.
 *
 * <p>Поддерживаются плейсхолдеры:
 * <ul>
 *   <li>{@code %player%} — имя игрока, кликнувшего по слоту;</li>
 *   <li>{@code %slot%} — индекс слота.</li>
 * </ul>
 *
 * @param slot    индекс слота (0-based)
 * @param command команда (с начальным «/» или без — оба варианта работают)
 * @param delay   задержка выполнения в тиках (0 = немедленно)
 */
public record SlotCommand(int slot, String command, int delay) {

    public SlotCommand {
        if (command == null) {
            command = "";
        }
        if (delay < 0) {
            delay = 0;
        }
    }
}
