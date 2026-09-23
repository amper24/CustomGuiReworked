package dev.moonaticks.customGuiReworked.editor;

import dev.moonaticks.customGuiReworked.api.Gui;

import java.util.UUID;

/**
 * Состояние редактирования одного игрока.
 */
public class EditorSession {

    /** Активный чат-промпт (ввод заголовка / ID блока). */
    public enum Prompt {
        NONE, TITLE, BLOCK_ID, CATEGORY
    }

    private final UUID uuid;
    private Gui gui;
    private Prompt prompt = Prompt.NONE;
    private int categoryPage;

    public EditorSession(UUID uuid, Gui gui) {
        this.uuid = uuid;
        this.gui = gui;
    }

    public UUID uuid() {
        return uuid;
    }

    public Gui gui() {
        return gui;
    }

    public void gui(Gui gui) {
        this.gui = gui;
    }

    public Prompt prompt() {
        return prompt;
    }

    public void prompt(Prompt prompt) {
        this.prompt = prompt == null ? Prompt.NONE : prompt;
    }

    public int categoryPage() {
        return categoryPage;
    }

    public void categoryPage(int page) {
        categoryPage = Math.max(0, page);
    }
}
