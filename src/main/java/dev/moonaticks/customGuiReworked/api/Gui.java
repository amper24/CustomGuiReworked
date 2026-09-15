package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.codec.Codecs;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Модель интерфейса (GUI).
 *
 * <p>GUI состоит из:
 * <ul>
 *   <li><b>названия</b> — уникальное имя, без расширений; файл хранится как {@code tables/<name>.yml};</li>
 *   <li><b>заголовка</b> — строка с кодами цвета §;</li>
 *   <li><b>размера</b> — 9..54 слота (кратное 9);</li>
 *   <li><b>скелета</b> — список {@link SlotType}, по одному на каждый слот;</li>
 *   <li><b>дизайна</b> — payloadы предметов для дизайн-слотов (см. {@link dev.moonaticks.customGuiReworked.codec.Codecs});</li>
 *   <li><b>типа хранения</b> — {@link StorageType};</li>
 *   <li><b>команд</b> — список {@link SlotCommand};</li>
 *   <li><b>ID кастомных блоков</b> — привязки к блокам ItemsAdder/CraftEngine.</li>
 * </ul>
 *
 * <p>Экземпляры создаются конструктором, {@link GuiBuilder} или
 * {@link GuiService#createGui(String)} и редактируются на месте;
 * изменения сохраняются в файл через {@link GuiService#saveGui(Gui)}.
 */
public class Gui {

    private static final Pattern INVALID_NAME = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final int MIN_SLOTS = 9;
    private static final int MAX_SLOTS = 54;

    /**
     * Происхождение GUI — определяет, в какой папке лежит файл
     * (для {@link #source()} без файла — только память).
     */
    public enum Source {
        /** Файл в папке {@code tables/} (создан редактором или унаследован от 1.x). */
        TABLE,
        /** Файл в папке {@code custom/} (зарегистрирован другим плагином через API). */
        CUSTOM,
        /** Только в памяти (зарегистрирован кодом без персистентности). */
        RUNTIME
    }

    private String name;
    private String title = "Custom GUI";
    private int slots = 27;
    private List<SlotType> skeleton = new ArrayList<>();
    /** По слотам: payload предмета дизайна (пустая строка = пусто). Для не-дизайн слотов всегда «». */
    private List<String> design = new ArrayList<>();
    private StorageType storage = StorageType.TEMPORARY;
    private List<SlotCommand> commands = new ArrayList<>();
    private final Set<String> blockIds = new LinkedHashSet<>();
    private Source source = Source.TABLE;

    /**
     * @param name имя GUI (с или без «.yml», с любыми символами — оно будет нормализовано)
     */
    public Gui(String name) {
        this.name = normalizeName(name);
        resetSkeleton();
        // Дизайн обязан иметь размер slots() — иначе setDesignAt/
        // setDesignItem на свежесозданном GUI бросали IndexOutOfBounds.
        resetDesign();
    }

    /** Нормализация имени: нижний регистр, только [a-zA-Z0-9_-], без «.yml». */
    public static String normalizeName(String raw) {
        if (raw == null) {
            return "gui";
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        while (n.endsWith(".yml")) {
            n = n.substring(0, n.length() - 4);
        }
        n = INVALID_NAME.matcher(n).replaceAll("_");
        return n.isEmpty() ? "gui" : n;
    }

    /** Приводит произвольное число слотов к кратному 9 в диапазоне 9..54. */
    public static int clampSlots(int value) {
        int clamped = Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, value));
        int rows = Math.max(1, Math.min(6, Math.round(clamped / 9f)));
        return rows * 9;
    }

    // ================= доступ к данным =================

    public String name() {
        return name;
    }

    /** Переименование (имя файла изменится при следующем сохранении). */
    public Gui rename(String newName) {
        this.name = normalizeName(newName);
        return this;
    }

    /** Имя файла конфигурации GUI (с расширением). */
    public String fileName() {
        return name + ".yml";
    }

    public String title() {
        return title;
    }

    public Gui title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    public int slots() {
        return slots;
    }

    /**
     * Меняет размер. Слота скелета/дизайна с меньшими индексами сохраняются,
     * новые слоты получают тип {@link SlotType#DESIGN} и пустой дизайн.
     * Команды за пределами нового размера удаляются.
     */
    public Gui slots(int newSlots) {
        int clamped = clampSlots(newSlots);
        if (clamped == this.slots) {
            return this;
        }
        int old = this.slots;
        this.slots = clamped;
        List<SlotType> oldSkeleton = this.skeleton;
        List<SlotType> newSkeleton = new ArrayList<>(clamped);
        for (int i = 0; i < clamped; i++) {
            newSkeleton.add(i < old && i < oldSkeleton.size() ? oldSkeleton.get(i) : SlotType.DESIGN);
        }
        this.skeleton = newSkeleton;
        List<String> oldDesign = this.design;
        List<String> newDesign = new ArrayList<>(clamped);
        for (int i = 0; i < clamped; i++) {
            newDesign.add(i < old && i < oldDesign.size() ? oldDesign.get(i) : "");
        }
        this.design = newDesign;
        this.commands.removeIf(c -> c.slot() < 0 || c.slot() >= clamped);
        return this;
    }

    /** Происхождение GUI (см. {@link Source}). */
    public Source source() {
        return source;
    }

    public Gui source(Source source) {
        this.source = source == null ? Source.TABLE : source;
        return this;
    }

    public StorageType storage() {
        return storage;
    }

    public Gui storage(StorageType storage) {
        this.storage = storage == null ? StorageType.TEMPORARY : storage;
        return this;
    }

    // ================= скелет =================

    /** Непомutable-копия типов слотов (размер = {@link #slots()}). */
    public List<SlotType> skeleton() {
        return Collections.unmodifiableList(skeleton);
    }

    /**
     * Полностью заменяет скелет.
     *
     * @throws IllegalArgumentException если размер не равен количеству слотов
     */
    public void replaceSkeleton(List<SlotType> skeleton) {
        if (skeleton == null || skeleton.size() != this.slots) {
            throw new IllegalArgumentException("skeleton size must be " + this.slots);
        }
        List<SlotType> copy = new ArrayList<>(skeleton.size());
        for (SlotType type : skeleton) {
            copy.add(type == null ? SlotType.DESIGN : type);
        }
        this.skeleton = copy;
    }

    /** Тип слота; неизвестные индексы считаются дизайн-слотами. */
    public SlotType slotType(int slot) {
        if (slot < 0 || slot >= skeleton.size()) {
            return SlotType.DESIGN;
        }
        return skeleton.get(slot);
    }

    public void setSlotType(int slot, SlotType type) {
        if (slot < 0 || slot >= skeleton.size()) {
            throw new IndexOutOfBoundsException("slot " + slot + " out of bounds for " + slots);
        }
        skeleton.set(slot, type == null ? SlotType.DESIGN : type);
    }

    /** Сброс скелета: все слоты — дизайн. */
    public void resetSkeleton() {
        skeleton = new ArrayList<>(Collections.nCopies(slots, SlotType.DESIGN));
    }

    // ================= дизайн =================

    /** Непомutable-копия payloadов дизайна (размер = {@link #slots()}). */
    public List<String> design() {
        return Collections.unmodifiableList(design);
    }

    public String designAt(int slot) {
        if (slot < 0 || slot >= design.size()) {
            return "";
        }
        return design.get(slot);
    }

    public void setDesignAt(int slot, String payload) {
        if (slot < 0 || slot >= design.size()) {
            throw new IndexOutOfBoundsException("slot " + slot + " out of bounds for " + slots);
        }
        design.set(slot, payload == null ? "" : payload);
    }

    /** Устанавливает предмет в дизайн-слот (кодируется активным {@link dev.moonaticks.customGuiReworked.codec.Codecs}). */
    public void setDesignItem(int slot, ItemStack item) {
        setDesignAt(slot, Codecs.encode(item));
    }

    /**
     * Полностью заменяет дизайн.
     *
     * @throws IllegalArgumentException если размер не равен количеству слотов
     */
    public void replaceDesign(List<String> design) {
        if (design == null || design.size() != this.slots) {
            throw new IllegalArgumentException("design size must be " + this.slots);
        }
        List<String> copy = new ArrayList<>(design.size());
        for (String payload : design) {
            copy.add(payload == null ? "" : payload);
        }
        this.design = copy;
    }

    /** Сброс дизайна: все слоты пусты. */
    public void resetDesign() {
        design = new ArrayList<>(Collections.nCopies(slots, ""));
    }

    // ================= команды =================

    /** Непомutable-копия привязанных команд. */
    public List<SlotCommand> commands() {
        return Collections.unmodifiableList(commands);
    }

    public void addCommand(SlotCommand command) {
        if (command != null) {
            commands.add(command);
        }
    }

    /** Команды, привязанные к конкретному слоту (в порядке добавления). */
    public List<SlotCommand> commandsForSlot(int slot) {
        List<SlotCommand> out = new ArrayList<>();
        for (SlotCommand command : commands) {
            if (command.slot() == slot) {
                out.add(command);
            }
        }
        return out;
    }

    /**
     * Удаляет команду по слоту и её порядковому номеру внутри слота.
     *
     * @return true, если команда найдена и удалена
     */
    public boolean removeCommand(int slot, int index) {
        int i = 0;
        for (SlotCommand command : commands) {
            if (command.slot() == slot) {
                if (i == index) {
                    commands.remove(command);
                    return true;
                }
                i++;
            }
        }
        return false;
    }

    // ================= блоки =================

    /** Непомutable-копия ID кастомных блоков. */
    public Set<String> blockIds() {
        return Collections.unmodifiableSet(blockIds);
    }

    public void addBlockId(String id) {
        if (id != null && !id.isBlank()) {
            blockIds.add(id.trim());
        }
    }

    /**
     * Удаляет ID блока (без учёта регистра).
     *
     * @return true, если ID был привязан
     */
    public boolean removeBlockId(String id) {
        if (id == null) {
            return false;
        }
        String trimmed = id.trim();
        if (blockIds.remove(trimmed)) {
            return true;
        }
        for (String bound : new ArrayList<>(blockIds)) {
            if (bound.equalsIgnoreCase(trimmed)) {
                blockIds.remove(bound);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Gui{" + name + ", slots=" + slots + ", storage=" + storage + "}";
    }
}
