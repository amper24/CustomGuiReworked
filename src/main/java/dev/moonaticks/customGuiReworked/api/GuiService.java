package dev.moonaticks.customGuiReworked.api;

import dev.moonaticks.customGuiReworked.api.functional.CraftingRecipe;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Публичный сервис плагина — основной способ использовать
 * CustomGuiReworked как библиотеку из других плагинов.
 *
 * <p>Получение без compileOnly-зависимости (рекомендуется):
 * <pre>{@code
 * GuiService service = Bukkit.getServicesManager()
 *         .load(GuiService.class).stream().findFirst().orElse(null);
 * if (service != null) {
 *     service.openGui(player, "shop");
 * }
 * }</pre>
 *
 * <p>Либо через статический фасад {@link CustomGuiAPI}.
 */
public interface GuiService {

    // ================= общий =================

    String getPluginName();

    String getPluginVersion();

    // ================= GUI =================

    /**
     * Возвращает GUI по имени.
     *
     * @return GUI или null
     */
    Gui getGui(String name);

    /** Имена всех загруженных GUI. */
    Set<String> getGuiNames();

    /**
     * Создаёт GUI с параметрами по умолчанию (27 слотов, все дизайн),
     * если он ещё не существует.
     */
    Gui createGui(String name);

    /**
     * Регистрирует GUI, созданный другим плагином. GUI сохраняется
     * в папку {@code custom/} — переживёт рестарт сервера; хранилище
     * (данные инвентаря) работает с ним в общем режиме.
     *
     * <p>Если GUI с таким именем уже существует — он заменяется.
     *
     * @return зарегистрированный GUI
     */
    Gui registerGui(Gui gui);

    /**
     * Регистрирует GUI, созданный другим плагином.
     *
     * @param gui     GUI
     * @param persist true — записать в {@code custom/} (переживёт рестарт),
     *                false — только в памяти (потеряется после перезагрузки)
     */
    Gui registerGui(Gui gui, boolean persist);

    /**
     * Снимает GUI, зарегистрированный через API.
     *
     * @param name       имя GUI
     * @param deleteFile удалить ли файл (для персистентных GUI)
     * @return true, если GUI существовал
     */
    boolean unregisterGui(String name, boolean deleteFile);

    /** Происхождение GUI: «table», «custom», «runtime» или «none». */
    String sourceOf(String name);

    /** Все GUI (неизменяемый список). */
    List<Gui> getGuis();

    /**
     * Перечитывает GUI из файла.
     *
     * @return обновлённый GUI или null
     */
    Gui loadGui(String name);

    /**
     * Удаляет GUI и его файл (данные хранилища не удаляются).
     *
     * @return true, если GUI существовал
     */
    boolean deleteGui(String name);

    /** Сохраняет GUI в файл. */
    void saveGui(Gui gui);

    // ================= открытие =================

    /** Открывает GUI игроку (BLOCK-хранилище требует блоки — см. ниже). */
    void openGui(Player player, String name);

    /** Открывает GUI игроку, передавая локацию блока (для BLOCK-хранилища). */
    void openGui(Player player, String name, Location blockLocation);

    /**
     * Открывает GUI игроку, временно подменяя тип хранилища
     * (например, открыть personal-GUI как TEMPORARY).
     * Для {@link StorageType#BLOCK} локация блока всё равно передаётся
     * отдельным вызовом с {@link Location}.
     */
    void openGui(Player player, String name, StorageType storageOverride);

    /** GUI, который игрок открыл прямо сейчас, или null. */
    Gui getOpenGui(Player player);

    // ================= блоки =================

    /**
     * Привязывает ID кастомного блока (ItemsAdder/CraftEngine) к GUI.
     * Правый клик по блоку будет открывать этот GUI.
     */
    void registerBlockGui(String blockId, String guiName);

    /** Отвязывает ID блока от всех GUI. */
    void unregisterBlockGui(String blockId);

    /** GUI, привязанный к ID блока (без учёта регистра). */
    Gui getBlockGui(String blockId);

    /** ID блоков, привязанные к GUI. */
    Set<String> getBlockIds(String guiName);

    // ================= хранилище =================

    /**
     * Читает данные хранилища.
     *
     * @param type  тип хранилища
     * @param owner владелец: имя игрока (PERSONAL), команда (TEAM),
     *              «world:x,y,z» (BLOCK), игнорируется для GLOBAL/TEMPORARY
     * @param table имя таблицы (обычно имя GUI)
     * @return список предметов (воздух для пустых слотов)
     */
    List<ItemStack> readStorage(StorageType type, String owner, String table);

    /** Записывает данные хранилища (список предметов, по одному на слот). */
    void writeStorage(StorageType type, String owner, String table, List<ItemStack> items);

    /** Очищает данные хранилища. */
    void deleteStorage(StorageType type, String owner, String table);

