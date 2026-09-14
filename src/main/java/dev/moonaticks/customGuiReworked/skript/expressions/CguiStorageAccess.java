package dev.moonaticks.customGuiReworked.skript.expressions;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import dev.moonaticks.customGuiReworked.storage.StorageKey;
import org.bukkit.entity.Player;

/**
 * Чтение данных хранилища GUI для игрока (для Skript/Denizen-выражений).
 *
 * <p>BLOCK-хранилище требует локацию блока, которую нельзя вывести из
 * одного игрока, — для таких GUI данные не доступны из скрипта
 * (используйте Java-API {@code readStorage} с координатами).
 */
final class CguiStorageAccess {

    private CguiStorageAccess() {
    }

    static String[] read(CustomGuiReworked plugin, Gui gui, Player player) {
        StorageType type = gui.storage();
        StorageKey key = switch (type) {
            case PERSONAL -> StorageKey.forPlayer(player, gui.fileName());
            case TEAM -> StorageKey.forTeam(GuiOpener.teamOf(player), gui.fileName());
            case GLOBAL -> StorageKey.global(gui.fileName());
            case BLOCK, TEMPORARY -> null;
        };
        if (key == null) {
            return new String[0];
        }
        return plugin.storage().load(key);
    }
}
