package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;

/**
 * Holder для открытых инвентарей GUI.
 *
 * <p>Через него из любого события однозначно определяется,
 * к какому GUI и к какому хранилищу относится инвентарь —
 * без обратных карт и парсинга ключей (устранив старые
 * «reverseLookupMap» и «lastGui»).
 *
 * <p>Дополнительно хранит {@code baseline} — закодированные предметы
 * сохраняемых слотов на момент открытия/последней записи. Реконсиляция
 * после клика сравнивает текущее содержимое с baseline и пишет в хранилище
 * только реально изменившиеся слоты, не затирая данные других сессий.
 */
public class GuiHolder implements InventoryHolder {

    /** Типы слотов, содержимое которых персистится (DESIGN — нет, RESULT — нет). */
    public static boolean isPersistable(SlotType type) {
        return type == SlotType.CONTAINER || type == SlotType.CRAFT || type == SlotType.FUEL;
    }

    private final Gui gui;
    private final StorageKey key;
    private Inventory inventory;
    private String[] baseline;
    /** Запланирована ли реконсиляция на следующий тик (коалесинг серии кликов). */
    private boolean reconcileQueued;
    /** Задача отложенной реконсиляции (для отмены при закрытии). */
    private BukkitTask reconcileTask;
    /** Слоты-кандидаты на проверку при следующей реконсиляции. */
    private final Set<Integer> candidates = new java.util.HashSet<>();
    private boolean allCandidates;

    public GuiHolder(Gui gui, StorageKey key) {
        this.gui = gui;
        this.key = key;
    }

    public Gui gui() {
        return gui;
    }

    public StorageKey key() {
        return key;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ================= baseline / реконсиляция =================

    /** Снимок персистентных слотов (индекс = слот, null для не-персистентных). */
    void initBaseline(String[] encoded) {
        this.baseline = encoded;
    }

    public String[] baseline() {
        return baseline;
    }

    /** Добавляет слот в кандидаты на реконсиляцию. */
    public synchronized void addCandidate(int slot) {
        candidates.add(slot);
    }

    /** Реконсиляция должна проверить все персистентные слоты. */
    public synchronized void addAllCandidates() {
        allCandidates = true;
        candidates.clear();
    }

    /** Снимает и возвращает состояние очереди кандидатов (атомарно). */
    public synchronized java.util.Collection<Integer> drainCandidates() {
        java.util.Collection<Integer> result = allCandidates ? null : new java.util.ArrayList<>(candidates);
        allCandidates = false;
        candidates.clear();
        return result;
    }

    public boolean isReconcileQueued() {
        return reconcileQueued;
    }

    public void setReconcileQueued(boolean queued) {
        this.reconcileQueued = queued;
    }

    public BukkitTask reconcileTask() {
        return reconcileTask;
    }

    public void reconcileTask(BukkitTask task) {
        this.reconcileTask = task;
    }
}
