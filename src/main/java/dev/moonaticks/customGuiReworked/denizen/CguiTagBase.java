package dev.moonaticks.customGuiReworked.denizen;

import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.PseudoObjectTagBase;
import com.denizenscript.denizencore.tags.TagManager;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

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
 *   <li>{@code <cgui.open_of[<игрок>]>} — GUI, открытое игроком («none» если нет);</li>
 *   <li>{@code <cgui.block_of[<игрок>]>} — блок, на котором игрок открыл GUI;</li>
 *   <li>{@code <cgui.viewers[<локация>]>} — игроки, у которых открыт GUI блока;</li>
 *   <li>{@code <cgui.working[<локация>]>} — работает ли блок (onBlockTick);</li>
 *   <li>{@code <cgui.block_item[[<локация>]|<слот>]>} — предмет персистентного
 *       слота блока (даже при закрытом GUI);</li>
 *   <li>{@code <cgui.block_data[[<локация>]|<ключ>]>} и
 *       {@code <cgui.block_data[[<локация>]|<blockid>|<ключ>]>} — персистентные
 *       данные блока.</li>
 * </ul>
 *
 * <p>Множественные параметры задаются списком (разделитель {@code |});
 * локацию удобно оборачивать в квадратные скобки, чтобы запятые
 * координат не конфликтовали с разделителем:
 * {@code <cgui.block_item[[<location>]|5]>}.
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
            if (location == null || plugin.service() == null) {
                return null;
            }
            ListTag list = new ListTag();
            plugin.service().getViewers(location)
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
        tagProcessor.registerTag(ElementTag.class, LocationTag.class, "working", (attribute, object, location) ->
                new ElementTag(location != null && plugin.service() != null
                        && plugin.service().isWorking(location)));

        // <--[tag]
        // @attribute <cgui.block_item[[<location>]|<slot>]>
        // @returns ItemTag
        // @plugin CustomGuiReworked
        // @description
        // Returns the item stored in a functional block's persistent slot
        // (works even when the GUI is closed; empty if the slot is empty).
        // Input is a list: the block location and the slot number, e.g.
        // <cgui.block_item[[<location>]|5]>.
        // -->
        tagProcessor.registerTag(ItemTag.class, ListTag.class, "block_item", (attribute, object, arg) -> {
            Location loc = locationAt(arg, 0, attribute);
            int slot = slotAt(arg, 1, attribute);
            if (loc == null || slot < 0 || plugin.service() == null) {
                return null;
            }
            ItemStack item = plugin.service().getBlockSlotItem(loc, slot);
            return item == null ? null : new ItemTag(item);
        });

        // <--[tag]
        // @attribute <cgui.block_data[[<location>]|<key>]>
        // @returns ElementTag
        // @plugin CustomGuiReworked
        // @description
        // Returns a value from the functional block's persistent data
        // (empty if the key is missing or the block is not functional).
        // The block id is resolved via CraftEngine. With three list items the
        // id is given explicitly: <cgui.block_data[[<location>]|<blockid>|<key>]>.
        // -->
        tagProcessor.registerTag(ElementTag.class, ListTag.class, "block_data", (attribute, object, arg) -> {
            if (arg == null || arg.size() < 2 || plugin.service() == null) {
                return new ElementTag("");
            }
            Location loc = locationAt(arg, 0, attribute);
            if (loc == null) {
                return new ElementTag("");
            }
            FunctionalBlockData data;
            String key;
            if (arg.size() >= 3) {
                data = plugin.service().blockData(arg.get(1), loc);
                key = arg.get(2);
            } else {
                data = plugin.service().blockData(loc);
                key = arg.get(1);
            }
            if (data == null) {
                return new ElementTag("");
            }
            return new ElementTag(data.getString(key, ""));
        });
    }

    private Gui lookup(ElementTag name) {
        if (name == null) {
            return null;
        }
        return plugin.registry().get(name.asString());
    }

    /** Item {@code index} of the list argument as a Bukkit location. */
    private Location locationAt(ListTag arg, int index, Attribute attribute) {
        if (arg == null || index >= arg.size()) {
            return null;
        }
        String s = arg.get(index);
        if (s == null || s.isEmpty()) {
            return null;
        }
        LocationTag loc = LocationTag.valueOf(s, attribute == null ? null : attribute.context);
        return loc == null ? null : loc.clone();
    }

    /** Item {@code index} of the list argument as a slot number (-1 on error). */
    private int slotAt(ListTag arg, int index, Attribute attribute) {
        if (arg == null || index >= arg.size()) {
            return -1;
        }
        String s = arg.get(index);
        if (s == null) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            if (attribute != null) {
                attribute.echoError("cgui: slot must be a number, got '" + s + "'");
            }
            return -1;
        }
    }
}
