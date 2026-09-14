package dev.moonaticks.customGuiReworked.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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
}
