package dev.moonaticks.customGuiReworked.manager;

import java.util.UUID;

/**
 * Состояние меню управления одного игрока.
 */
public class ManagerSession {

    /** Владелец сессии (UUID игрока). */
    private final UUID owner;
    /** Страница списка (0-based). */
    private int page;
    /** Поиск по имени (null или пусто — без фильтра). */
    private String search;
    /** Ждём текст в чат (поиск / имя нового GUI) — меню при этом закрыто. */
    public enum InputMode { NONE, CREATE, SEARCH }
    private InputMode inputMode = InputMode.NONE;
    /** GUI, удаление которого «заряжено» (двойной клик для подтверждения). */
    private String armedDelete;
    /** Когда «зарядка» удаления сработает (System.currentTimeMillis). */
    private long armedUntil;

    public ManagerSession(UUID owner) {
        this.owner = owner;
    }

    /** UUID игрока, которому принадлежит сессия. */
    public UUID owner() {
        return owner;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }

    public String search() {
        return search;
    }

    public void search(String search) {
        this.search = (search == null || search.isBlank()) ? null : search.trim();
    }

    public InputMode inputMode() {
        return inputMode;
    }

    public void inputMode(InputMode mode) {
        this.inputMode = mode == null ? InputMode.NONE : mode;
    }

    public String armedDelete() {
        return armedDelete;
    }

    public void armDelete(String guiName, long until) {
        this.armedDelete = guiName;
        this.armedUntil = until;
    }

    public long armedUntil() {
        return armedUntil;
    }

    /** Активна ли «зарядка» удаления именно для этого GUI. */
    public boolean isArmed(String guiName, long now) {
        return guiName != null && guiName.equals(armedDelete) && now < armedUntil;
    }

    public void disarm() {
        this.armedDelete = null;
        this.armedUntil = 0;
    }
}
