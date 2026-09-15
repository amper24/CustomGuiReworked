package dev.moonaticks.customGuiReworked.denizen;

import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.PseudoObjectTagBase;
import com.denizenscript.denizencore.tags.TagManager;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import org.bukkit.Location;

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

    @Override
    public void register() {

        // <--[tag]
        // @attribute <cgui.guis>
        // @returns ListTag
        // @plugin CustomGuiReworked
        // @description Returns a list of all GUI names.
        // -->
        tagProcessor.registerTag(ListTag.class, "guis", (attribute, object) ->
                new ListTag(plugin.registry().names()));

        // <--[tag]
        // @attribute <cgui.exists[<name>]>
        // @returns ElementTag(Boolean)
        // @plugin CustomGuiReworked
        // @description Returns true if a GUI with the given name exists.
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "exists", (attribute, object, name) -> {
            String guiName = name == null ? null : name.asString();
            return new ElementTag(guiName != null && plugin.registry().get(guiName) != null);
        });

        // <--[tag]
        // @attribute <cgui.size[<name>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the slot count of the GUI (empty if not found).
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "size", (attribute, object, name) -> {
            Gui gui = lookup(name);
            return gui == null ? new ElementTag("") : new ElementTag(gui.slots());
        });

        // <--[tag]
        // @attribute <cgui.title[<name>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the title of the GUI (empty if not found).
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "title", (attribute, object, name) -> {
            Gui gui = lookup(name);
            return gui == null ? new ElementTag("") : new ElementTag(gui.title());
        });

        // <--[tag]
        // @attribute <cgui.storage[<name>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the storage type of the GUI
        // (block, personal, global, team or temporary; empty if not found).
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "storage", (attribute, object, name) -> {
            Gui gui = lookup(name);
            return gui == null ? new ElementTag("") : new ElementTag(gui.storage().id());
        });

        // <--[tag]
        // @attribute <cgui.open_of[<player>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns the name of the GUI the player has open right now
        // (or «none» if the player has no GUI open).
        // -->
        tagProcessor.registerTag(ElementTag.class, PlayerTag.class, "open_of", (attribute, object, player) -> {
            Gui gui = player == null || player.getPlayerEntity() == null
                    ? null
                    : plugin.opener().guiOf(player.getPlayerEntity().getUniqueId());
            return new ElementTag(gui == null ? "none" : gui.name());
        });

        // <--[tag]
        // @attribute <cgui.block_of[<player>]>
        // @returns LocationTag
        // @plugin CustomGuiReworked
        // @description Returns the location of the functional block whose GUI
        // the player has open right now (empty if the GUI is not a block-GUI).
        // -->
        tagProcessor.registerTag(LocationTag.class, PlayerTag.class, "block_of", (attribute, object, player) -> {
            Location loc = player == null || player.getPlayerEntity() == null
                    ? null
                    : plugin.service() == null ? null : plugin.service().getOpenBlockLocation(player.getPlayerEntity());
            return loc == null ? null : new LocationTag(loc);
        });

        // <--[tag]
        // @attribute <cgui.viewers[<location>]>
        // @returns ListTag(PlayerTag)
        // @plugin CustomGuiReworked
        // @description Returns the players who have the GUI of this block open right now.
        // -->
        tagProcessor.registerTag(ListTag.class, LocationTag.class, "viewers", (attribute, object, location) -> {
            Location loc = location == null ? null : location.asLocation();
            if (loc == null || plugin.service() == null) {
                return null;
            }
            ListTag list = new ListTag();
            plugin.service().getViewers(loc)
                    .forEach(p -> list.addObject(new PlayerTag(p)));
            return list;
        });

        // <--[tag]
        // @attribute <cgui.working[<location>]>
        // @returns ElementTag(Boolean)
        // @plugin CustomGuiReworked
        // @description Returns true if the functional block at this location
        // is "working" (its server logic ticks even without open GUIs).
        // -->
        tagProcessor.registerTag(ElementTag.class, LocationTag.class, "working", (attribute, object, location) -> {
            Location loc = location == null ? null : location.asLocation();
            return new ElementTag(loc != null && plugin.service() != null
                    && plugin.service().isWorking(loc));
        });

        // <--[tag]
        // @attribute <cgui.block_item[<location>,<slot>]>
        // @returns ItemTag
        // @plugin CustomGuiReworked
        // @description Returns the item stored in a functional block's persistent
        // slot (works even when the GUI is closed; empty if the slot is empty).
        // -->
        tagProcessor.registerTag(ItemTag.class, LocationTag.class, ElementTag.class, "block_item",
                (attribute, object, location, slot) -> {
                    Location loc = location == null ? null : location.asLocation();
                    if (loc == null || slot == null || plugin.service() == null) {
                        return null;
                    }
                    int s;
                    try {
                        s = Integer.parseInt(slot.asString().trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    org.bukkit.inventory.ItemStack item = plugin.service().getBlockSlotItem(loc, s);
                    return item == null ? null : new ItemTag(item);
                });

        // <--[tag]
        // @attribute <cgui.block_data[<location>,<key>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Returns a value from the functional block's persistent data.
        // The block id is resolved via CraftEngine.
        // -->
        tagProcessor.registerTag(ElementTag.class, LocationTag.class, ElementTag.class, "block_data",
                (attribute, object, location, key) -> {
                    Location loc = location == null ? null : location.asLocation();
                    if (loc == null || key == null || plugin.service() == null) {
                        return new ElementTag("");
                    }
                    FunctionalBlockData data = plugin.service().blockData(loc);
                    if (data == null) {
                        return new ElementTag("");
                    }
                    String v = data.getString(key.asString(), "");
                    return new ElementTag(v);
                });

        // <--[tag]
        // @attribute <cgui.block_data[<location>,<blockid>,<key>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description Same as <cgui.block_data[<location>,<key>]>, but with an
        // explicit functional block id instead of CraftEngine resolution.
        // -->
        tagProcessor.registerTag(ElementTag.class, LocationTag.class, ElementTag.class, ElementTag.class,
                "block_data", (attribute, object, location, blockId, key) -> {
                    Location loc = location == null ? null : location.asLocation();
                    if (loc == null || blockId == null || key == null || plugin.service() == null) {
                        return new ElementTag("");
                    }
                    FunctionalBlockData data = plugin.service().blockData(blockId.asString(), loc);
                    if (data == null) {
                        return new ElementTag("");
                    }
                    String v = data.getString(key.asString(), "");
                    return new ElementTag(v);
                });
    }

    private Gui lookup(ElementTag name) {
        if (name == null) {
            return null;
        }
        return plugin.registry().get(name.asString());
    }
}
