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