    // ================= локальные оверрайды (per-viewer) =================
    //
    // Методы работают с GUI, который игрок открыл прямо сейчас:
    // подменяют название/дизайн только для этой сессии — файл GUI,
    // другие игроки и другие блоки не затрагиваются. Все оверрайды
    // очищаются при закрытии GUI и никогда не пишутся в файл
    // (пока не вызван saveGui() с явными изменениями GUI).

    /**
     * Устанавливает локальное название окна открытого GUI игрока.
     * Поддерживает legacy-коды цвета (§) как и заголовок из файла.
     *
     * <p>Если окно уже открыто на сервере, где заголовок окна
     * неизменяем, название применится при следующем открытии GUI.
     *
     * @param title название; null — вернуть название из файла
     */
    void setLocalTitle(Player player, String title);

    /** Локальное название окна, либо null (используется название из файла). */
    String getLocalTitle(Player player);

    /** Возвращает открытому GUI игрока название из файла. */
    void clearLocalTitle(Player player);

    /**
     * Устанавливает локальный предмет в DESIGN/RESULT слот открытого GUI.
     *
     * @param player игрок
     * @param slot   DESIGN/RESULT слот
     * @param item   предмет; null — сбросить оверрайд (вернуть дизайн из файла)
     * @throws IllegalArgumentException если GUI не открыт для игрока и слот
     *                                 не DESIGN/RESULT
     */
    void setLocalDesign(Player player, int slot, ItemStack item);

    /**
     * Устанавливает сразу несколько локальных предметов (все валидируются
     * до применения; слоты — DESIGN/RESULT).
     */
    void setLocalDesigns(Player player, Map<Integer, ItemStack> slots);

    /** Сбрасывает локальный предмет одного слота (возвращает дизайн из файла). */
    void clearLocalDesign(Player player, int slot);

    /** Сбрасывает все локальные предметы сессии. */
    void clearAllLocalDesigns(Player player);

    /** Локальный предмет слота, либо null (показывается дизайн из файла). */
    ItemStack getLocalDesign(Player player, int slot);

    /**
     * Пер-блок + пер-плеер: устанавливает локальный предмет для GUI,
     * который {@code player} открыл на блоке {@code block}.
     * (Хранилище BLOCK уже ключится по «world:x,y,z» — метод просто
     * не даёт ошибиться с сессией.)
     */
    void setLocalDesign(Player player, Location block, int slot, ItemStack item);

    /**
     * Пер-блок + пер-плеер: локальное название для GUI, открытый
     * {@code player} на блоке {@code block}.
     */
    void setLocalTitle(Player player, Location block, String title);

    // ================= утилиты =================

    /**
     * Готовит предмет к размещению как дизайн: клонирует, ставит
     * {@code maxStackSize} равным количеству и скрытый PDC-маркер
     * (анти-дюп: ванильная механика не влечёт/не сливает такие предметы).
     *
     * @return подготовленный клон; null для AIR/null
     */
    ItemStack prepareDesignItem(ItemStack item);

    /**
     * Локация блока, на котором игрок открыл GUI прямо сейчас
     * (для BLOCK-хранилища), либо null.
     */
    Location getOpenBlockLocation(Player player);

    /**
     * Все онлайн-игроки, у которых прямо сейчас открыт GUI на блоке.
     */
    List<Player> getViewers(Location block);

    // ================= крафт / топливо / результат =================

    /**
     * Соответствует ли содержимое инвентаря рецепту крафта
     * (CRAFT-слоты против {@link CraftingRecipe#getIngredients()}).
     *
     * @param inventory инвентарь GUI (с holder'ом плагина)
     * @param recipe    рецепт
     * @return true, если рецепт валиден
     */
    boolean matchesCraft(Inventory inventory, CraftingRecipe recipe);

    /**
     * Расходует до {@code amount} предметов из FUEL-слотов инвентаря
     * (в порядке слотов) и планирует запись изменений в хранилище.
     *
     * @return сколько реально было расходу
     */
    int consumeFuel(Inventory inventory, int amount);

    /**
     * Выдаёт результаты в RESULT-слоты «как есть» (не локально):
     * предмет появляется в инвентаре и доступен игроку.
     *
     * <p>Все-or-nothing: сначала проверяются все слоты (целевой слот —
     * RESULT, пустой либо similar с запасом под стек), и только если
     * всё влезает — предметы ставятся.
     *
     * @param inventory инвентарь GUI (с holder'ом плагина)
     * @param results   RESULT-слоты: слот → предмет
     * @return true, если все результаты выдааны
     */
    boolean produceResult(Inventory inventory, Map<Integer, ItemStack> results);

    // ================= функциональные блоки =================

    /**
     * Реестр функциональных блоков (печь/верстак/бочка/генератор).
     * Удобный вход — {@link FunctionalBlock#builder(String)}
     * или {@code CustomGuiAPI.functionalBlock(String)}.
     */
    FunctionalBlockRegistry getFunctionalBlocks();
}
