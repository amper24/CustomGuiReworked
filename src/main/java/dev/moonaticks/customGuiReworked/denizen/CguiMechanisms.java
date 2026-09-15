package dev.moonaticks.customGuiReworked.denizen;

import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.GuiService;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Механизмы Denizen (setters объектов, применяются командой {@code adjust})
 * для инструментов пер-плеер/пер-блок GUI.
 *
 * <p>Эта линия Denizen (1.3.x) не содержит системы «mechanics» —
 * изменения значений в dScript делаются механизмами:
 *
 * <pre>
 * adjust &lt;player&gt;  cgui_local_title:&lt;title&gt;
 * adjust &lt;player&gt;  cgui_clear_local_title
 * adjust &lt;player&gt;  cgui_local_design:[&lt;slot&gt;|&lt;item&gt;]
 * adjust &lt;player&gt;  cgui_local_design:[&lt;location&gt;|&lt;slot&gt;|&lt;item&gt;]
 * adjust &lt;player&gt;  cgui_clear_local_design
 * adjust &lt;location&gt; cgui_working:&lt;true|false&gt;
 * adjust &lt;location&gt; cgui_block_data:[&lt;key&gt;|&lt;value&gt;]
 * adjust &lt;location&gt; cgui_block_data:[&lt;blockid&gt;|&lt;key&gt;|&lt;value&gt;]
 * adjust &lt;location&gt; cgui_block_item:[&lt;slot&gt;|&lt;item&gt;]
 * </pre>
 */
public final class CguiMechanisms {

    public CguiMechanisms(CustomGuiReworked plugin) {
        registerPlayer(plugin);
        registerBlock(plugin);
    }

    // ================= PlayerTag =================

    private static void registerPlayer(CustomGuiReworked plugin) {

        // <--[mechanism]
        // @name cgui_local_title
        // @object PlayerTag
        // @input ElementTag
        // @plugin CustomGuiReworked
        // @description
        // Sets the local (per-viewer) title of the GUI the player has open
        // right now. Supports legacy color codes.
        // @Syntax
        // adjust <player> cgui_local_title:<title>
        // @Examples
        // adjust <player> cgui_local_title:"&amp;bPot 42%"
        // -->
        PlayerTag.tagProcessor.registerMechanism("cgui_local_title", false, ElementTag.class, (player, mechanism, input) -> {
            Player p = player.getPlayerEntity();
            GuiService service = plugin.service();
            if (service == null || p == null || input == null) {
                return;
            }
            service.setLocalTitle(p, input.asString());
        });

        // <--[mechanism]
        // @name cgui_clear_local_title
        // @object PlayerTag
        // @input None
        // @plugin CustomGuiReworked
        // @description
        // Restores the file title of the GUI the player has open right now.
        // @Syntax
        // adjust <player> cgui_clear_local_title
        // @Examples
        // adjust <player> cgui_clear_local_title
        // -->
        PlayerTag.tagProcessor.registerMechanism("cgui_clear_local_title", false, (player, mechanism) -> {
            Player p = player.getPlayerEntity();
            GuiService service = plugin.service();
            if (service == null || p == null) {
                return;
            }
            service.clearLocalTitle(p);
        });

        // <--[mechanism]
        // @name cgui_local_design
        // @object PlayerTag
        // @input ListTag
        // @plugin CustomGuiReworked
        // @description
        // Sets the local (per-viewer) item of a DESIGN/RESULT slot of the GUI
        // the player has open right now. Input: [<slot>|<item>] for a personal
        // GUI, or [<location>|<slot>|<item>] to target the GUI open on a block.
        // @Syntax
        // adjust <player> cgui_local_design:[<slot>|<item>]
        // adjust <player> cgui_local_design:[<location>|<slot>|<item>]
        // @Examples
        // adjust <player> cgui_local_design:[5|cooked_pot]
        // adjust <player> cgui_local_design:[<context.block>|10|iron_ingot]
        // -->
        PlayerTag.tagProcessor.registerMechanism("cgui_local_design", false, ListTag.class, (player, mechanism, input) -> {
            Player p = player.getPlayerEntity();
            GuiService service = plugin.service();
            if (service == null || p == null || input == null) {
                return;
            }
            if (input.size() == 2) {
                int slot = parseSlot(mechanism, input.get(0));
                if (slot < 0) {
                    return;
                }
                service.setLocalDesign(p, slot, itemFrom(mechanism, input.get(1)));
            } else if (input.size() == 3 && LocationTag.matches(input.get(0))) {
                LocationTag loc = LocationTag.valueOf(input.get(0), mechanism.context);
                int slot = parseSlot(mechanism, input.get(1));
                if (loc == null || slot < 0) {
                    return;
                }
                service.setLocalDesign(p, loc, slot, itemFrom(mechanism, input.get(2)));
            } else {
                mechanism.echoError("cgui_local_design input: [<slot>|<item>] or [<location>|<slot>|<item>]");
            }
        });

        // <--[mechanism]
        // @name cgui_clear_local_design
        // @object PlayerTag
        // @input None
        // @plugin CustomGuiReworked
        // @description
        // Removes ALL local design overrides of the player's open GUI
        // (slots fall back to the file design).
        // @Syntax
        // adjust <player> cgui_clear_local_design
        // @Examples
        // adjust <player> cgui_clear_local_design
        // -->
        PlayerTag.tagProcessor.registerMechanism("cgui_clear_local_design", false, (player, mechanism) -> {
            Player p = player.getPlayerEntity();
            GuiService service = plugin.service();
            if (service == null || p == null) {
                return;
            }
            service.clearAllLocalDesigns(p);
        });
    }

