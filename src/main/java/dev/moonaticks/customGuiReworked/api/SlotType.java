package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Тип слота GUI. Встроенные константы ведут себя как прежде; сторонние плагины
 * регистрируют новые типы с помощью {@link #builder(String)} и
 * {@link CustomGuiAPI#registerSlotType(SlotType)}.
 *
 * <p>Типы хранятся в файле по ID, например {@code myplugin:input}. Если
 * файл прочитан до регистрации плагина-владельца, тип остаётся на месте,
 * но ведёт себя как запертый декоративный слот. Регистрация активирует
 * тот же объект для уже загруженных GUI; unregister снова запирает его,
 * не уничтожая записанные в хранилище предметы.
 *
 * <p>Регистрировать типы нужно на основном потоке до открытия GUI.
 */
public final class SlotType {

    private static final Pattern CUSTOM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");

    public static final SlotType DESIGN = builtIn("design", false, false, false, false, true, true,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE);
    public static final SlotType CONTAINER = builtIn("container", true, true, true, true, false, false,
            Material.GREEN_STAINED_GLASS_PANE);
    public static final SlotType CRAFT = builtIn("craft", true, true, true, true, false, false,
            Material.ORANGE_STAINED_GLASS_PANE);
    public static final SlotType RESULT = builtIn("result", false, true, false, true, true, false,
            Material.BLACK_STAINED_GLASS_PANE);
    public static final SlotType FUEL = builtIn("fuel", true, true, true, true, false, false,
            Material.RED_STAINED_GLASS_PANE);

    /** Порядок в редакторе: старые пять типов сохраняют свои позиции. */
    private static final Map<String, SlotType> TYPES = new LinkedHashMap<>();
    static {
        for (SlotType type : new SlotType[]{DESIGN, CONTAINER, CRAFT, RESULT, FUEL}) {
            TYPES.put(type.id, type);
        }
    }

    private final String id;
    private final boolean builtin;
    /** null — ещё не зарегистрирован, никакое взаимодействие не разрешено. */
    private volatile Definition definition;

    private SlotType(String id, boolean builtin, Definition definition) {
        this.id = id;
        this.builtin = builtin;
        this.definition = definition;
    }

    private static SlotType builtIn(String id, boolean insert, boolean take, boolean persist,
                                    boolean track, boolean localDesign, boolean decorative, Material icon) {
        return new SlotType(id, true, new Definition(insert, take, persist, track, localDesign, decorative,
                icon, id, "", null, null, null, null, Set.of(), null));
    }

    /** ID для файла GUI (нижний регистр). */
    public String id() {
        return id;
    }

    /** Аналог Enum.name(): встроенные типы — в верхнем регистре. */
    public String name() {
        return builtin ? id.toUpperCase(Locale.ROOT) : id;
    }

    /** Позиция в текущем списке зарегистрированных типов; -1 для незарегистрированного. */
    public int ordinal() {
        return Arrays.asList(values()).indexOf(this);
    }

    public boolean isRegistered() {
        return definition != null;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    /** Неразрешённый ID безопасно считается декором (не выдаёт хранимые предметы). */
    public boolean isDecorative() {
        Definition d = definition;
        return d == null || d.decorative;
    }

    public boolean allowsInsert() {
        Definition d = definition;
        return d != null && d.insert;
    }

    public boolean allowsTake() {
        Definition d = definition;
        return d != null && d.take;
    }

    public boolean isPersistable() {
        Definition d = definition;
        return d != null && d.persist;
    }

    public boolean isTracked() {
        Definition d = definition;
        return d != null && d.track;
    }

    public boolean allowsLocalDesign() {
        Definition d = definition;
        return d != null && d.localDesign;
    }

    /** Иконка в редакторе (для неразрешённого типа — BARRIER). */
    public Material icon() {
        Definition d = definition;
        return d == null ? Material.BARRIER : d.icon;
    }

    /** Заголовок для стороннего типа (встроенные типы локализуются редактором). */
    public String displayName() {
        Definition d = definition;
        return d == null ? id : d.name;
    }

    public String description() {
        Definition d = definition;
        return d == null ? "" : d.description;
    }

    /**
     * Можно ли положить этот предмет в слот (включая фильтр предметов).
     * Проверка используется при кликах, драгах и shift-кликах.
     */
    public boolean canInsert(Gui gui, Inventory inventory, int slot, Player player, ItemStack item) {
        Definition d = definition;
        return d != null && d.insert && item != null && item.getType() != Material.AIR
                && (d.insertFilter == null || d.insertFilter.test(new SlotContext(gui, inventory, slot, player, item)));
    }

    /** Можно ли забрать предмет из слота (включая фильтр). */
    public boolean canTake(Gui gui, Inventory inventory, int slot, Player player, ItemStack item) {
        Definition d = definition;
        return d != null && d.take && item != null && item.getType() != Material.AIR
                && (d.takeFilter == null || d.takeFilter.test(new SlotContext(gui, inventory, slot, player, item)));
    }

    /** ID типов, за изменением которых следит этот тип. */
    public Set<String> watches() {
        Definition d = definition;
        return d == null ? Set.of() : d.watches;
    }

    public void handleClick(GuiSlotClickEvent event) {
        Definition d = definition;
        if (d != null && d.onClick != null) {
            d.onClick.accept(event);
        }
    }

    public void handleChange(GuiSlotChangedEvent event) {
        Definition d = definition;
        if (d != null && d.onChange != null) {
            d.onChange.accept(event);
        }
    }

    /** Вызывается для каждого слота этого типа при изменении наблюдаемого типа. */
    public void handleRelatedChange(SlotRelationEvent event) {
        Definition d = definition;
        if (d != null && d.onRelatedChange != null && d.watches.contains(event.change().getSlotType().id())) {
            d.onRelatedChange.accept(event);
        }
    }

    public boolean watches(SlotType other) {
        return other != null && watches().contains(other.id());
    }

    /** Контекст предиката вставки/изъятия. ItemStack не следует изменять. */
    public record SlotContext(Gui gui, Inventory inventory, int slot, Player player, ItemStack item) {
    }

    /** Изменение исходного слота и слот получателя уведомления о связи. */
    public record SlotRelationEvent(GuiSlotChangedEvent change, int relatedSlot) {
    }

    /** Начинает описание типа; namespace обязателен (например, myplugin:input). */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Регистрирует описание и возвращает канонический экземпляр. При загрузке
     * файла ранее регистрации это ТОТ ЖЕ объект, который уже лежит в GUI.
     * Не заменяйте встроенные ID. Повторная регистрация обновляет правила.
     */
    public static synchronized SlotType register(SlotType type) {
        Objects.requireNonNull(type, "type");
        if (type.builtin || type.definition == null) {
            throw new IllegalArgumentException("Only custom, defined slot types can be registered");
        }
        SlotType canonical = TYPES.computeIfAbsent(type.id, id -> new SlotType(id, false, null));
        canonical.definition = type.definition;
        return canonical;
    }

    /** Отключает кастомный тип; ссылки в GUI и ID в файлах сохраняются. */
    public static synchronized boolean unregister(String id) {
        SlotType type = TYPES.get(normalizeId(id));
        if (type == null || type.builtin || type.definition == null) {
            return false;
        }
        type.definition = null;
        return true;
    }

    /** Только зарегистрированные типы: встроенные, затем кастомные по ID. */
    public static synchronized SlotType[] values() {
        List<SlotType> registered = new ArrayList<>(List.of(DESIGN, CONTAINER, CRAFT, RESULT, FUEL));
        TYPES.values().stream().filter(type -> !type.builtin && type.isRegistered())
                .sorted(java.util.Comparator.comparing(SlotType::id)).forEach(registered::add);
        return registered.toArray(SlotType[]::new);
    }

    /** Возвращает зарегистрированный тип или null. */
    public static synchronized SlotType get(String id) {
        SlotType type = TYPES.get(normalizeId(id));
        return type != null && type.isRegistered() ? type : null;
    }

    /**
     * Разрешает ID в файле; несуществующий namespace сохраняется как запертый
     * placeholder, а неизвестное legacy-значение становится DESIGN.
     */
    public static synchronized SlotType fromLegacy(String value) {
        if (value == null) {
            return DESIGN;
        }
        String id = normalizeId(value);
        if (CUSTOM_ID.matcher(id).matches()) {
            return TYPES.computeIfAbsent(id, key -> new SlotType(key, false, null));
        }
        if (id.startsWith("container")) {
            return CONTAINER;
        }
        if (id.startsWith("result")) {
            return RESULT;
        }
        if (id.startsWith("craft")) {
            return CRAFT;
        }
        if (id.startsWith("fuel")) {
            return FUEL;
        }
        return DESIGN;
    }

    /** Канонизирует тип перед помещением в GUI (builder без register остаётся запертым). */
    public static SlotType resolve(SlotType type) {
        return type == null ? DESIGN : fromLegacy(type.id);
    }

    /** Аналог Enum.valueOf для зарегистрированных типов. */
    public static SlotType valueOf(String name) {
        SlotType found = get(name);
        if (found == null) {
            throw new IllegalArgumentException("Unknown slot type: " + name);
        }
        return found;
    }

    /** Снимает сторонние регистрации при выключении плагина (ссылки остаются безопасными). */
    public static synchronized void clearCustom() {
        for (SlotType type : TYPES.values()) {
            if (!type.builtin) {
                type.definition = null;
            }
        }
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SlotType type && id.equals(type.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private record Definition(boolean insert, boolean take, boolean persist, boolean track,
                              boolean localDesign, boolean decorative, Material icon, String name,
                              String description, Predicate<SlotContext> insertFilter,
                              Predicate<SlotContext> takeFilter, Consumer<GuiSlotClickEvent> onClick,
                              Consumer<GuiSlotChangedEvent> onChange, Set<String> watches,
                              Consumer<SlotRelationEvent> onRelatedChange) {
    }

    /**
     * Правила типа. По умолчанию новый тип заперт. Для слота, принимающего
     * предметы, включите persist(true), иначе предметы могли бы исчезнуть при
     * закрытии неперсистентного GUI. Отслеживание включается автоматически
     * для сохраняемых слотов, для output слотов включите track(true) явно.
     */
    public static final class Builder {
        private final String id;
        private boolean insert;
        private boolean take;
        private boolean persist;
        private boolean track;
        private boolean localDesign;
        private boolean decorative;
        private Material icon = Material.PAPER;
        private String displayName;
        private String description = "";
        private Predicate<SlotContext> insertFilter;
        private Predicate<SlotContext> takeFilter;
        private Consumer<GuiSlotClickEvent> onClick;
        private Consumer<GuiSlotChangedEvent> onChange;
        private final Set<String> watches = new LinkedHashSet<>();
        private Consumer<SlotRelationEvent> onRelatedChange;

        private Builder(String id) {
            this.id = normalizeId(id);
            if (!CUSTOM_ID.matcher(this.id).matches()) {
                throw new IllegalArgumentException("Slot type ID must be namespaced, e.g. myplugin:input: " + id);
            }
            displayName = this.id;
        }

        public Builder displayName(String value) {
            displayName = value == null || value.isBlank() ? id : value;
            return this;
        }

        public Builder description(String value) {
            description = value == null ? "" : value;
            return this;
        }

        public Builder icon(Material value) {
            // Paper can only answer isItem() once its RegistryAccess is ready.
            if (value == null || (Bukkit.getServer() != null && !value.isItem())) {
                throw new IllegalArgumentException("Slot type icon must be an item");
            }
            icon = value;
            return this;
        }

        public Builder allowInsert(boolean value) {
            insert = value;
            return this;
        }

        public Builder allowTake(boolean value) {
            take = value;
            return this;
        }

        public Builder persist(boolean value) {
            persist = value;
            return this;
        }

        public Builder track(boolean value) {
            track = value;
            return this;
        }

        /** Для неперсистентных output/декоративных слотов (анимации/локальные предметы). */
        public Builder localDesign(boolean value) {
            localDesign = value;
            return this;
        }

        /** Неизменяемый декор, как DESIGN (может иметь локальные оверрайды). */
        public Builder decorative(boolean value) {
            decorative = value;
            return this;
        }

        public Builder acceptInsert(Predicate<SlotContext> filter) {
            insertFilter = filter;
            return this;
        }

        public Builder acceptTake(Predicate<SlotContext> filter) {
            takeFilter = filter;
            return this;
        }

        public Builder onClick(Consumer<GuiSlotClickEvent> handler) {
            onClick = handler;
            return this;
        }

        public Builder onChange(Consumer<GuiSlotChangedEvent> handler) {
            onChange = handler;
            return this;
        }

        /**
         * Направленная связь: слоты данного типа получат onRelatedChange,
         * когда изменится слот указанного типа в том же GUI.
         */
        public Builder watch(SlotType... types) {
            for (SlotType type : types) {
                watches.add(Objects.requireNonNull(type, "watched type").id());
            }
            return this;
        }

        /** Позволяет сослаться на тип другого плагина ещё до его регистрации. */
        public Builder watch(String... ids) {
            for (String watched : ids) {
                String value = normalizeId(watched);
                if (!CUSTOM_ID.matcher(value).matches() && !TYPES.containsKey(value)) {
                    throw new IllegalArgumentException("Unknown watched slot type: " + watched);
                }
                watches.add(value);
            }
            return this;
        }

        public Builder onRelatedChange(Consumer<SlotRelationEvent> handler) {
            onRelatedChange = handler;
            return this;
        }

        public SlotType build() {
            if (insert && !persist) {
                throw new IllegalStateException("Insertable slot types must persist their contents");
            }
            if (decorative && (insert || take || persist || track)) {
                throw new IllegalStateException("Decorative slot types cannot be interactive or persistent");
            }
            if (localDesign && (insert || persist)) {
                throw new IllegalStateException("Local design is only safe on non-persistent output/decorative slots");
            }
            return new SlotType(id, false, new Definition(insert, take, persist, track || persist,
                    localDesign || decorative, decorative, icon, displayName, description,
                    insertFilter, takeFilter, onClick, onChange, Set.copyOf(watches), onRelatedChange));
        }
    }
}
