package dev.moonaticks.customGuiReworked.denizen;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.PseudoObjectTagBase;
import com.denizenscript.denizencore.tags.TagManager;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;

/**
 * Pseudo-object «cgui» для тегов Denizen.
 *
 * <p>Теги:
 * <ul>
 *   <li>{@code <cgui.guis>} — список имён всех GUI;</li>
 *   <li>{@code <cgui.exists[<имя>]>} — существует ли GUI;</li>
 *   <li>{@code <cgui.size[<имя>]>} — количество слотов;</li>
 *   <li>{@code <cgui.title[<имя>]>} — заголовок;</li>
 *   <li>{@code <cgui.storage[<имя>]>} — тип хранилища;</li>
 *   <li>{@code <cgui.open_of[<игрок>]>} — GUI, открытое игроком («none» если нет).</li>
 * </ul>
 */
public class CguiTagBase extends PseudoObjectTagBase<CguiTagBase> {

    public static CguiTagBase instance;

    private final CustomGuiReworked plugin;

    public CguiTagBase(CustomGuiReworked plugin) {
        this.plugin = plugin;
        instance = this;
        TagManager.registerStaticTagBaseHandler(CguiTagBase.class, "cgui", (tag) -> instance);
        register();
    }

    private void register() {

        // <--[tag]
        // @attribute <cgui.guis>
        // @returns ListTag
        // @plugin CustomGuiReworked
        // @description Returns a list of all GUI names.
        // -->
        tagProcessor.registerTag(ListTag.class, "guis", (attribute, object) ->
                new ListTag(plugin.registry().names().toArray(new String[0])));

        // <--[tag]
        // @attribute <cgui.exists[<name>]>
        // @returns ElementTag(Boolean)
        // @plugin CustomGuiReworked
        // @description Returns true if a GUI with the given name exists.
        // -->
        tagProcessor.registerTag(ElementTag.class, String.class, "exists", (attribute, object, name) ->
                new ElementTag(String.valueOf(plugin.registry().get(name) != null)));

        // <--[tag]
        // @attribute <cgui.size[<name>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the slot count of the GUI (empty if not found).
        // -->
        tagProcessor.registerTag(ElementTag.class, String.class, "size", (attribute, object, name) -> {
            Gui gui = plugin.registry().get(name);
            return new ElementTag(gui == null ? "" : String.valueOf(gui.slots()));
        });

        // <--[tag]
        // @attribute <cgui.title[<name>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the title of the GUI (empty if not found).
        // -->
        tagProcessor.registerTag(ElementTag.class, String.class, "title", (attribute, object, name) -> {
            Gui gui = plugin.registry().get(name);
            return new ElementTag(gui == null ? "" : gui.title());
        });

        // <--[tag]
        // @attribute <cgui.storage[<name>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the storage type of the GUI
        // (block, personal, global, team or temporary; empty if not found).
        // -->
        tagProcessor.registerTag(ElementTag.class, String.class, "storage", (attribute, object, name) -> {
            Gui gui = plugin.registry().get(name);
            return new ElementTag(gui == null ? "" : gui.storage().id());
        });

        // <--[tag]
        // @attribute <cgui.open_of[<player>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the name of the GUI the player has open right now
        // (or «none» if the player has no GUI open).
        // -->
        tagProcessor.registerTag(ElementTag.class, PlayerTag.class, "open_of", (attribute, object, player) -> {
            Gui gui = player == null || player.getPlayer() == null
                    ? null
                    : plugin.opener().guiOf(player.getPlayer().getUniqueId());
            return new ElementTag(gui == null ? "none" : gui.name());
        });
    }
}