    // ================= LocationTag =================

    private static void registerBlock(CustomGuiReworked plugin) {

        // <--[mechanism]
        // @name cgui_working
        // @object LocationTag
        // @input ElementTag
        // @plugin CustomGuiReworked
        // @description
        // Toggles the "working" state of the functional block at this location
        // (server logic keeps ticking even when no GUI is open; persisted).
        // @Syntax
        // adjust <location> cgui_working:<true|false>
        // @Examples
        // adjust <location> cgui_working:true
        // -->
        LocationTag.tagProcessor.registerMechanism("cgui_working", false, ElementTag.class, (location, mechanism, input) -> {
            GuiService service = plugin.service();
            if (service == null || input == null) {
                return;
            }
            service.setWorking(location, input.asBoolean());
        });

        // <--[mechanism]
        // @name cgui_block_data
        // @object LocationTag
        // @input ListTag
        // @plugin CustomGuiReworked
        // @description
        // Sets a persistent value of the functional block at this location
        // (survives restarts, exists without an open GUI). Input:
        // [<key>|<value>] (block id resolved via CraftEngine) or
        // [<blockid>|<key>|<value>] with an explicit block id.
        // @Syntax
        // adjust <location> cgui_block_data:[<key>|<value>]
        // adjust <location> cgui_block_data:[<blockid>|<key>|<value>]
        // @Examples
        // adjust <location> cgui_block_data:[progress|0.5]
        // adjust <location> cgui_block_data:[farmersdelight:cooking_pot|progress|0.5]
        // -->
        LocationTag.tagProcessor.registerMechanism("cgui_block_data", false, ListTag.class, (location, mechanism, input) -> {
            GuiService service = plugin.service();
            if (service == null || input == null || input.size() < 2) {
                return;
            }
            FunctionalBlockData data;
            String key;
            String value;
            if (input.size() == 3) {
                data = service.blockData(input.get(0), location);
                key = input.get(1);
                value = input.get(2);
            } else {
                data = service.blockData(location);
                key = input.get(0);
                value = input.get(1);
            }
            if (data != null) {
                data.set(key, value);
            }
        });

        // <--[mechanism]
        // @name cgui_block_item
        // @object LocationTag
        // @input ListTag
        // @plugin CustomGuiReworked
        // @description
        // Writes an item into a persistent slot of the functional block at this
        // location (works even when the GUI is closed; open viewers are
        // redrawn). Input: [<slot>|<item>]; give "air" to clear the slot.
        // @Syntax
        // adjust <location> cgui_block_item:[<slot>|<item>]
        // @Examples
        // adjust <location> cgui_block_item:[24|iron_ingot]
        // -->
        LocationTag.tagProcessor.registerMechanism("cgui_block_item", false, ListTag.class, (location, mechanism, input) -> {
            GuiService service = plugin.service();
            if (service == null || input == null || input.size() != 2) {
                return;
            }
            int slot = parseSlot(mechanism, input.get(0));
            if (slot < 0) {
                return;
            }
            service.setBlockSlotItem(location, slot, itemFrom(mechanism, input.get(1)));
        });
    }

    // ================= helpers =================

    private static int parseSlot(Mechanism mechanism, String s) {
        if (s == null) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            mechanism.echoError("cgui: slot must be a number, got '" + s + "'");
            return -1;
        }
    }

    private static ItemStack itemFrom(Mechanism mechanism, String s) {
        if (s == null) {
            return null;
        }
        ItemTag item = ItemTag.valueOf(s, mechanism.context);
        return item == null ? null : item.getItemStack();
    }
}
