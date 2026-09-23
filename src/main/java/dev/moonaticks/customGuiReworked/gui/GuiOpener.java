package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.animation.DesignAnimation;
import dev.moonaticks.customGuiReworked.api.event.GuiCloseEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiOpenEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.codec.Codecs;
import dev.moonaticks.customGuiReworked.lang.LanguageManager;
import dev.moonaticks.customGuiReworked.storage.BlockStorageBackend;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import dev.moonaticks.customGuiReworked.storage.StorageService;
import dev.moonaticks.customGuiReworked.util.DesignItems;
import dev.moonaticks.customGuiReworked.util.ItemDrops;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Открытие и закрытие GUI: сборка инвентаря по скелету/дизайну/данным,
 * события API, дифная реконсиляция слотов при закрытии.
 *
 * <p>Чтение диска при открытии выполняется асинхронно ({@link StorageService#preload}),
 * сам инвентарь собирается и открывается строго на основном потоке.
 *
 * <p><b>Локальные оверрайды (per-viewer).</b> При сборке инвентаря и в
 * {@link #rescueDesignItems} для каждого DESIGN-слота «ожидаемый» предмет
 * берётся из {@link GuiHolder#getLocalDesign(int)} если оверрайд задан,
 * иначе из файла GUI. Название окна в {@link #finishOpen} — из
 * {@link GuiHolder#getLocalTitle()} если задан, иначе из {@link Gui#title()}.
 * Все оверрайды очищаются при {@link #handleClose}.
 */
public class GuiOpener {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    /**
     * Кэш: есть ли у {@link org.bukkit.inventory.InventoryView} сеттер
     * заголовка окна ({@code setWindowTitle(Component)}) — в старых
     * сборках Paper его нет. Ищем один раз, не на каждом вызове.
     */
    private static volatile Method viewTitleSetter;
    private static volatile boolean viewTitleSetterChecked;

    private final CustomGuiReworked plugin;
    private final GuiRegistry registry;
    private final StorageService storage;
    private final LanguageManager lang;
    private final ItemDrops drops = new ItemDrops();

    /** GUI, открытые прямо сейчас (игрок → GUI). */
    private final Map<UUID, Gui> openGuis = new ConcurrentHashMap<>();
    /** Монотонный номер запроса на открытие (защита от устаревших async-preload). */
    private final Map<UUID, Long> openRequests = new ConcurrentHashMap<>();

    /** GUI, который игрок открыл сейчас, или null. */
    public Gui guiOf(UUID playerId) {
        return playerId == null ? null : openGuis.get(playerId);
    }

    public GuiOpener(CustomGuiReworked plugin, GuiRegistry registry, StorageService storage, LanguageManager lang) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.lang = lang;
    }

    /**
     * Открывает GUI. Для {@link StorageType#BLOCK} без локации — отказ.
     */
    public void openForPlayer(Player player, Gui gui) {
        openForPlayer(player, gui, null, null, null);
    }

    /**
     * Открывает GUI.
     *
     * @param player        игрок
     * @param gui           GUI
     * @param blockLocation локация блока (обязательна для BLOCK-хранилища, игнорируется иначе)
     */
    public void openForPlayer(Player player, Gui gui, Location blockLocation) {
        openForPlayer(player, gui, blockLocation, null, null);
    }

    /**
     * Открывает GUI.
     *
     * @param player          игрок
     * @param gui             GUI
     * @param blockLocation   локация блока (обязательна для BLOCK-хранилища, игнорируется иначе)
     * @param storageOverride временный тип хранилища (null — тип самого GUI)
     */
    public void openForPlayer(Player player, Gui gui, Location blockLocation, StorageType storageOverride) {
        openForPlayer(player, gui, blockLocation, storageOverride, null);
    }

    /**
     * Открывает GUI.
     *
     * @param player          игрок
     * @param gui             GUI
     * @param blockLocation   локация блока (обязательна для BLOCK-хранилища, игнорируется иначе)
     * @param storageOverride временный тип хранилища (null — тип самого GUI)
     * @param blockId         ID кастомного блока (itemsadder:/craftengine:) — для
     *                        диспетчеризации функциональных блоков; null, если блок
     *                        не функциональный или GUI открыт программно
     */
    public void openForPlayer(Player player, Gui gui, Location blockLocation,
                              StorageType storageOverride, String blockId) {
        if (player == null || gui == null) {
            return;
        }
        if (gui.skeleton().isEmpty() || gui.skeleton().size() != gui.slots()) {
            player.sendMessage(lang.msg("interface.broken.skeleton"));
            return;
        }

        StorageType type = storageOverride != null ? storageOverride : gui.storage();
        StorageKey key = buildKey(player, gui, blockLocation, type);
        if (key == null) {
            return; // сообщение уже отправлено
        }

        long requestId = openRequests.merge(player.getUniqueId(), 1L, Long::sum);
        // Диск читается на I/O-потоке, сборка/открытие — на основном.
        storage.preload(key, () -> {
            if (!player.isOnline()) {
                openRequests.remove(player.getUniqueId(), requestId);
                return;
            }
            // Устаревший запрос (игрок успел запросить другой GUI) — игнорируем
            if (!openRequests.getOrDefault(player.getUniqueId(), -1L).equals(requestId)) {
                return;
            }
            finishOpen(player, gui, key, requestId, blockLocation, blockId);
        });
    }

    private StorageKey buildKey(Player player, Gui gui, Location blockLocation, StorageType type) {
        return switch (type) {
            case BLOCK -> {
                if (blockLocation == null || blockLocation.getWorld() == null) {
                    player.sendMessage(lang.msg("interface.blockNoLocation"));
                    yield null;
                }
                yield StorageKey.forBlock(blockLocation, gui.fileName());
            }
            case PERSONAL -> StorageKey.forPlayer(player, gui.fileName());
            case TEAM -> StorageKey.forTeam(teamOf(player), gui.fileName());
            case GLOBAL -> StorageKey.global(gui.fileName());
            case TEMPORARY -> StorageKey.temporary(player.getUniqueId(), gui.fileName());
        };
    }

    private void finishOpen(Player player, Gui gui, StorageKey key, long requestId,
                            Location blockLocation, String blockId) {
        String[] stored = key.type() == StorageType.TEMPORARY ? null : storage.load(key);

        GuiHolder holder = new GuiHolder(gui, key);
        holder.setPlayer(player.getUniqueId());
        if (key.type() == StorageType.BLOCK) {
            // Запоминаем блок сессии: из параметра открытия, иначе — из owner-ключа.
            holder.setBlockLocation(blockLocation != null && blockLocation.getWorld() != null
                    ? blockLocation
                    : StorageKey.blockLocation(key.owner()));
        }
        // Название: локальный оверрайд (если уже задан), иначе из файла GUI.
        String title = holder.getLocalTitle() != null ? holder.getLocalTitle() : gui.title();
        Inventory inventory = Bukkit.createInventory(holder, gui.slots(), SERIALIZER.deserialize(title));
        holder.attach(inventory);
        applyDesign(inventory, holder);
        if (stored != null) {
            applyStorage(inventory, gui, stored);
        }
        holder.initBaseline(snapshotTracked(inventory, gui));

        GuiOpenEvent event = new GuiOpenEvent(player, gui, inventory, key);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            // Снимаем маркер только если это всё ещё наш запрос — иначе
            // мы бы обнулили более новый запрос и его бы посчитали устаревшим.
            openRequests.remove(player.getUniqueId(), requestId);
            return;
        }

        // Функциональные блоки: onOpen-колбэк перед показом окна —
        // оттуда удобно сетаить локальный title/design (он ещё «свежий»).
        if (key.type() == StorageType.BLOCK && holder.blockLocation() != null) {
            plugin.dispatcher().onFunctionalOpen(player, holder, holder.blockLocation(), blockId);
        }
        // Локальный title мог быть задан в GuiOpenEvent/onOpen —
        // инвентарь ещё не открыт, переделываем заголовок без моргания.
        if (holder.getLocalTitle() != null) {
            inventory = retitle(holder, gui, inventory);
        }
        openRequests.remove(player.getUniqueId(), requestId);
        openGuis.put(player.getUniqueId(), gui);
        if (key.type() == StorageType.BLOCK) {
            plugin.dispatcher().onInventoryOpened(player, key);
        }
        player.openInventory(inventory);
    }

    /**
     * Пересоздаёт инвентарь того же holder'а с новым заголовком
     * (инвентарь ещё не открыт игроку, поэтому без моргания).
     */
    private Inventory retitle(GuiHolder holder, Gui gui, Inventory old) {
        Inventory next = Bukkit.createInventory(holder, gui.slots(),
                SERIALIZER.deserialize(holder.getLocalTitle()));
        for (int i = 0; i < old.getSize(); i++) {
            next.setItem(i, old.getItem(i));
        }
        holder.attach(next);
        return next;
    }

    /**
     * Ставит дизайн в DESIGN-слоты инвентаря.
     * Ожидаемый предмет: локальный оверрайд ({@link GuiHolder#getLocalDesign(int)})
     * если задан, иначе — дизайн из файла. Каждый предмет проходит
     * {@link DesignItems#prepare} (maxStackSize + PDC-маркер, анти-дюп).
     */
    void applyDesign(Inventory inventory, GuiHolder holder) {
        Gui gui = holder.gui();
        for (int i = 0; i < gui.slots(); i++) {
            if (!gui.slotType(i).isRegistered() || !gui.slotType(i).isDecorative()) {
                continue;
            }
            ItemStack local = holder.getLocalDesign(i);
            ItemStack item = local != null
                    ? DesignItems.prepare(local)
                    : DesignItems.prepare(Codecs.decode(gui.designAt(i)));
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(i, item);
            }
        }
    }

    private void applyStorage(Inventory inventory, Gui gui, String[] stored) {
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i).isDecorative()) {
                continue;
            }
            String payload = i < stored.length ? stored[i] : null;
            if (payload == null || payload.isBlank()) {
                continue;
            }
            org.bukkit.inventory.ItemStack item = Codecs.decode(payload);
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(i, item);
            }
        }
    }

    /**
     * Снимок отслеживаемых слотов (персистентные + RESULT;
     * не-отслеживаемые позиции = null). Результат используется как
     * baseline для записи в хранилище И для
     * {@link GuiSlotChangedEvent}.
     */
    private String[] snapshotTracked(Inventory inventory, Gui gui) {
        String[] snapshot = new String[gui.slots()];
        for (int i = 0; i < gui.slots(); i++) {
            if (GuiHolder.isTracked(gui.slotType(i))) {
                snapshot[i] = Codecs.encode(inventory.getItem(i));
            }
        }
        return snapshot;
    }

    /**
     * Возвращает игрокам предметы, которые ванильная механика могла
     * занести в дизайн-слоты (теоретически — драг по вине ванили,
     * обход через моды, PvP-клиенты и т.п.; нормальные shift/double-click
     * пути до декора не доходят благодаря PDC-маркеру и maxStackSize).
     * Если стак «слился» с декоративным предметом — возвращается
     * только дельта, сам дизайн восстанавливается как был (с маркером
     * и maxStackSize, подготовленными {@link DesignItems#prepare}).
     *
     * <p>«Ожидаемый» предмет — локальный оверрайд, если он задан,
     * иначе дизайн из файла.
     */
    void rescueDesignItems(GuiHolder holder) {
        Gui gui = holder.gui();
        Inventory inv = holder.getInventory();
        if (inv == null) {
            return;
        }
        for (int i = 0; i < gui.slots(); i++) {
            // После unregister содержимое уже открытого слота могло быть
            // персистентным: не возвращаем его игроку, иначе будет дюп.
            if (!gui.slotType(i).isRegistered() || !gui.slotType(i).isDecorative()) {
                continue;
            }
            ItemStack current = inv.getItem(i);
            ItemStack local = holder.getLocalDesign(i);
            ItemStack expected = local != null
                    ? DesignItems.prepare(local)
                    : DesignItems.prepareExpected(Codecs.decode(gui.designAt(i)));
            if (current == null || current.getType() == Material.AIR) {
                // Слот опустел (например double-click стянул дизайн на курсор) —
                // возвращаем оформление на место. Слитый с курсором стек всё
                // равно окажется в инвентаре игрока (это его курсор), дюпа нет.
                inv.setItem(i, expected);
                continue;
            }
            if (expected != null && expected.getType() != Material.AIR && current.isSimilar(expected)) {
                int delta = current.getAmount() - expected.getAmount();
                inv.setItem(i, expected);
                if (delta > 0) {
                    // Лишние предметы влитые в декоративный стак — возвращаем игроку.
                    ItemStack deltaItem = current.clone();
                    deltaItem.setAmount(delta);
                    giveBack(holder, deltaItem);
                }
                // При delta < 0 часть декора, гипотетически, унесли — expected
                // уже поставлен обратно вызовом выше, этого достаточно.
            } else {
                ItemStack moved = current.clone();
                inv.setItem(i, expected);
                giveBack(holder, moved);
            }
        }
    }

    private void giveBack(GuiHolder holder, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        for (org.bukkit.entity.HumanEntity human : holder.getInventory().getViewers()) {
            if (!(human instanceof org.bukkit.entity.Player player)) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                if (rest != null && rest.getType() != Material.AIR && rest.getAmount() > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
            return; // предмет возвращается одному зрителю
        }
    }

    // ================= локальные оверрайды: живое применение =================

    /**
     * Применяет (или сбрасывает) локальный дизайн-оверрайд в живом инвентаре:
     * запоминает его в holder и ставит подготовленный предмет
     * ({@link DesignItems#prepare}) в слот либо возвращает слот к
     * дизайну из файла.
     *
     * @param holder holder открытой сессии
     * @param slot   DESIGN/RESULT слот
     * @param item   предмет; null (или AIR) — сбросить оверрайд
     */
    public static void applyLocalDesign(GuiHolder holder, int slot, ItemStack item) {
        // Валидация (диапазон + тип слота) даже если инвентарь не прицеплён.
        holder.setLocalDesign(slot, item);
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        if (item == null || item.getType() == Material.AIR) {
            inventory.setItem(slot, defaultForSlot(holder, slot));
        } else {
            inventory.setItem(slot, DesignItems.prepare(item));
        }
        // «Виртуальные» изменения RESULT-слота (локальный результат/прогресс
        // из анимаций и onTick) синхронизируем в baseline: иначе при
        // ближайшей реконсиляции они выглядели бы как чужое изменение и
        // генерировали бы GuiSlotChangedEvent с виртуальным предметом.
        // Реальное действие игрока (например, забрал результат) по-прежнему
        // даст событие: текущее содержимое больше не совпадёт с baseline.
        if (GuiHolder.isTracked(holder.gui().slotType(slot)) && holder.gui().slotType(slot).allowsLocalDesign()) {
            String[] baseline = holder.baseline();
            if (baseline != null && slot < baseline.length) {
                baseline[slot] = Codecs.encode(inventory.getItem(slot));
            }
        }
    }

    /**
     * «Ожидаемый» предмет слота без локального оверрайда:
     * дизайн из файла, подготовленный {@link DesignItems#prepare}
     * (null, если слот пуст).
     */
    public static ItemStack defaultForSlot(GuiHolder holder, int slot) {
        return DesignItems.prepare(Codecs.decode(holder.gui().designAt(slot)));
    }

    /**
     * Применяет локальный заголовок к уже открытому окну игрока.
     *
     * <p>Заголовок окна неизменяем в базовом API Bukkit; в новых
     * сборках Paper есть {@code InventoryView#setWindowTitle(Component)}.
     * Чтобы не ломать сборку на старых версиях, сеттер вызывается
     * через reflection (метод ищется один раз). Если метода нет —
     * заголовок просто сохранится в holder и применится при следующем
     * открытии GUI, без ошибок.
     */
    public void applyLocalTitle(Player player, GuiHolder holder, String title) {
        if (player == null || holder == null || title == null) {
            return;
        }
        Method setter = viewTitleSetter();
        if (setter == null) {
            return;
        }
        try {
            setter.invoke(player.getOpenInventory(), SERIALIZER.deserialize(title));
        } catch (ReflectiveOperationException e) {
            // Не критично: заголовок применится при следующем открытии.
        }
    }

    private static Method viewTitleSetter() {
        if (!viewTitleSetterChecked) {
            synchronized (GuiOpener.class) {
                if (!viewTitleSetterChecked) {
                    try {
                        viewTitleSetter = org.bukkit.inventory.InventoryView.class
                                .getMethod("setWindowTitle", Component.class);
                    } catch (NoSuchMethodException e) {
                        viewTitleSetter = null;
                    }
                    viewTitleSetterChecked = true;
                }
            }
        }
        return viewTitleSetter;
    }

    // ================= зрители блока =================

    /**
     * Все онлайн-игроки, у которых прямо сейчас открыт GUI на данном блоке
     * (BLOCK-хранилище с совпадающим owner-ключом).
     *
     * @param location блок (мир должен быть загружен)
     * @return список зрителей (возможен пустой)
     */
    public static List<Player> getViewers(Location location) {
        List<Player> viewers = new ArrayList<>();
        if (location == null || location.getWorld() == null) {
            return viewers;
        }
        String owner = BlockStorageBackend.ownerKey(location);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof GuiHolder holder
                    && holder.key().type() == StorageType.BLOCK
                    && holder.key().owner().equals(owner)) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    // ================= реконсиляция / закрытие =================

    /**
     * Планирует дифную реконсиляцию на следующий тик: к этому моменту
     * Bukkit уже применил изменения клика к инвентарю (внутри
     * InventoryClickEvent/DragEvent содержимое ещё старое — читать
     * его синхронно нельзя).
     */
    public void scheduleReconcile(GuiHolder holder) {
        synchronized (holder) {
            if (holder.isReconcileQueued()) {
                return;
            }
            holder.setReconcileQueued(true);
        }
        holder.reconcileTask(new BukkitRunnable() {
            @Override
            public void run() {
                holder.reconcileTask(null);
                holder.setReconcileQueued(false);
                reconcile(holder, holder.drainCandidates());
            }
        }.runTask(plugin));
    }

    /**
     * Сравнивает текущее содержимое отслеживаемых слотов (персистентные
     * + RESULT) с baseline: изменившиеся персистентные слоты пишет в
     * хранилище, а по каждому изменившемуся слоту вызывает
     * {@link GuiSlotChangedEvent} (с предметами «было/стало»).
     *
     * <p>Вызывается на следующий тик после кликов/драгов (когда Bukkit
     * уже применил изменения) и при закрытии — поэтому событие видит
     * ФАКТУЧЕСКОЕ состояние инвентаря, в отличие от {@code GuiSlotClickEvent}.
     *
     * @param candidates null — проверить все отслеживаемые слоты;
     *                   иначе только перечисленные
     */
    public void reconcile(GuiHolder holder, Collection<Integer> candidates) {
        Gui gui = holder.gui();
        rescueDesignItems(holder);
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        String[] baseline = holder.baseline();
        if (baseline == null) {
            return;
        }
        boolean persist = holder.key().type() != StorageType.TEMPORARY;
        for (int i = 0; i < gui.slots(); i++) {
            SlotType type = gui.slotType(i);
            if (!GuiHolder.isTracked(type)) {
                continue;
            }
            if (candidates != null && !candidates.contains(i)) {
                continue;
            }
            String encoded = Codecs.encode(inventory.getItem(i));
            String before = i < baseline.length ? baseline[i] : null;
            if (java.util.Objects.equals(encoded, before == null ? "" : before)) {
                continue; // не изменилось
            }
            if (persist && GuiHolder.isPersistable(type)) {
                storage.updateSlot(holder.key(), i, encoded);
            }
            baseline[i] = encoded;
            fireSlotChanged(holder, gui, inventory, i, before, encoded);
        }
    }

    /**
     * Серверное изменение слота (напр., «работа» блока без зрителей —
     * {@code setBlockSlotItem}): применяет предмет к живому инвентарю
     * сессии, обновляет baseline и вызывает {@link GuiSlotChangedEvent}.
     *
     * <p>Вызывается для отслеживаемых слотов (CONTAINER/CRAFT/FUEL/RESULT)
     * с уже записанным в хранилище предметом.
     */
    public void applyRemoteChange(GuiHolder holder, int slot, ItemStack item) {
        Gui gui = holder.gui();
        if (slot < 0 || slot >= gui.slots() || !GuiHolder.isTracked(gui.slotType(slot))) {
            return;
        }
        Inventory inventory = holder.getInventory();
        String[] baseline = holder.baseline();
        if (inventory == null || baseline == null || slot >= baseline.length) {
            return;
        }
        if (item == null || item.getType() == Material.AIR) {
            inventory.setItem(slot, null);
        } else {
            inventory.setItem(slot, item.clone());
        }
        String before = baseline[slot];
        String after = Codecs.encode(inventory.getItem(slot));
        if (java.util.Objects.equals(after, before == null ? "" : before)) {
            return; // ничего не изменилось
        }
        baseline[slot] = after;
        fireSlotChanged(holder, gui, inventory, slot, before, after);
    }

    /**
     * Вызывает {@link GuiSlotChangedEvent} для изменившегося слота.
     * Предметы «было/стало» восстанавливаются из baseline/текущего
     * содержимого (null — пустой слот).
     */
    private void fireSlotChanged(GuiHolder holder, Gui gui, Inventory inventory, int slot,
                                 String before, String after) {
        UUID playerId = holder.player();
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        GuiSlotChangedEvent event = new GuiSlotChangedEvent(player, gui, inventory, slot,
                gui.slotType(slot), Codecs.decode(before), Codecs.decode(after));
        // Функциональный обработчик блока — до внешних слушателей
        // (как в кликах: он может использовать актуальное содержимое).
        plugin.dispatcher().onSlotChanged(holder, event);
        Bukkit.getPluginManager().callEvent(event);
        try {
            event.getSlotType().handleChange(event);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Slot type '" + event.getSlotType().id() + "' onChange failed: " + e.getMessage());
        }
        // Направленные связи: например output.watch(input) уведомляет каждый
        // output-слот об изменении input. Вызывается и при серверных изменениях.
        for (int related = 0; related < gui.slots(); related++) {
            if (related == slot) {
                continue;
            }
            SlotType watcher = gui.slotType(related);
            if (watcher.watches(event.getSlotType())) {
                try {
                    watcher.handleRelatedChange(new SlotType.SlotRelationEvent(event, related));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Slot type '" + watcher.id()
                            + "' onRelatedChange failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Обработка закрытия GUI: для TEMPORARY возвращает предметы,
     * для остальных типов делает финальную реконсиляцию и немедленную запись.
     *
     * <p>В конце сессия завершается: локальные оверрайды (дизайн/title/блок)
     * очищаются, анимации дизайна этого инвентаря останавливаются.
     */
    public void handleClose(Player player, GuiHolder holder) {
        Gui gui = holder.gui();
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        BukkitTask task = holder.reconcileTask();
        if (task != null) {
            task.cancel();
            holder.reconcileTask(null);
        }
        holder.setReconcileQueued(false);
        openGuis.remove(player.getUniqueId(), gui);
        StorageKey key = holder.key();

        // Предметы, занесённые ванилью в дизайн-слоты, возвращаем
        // игроку до обработки хранилища/возврата.
        rescueDesignItems(holder);

        boolean destroyedBlock = key.type() == StorageType.BLOCK
                && plugin.dispatcher().isDestroyed(key.owner());
        if (key.type() == StorageType.TEMPORARY) {
            drops.returnToPlayer(player, inventory, gui);
            player.sendMessage(lang.msg("interface.temporaryReturned"));
        } else if (!destroyedBlock) {
            // InventoryCloseEvent читает уже финальное содержимое —
            // здесь диф корректен, в отличие от ClickEvent.
            reconcile(holder, null);
            storage.saveNow(key);
        }
        Bukkit.getPluginManager().callEvent(new GuiCloseEvent(player, gui, inventory, key));

        // Сессия завершена: снимаем пер-вьювер оверрайды и анимации.
        DesignAnimation.onInventoryClosed(inventory);
        holder.clearLocalState();
    }

    /** Команда игрока по scoreboard (fallback — «default»). */
    public static String teamOf(Player player) {
        Team team = player.getScoreboard().getPlayerTeam(player);
        return team == null ? "default" : team.getName();
    }
}
