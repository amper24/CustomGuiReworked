package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
 *
 * <p><b>Локальные оверрайды (per-viewer).</b> Каждая открытая сессия —
 * отдельный holder, поэтому «локальное» здесь означает «для этого
 * конкретного открытого инвентаря» и не влияет на файл GUI, других
 * игроков или другие блоки:
 * <ul>
 *   <li>{@code localDesignOverride} — предметы, показываемые вместо
 *       файла в DESIGN/RESULT и совместимых кастомных слотах (O(1)-lookup);</li>
 *   <li>{@code localTitleOverride} — название окна, подменяющее
 *       {@link Gui#title()};</li>
 *   <li>{@code blockLocation} — локация блока для сессий с
 *       {@link dev.moonaticks.customGuiReworked.api.StorageType#BLOCK}-хранилищем.</li>
 * </ul>
 *
 * <p>Весь holder живёт на основном потоке (Bukkit), синхронизация не нужна.
 */
public class GuiHolder implements InventoryHolder {

    /** Типы слотов, содержимое которых персистится (встроенные и зарегистрированные). */
    public static boolean isPersistable(SlotType type) {
        return type != null && type.isPersistable();
    }

    /**
     * Типы слотов, чьё содержимое отслеживается для
     * {@link dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent}:
     * персистентные слоты + RESULT (изменения в них — действия игрока,
     * хотя и не персистятся). DESIGN не отслеживается.
     */
    public static boolean isTracked(SlotType type) {
        return type != null && type.isTracked();
    }

    /** Типы слотов, допустимые для локальных дизайн-оверрайдов. */
    private static boolean allowsLocalDesign(SlotType type) {
        return type != null && type.allowsLocalDesign();
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

    /** Локальные (per-viewer) оверрайды: слот → предмет (типы с localDesign). */
    private final Map<Integer, ItemStack> localDesignOverride = new HashMap<>();
    /** Локальное (per-viewer) название окна; null — использовать {@link Gui#title()}. */
    private String localTitleOverride;
    /** Локация блока для BLOCK-сессий; null для остальных типов. */
    private Location blockLocation;
    /** Игрок, для которого открыта сессия (для событий изменения слотов). */
    private UUID player;

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

    /** Игрок, для которого открыта сессия (запоминается при открытии). */
    public UUID player() {
        return player;
    }

    /** Запоминает игрока сессии (вызывается при открытии). */
    public void setPlayer(UUID player) {
        this.player = player;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ================= локальные оверрайды (per-viewer) =================

    /**
     * Локальный предмет для слота, либо null — если оверрайда нет
     * (показывается дизайн из файла).
     *
     * @param slot индекс слота (может быть вне диапазона — вернёт null)
     * @return ItemStack или null; O(1)
     */
    public ItemStack getLocalDesign(int slot) {
        if (slot < 0) {
            return null;
        }
        return localDesignOverride.get(slot);
    }

    /**
     * Устанавливает локальный предмет в слот (per-viewer, файл GUI не меняется).
     *
     * <p>Допустимы DESIGN/RESULT и кастомные неперсистентные типы с
     * {@code localDesign(true)}: оверрайды для
     * CONTAINER/CRAFT/FUEL запрещены, потому что их содержимое
     * персистится в хранилище (виртуальный предмет стал бы реальным
     * и мог быть вынесен из GUI — риск дюпа).
     *
     * @param slot индекс слота
     * @param item предмет; null (или AIR) — сбрасывает оверрайд
     * @throws IndexOutOfBoundsException если слот вне диапазона GUI
     * @throws IllegalArgumentException  если тип слота не поддерживает localDesign
     */
    public void setLocalDesign(int slot, ItemStack item) {
        checkLocalDesignSlot(slot);
        if (item == null || item.getType() == Material.AIR) {
            localDesignOverride.remove(slot);
        } else {
            localDesignOverride.put(slot, item);
        }
    }

    /** Сбрасывает локальный оверрайд одного слота (см. {@link #setLocalDesign}). */
    public void clearLocalDesign(int slot) {
        localDesignOverride.remove(slot);
    }

    /** Сбрасывает все локальные дизайн-оверрайды сессии. */
    public void clearAllLocalDesigns() {
        localDesignOverride.clear();
    }

    /** Непомutable-копия всех оверрайдов (для служебных иттераций). */
    public Map<Integer, ItemStack> localDesigns() {
        return Map.copyOf(localDesignOverride);
    }

    /**
     * Локальное название окна (per-viewer), либо null —
     * использовать {@link Gui#title()}.
     */
    public String getLocalTitle() {
        return localTitleOverride;
    }

    /**
     * Устанавливает локальное название окна; null или пустая строка —
     * вернуться к названию из файла GUI.
     */
    public void setLocalTitle(String title) {
        this.localTitleOverride = (title == null || title.isEmpty()) ? null : title;
    }

    /** Сбрасывает локальное название окна (см. {@link #setLocalTitle}). */
    public void clearLocalTitle() {
        this.localTitleOverride = null;
    }

    /**
     * Локация блока, на котором открыта эта сессия
     * (для {@link dev.moonaticks.customGuiReworked.api.StorageType#BLOCK}), либо null.
     */
    public Location blockLocation() {
        return blockLocation;
    }

    /** Запоминает локацию блока (вызывается при открытии BLOCK-сессии). */
    public void setBlockLocation(Location location) {
        this.blockLocation = location;
    }

    /**
     * Полностью очищает пер-вьювер состояние сессии.
     * Вызывается при закрытии GUI: сессия завершена, оверрайды
     * больше никуда не нужны.
     */
    public void clearLocalState() {
        localDesignOverride.clear();
        localTitleOverride = null;
        blockLocation = null;
    }

    private void checkLocalDesignSlot(int slot) {
        if (slot < 0 || slot >= gui.slots()) {
            throw new IndexOutOfBoundsException("slot " + slot + " out of bounds for " + gui.slots());
        }
        SlotType type = gui.slotType(slot);
        if (!allowsLocalDesign(type)) {
            throw new IllegalArgumentException(
                    "local design overrides are unsupported for slot " + slot + " (type " + type + ")");
        }
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
