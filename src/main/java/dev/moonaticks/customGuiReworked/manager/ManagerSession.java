package dev.moonaticks.customGuiReworked.manager;

/**
 * Состояние меню управления одного игрока.
 */
public class ManagerSession {

    /** Страница списка (0-based). */
    private int page;
    /** Поиск по имени (null или пусто — без фильтра). */
    private String search;
    /** Ждём имя нового GUI в чате. */
    private boolean creating;
    /** GUI, удаление которого «заряжено» (двойной клик для подтверждения). */
    private String armedDelete;
    /** Когда «зарядка» удаления сработает (System.currentTimeMillis). */
    private long armedUntil;

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

    public boolean isCreating() {
        return creating;
    }

    public void creating(boolean creating) {
        this.creating = creating;
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
