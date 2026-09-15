package dev.moonaticks.customGuiReworked.denizen;

import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.exceptions.MechanicError;
import com.denizenscript.denizencore.mechanics.MechanicImpl;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.GuiService;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Механики Denizen для инструментов пер-плеер/пер-блок GUI:
 *
 * <pre>
 * cgui - set local design:&lt;item&gt; at &lt;slot&gt; for &lt;player&gt;
 * cgui - clear local design for &lt;player&gt;
 * cgui - set local title:&lt;string&gt; for &lt;player&gt;
 * cgui - set working:&lt;true|false&gt; at &lt;location&gt;
 * cgui - set block data:&lt;key&gt;:&lt;value&gt; at &lt;location&gt; [for &lt;blockid&gt;]
 * cgui - set block slot:&lt;item&gt; at &lt;slot&gt; for &lt;location&gt;
 * </pre>
 */
public class CguiMechanics extends MechanicImpl {

    // <--[mechanic]
    // @name cgui
    //
    // @Group CustomGuiReworked
    //
    // @Description
    // Controls per-player GUI overrides and per-block "work" state.
    //
    // @Syntax
    // set local design:<item> at <slot> for <player>
    // clear local design for <player>
    // set local title:<string> for <player>
    // set working:<true|false> at <location>
    // set block data:<key>:<value> at <location> [for <blockid>]
    // set block slot:<item> at <slot> for <location>
    //
    // @Examples
    // cgui - set local design:arrow item at 5 for <player>
    // cgui - set local title:&§bPot 42% for <player>
    // cgui - set working:true at <location>
    // cgui - set block data:cook:42 at <location> for farmersdelight:cooking_pot
    // cgui - set block slot:cooked stew at 24 for <location>
    //
    // @Plugin CustomGuiReworked
    // -->

    private static final Pattern P_LOCAL_DESIGN =
            Pattern.compile("^set local design:(.+) at (\\d+) for (.+)$");
    private static final Pattern P_CLEAR_LOCAL_DESIGN =
            Pattern.compile("^clear local design for (.+)$");
    private static final Pattern P_LOCAL_TITLE =
            Pattern.compile("^set local title:(.+) for (.+)$");
    private static final Pattern P_WORKING =
            Pattern.compile("^set working:(true|false|1|0) at (.+)$");
    private static final Pattern P_BLOCK_DATA =
            Pattern.compile("^set block data:(.+) at (.+?)(?: for (.+))?$");
    private static final Pattern P_BLOCK_SLOT =
            Pattern.compile("^set block slot:(.+) at (\\d+) for (.+)$");

    private String action;

    public CguiMechanics(CustomGuiReworked plugin) {
        super(plugin);
        instance = this;
    }

    public static CguiMechanics instance;

    @Override
    protected void parseArgs(String[] args) throws MechanicError {
        String attr = attribute == null ? "" : attribute.trim();
        Matcher m;
        if ((m = P_LOCAL_DESIGN.matcher(attr)).find()) {
            action = "local-design";
            this.argItem = ItemTag.itemFromCommand(m.group(1).trim());
            this.argSlot = Integer.parseInt(m.group(2));
            this.argPlayer = PlayerTag.objectFromCommand(m.group(3).trim());
        } else if ((m = P_CLEAR_LOCAL_DESIGN.matcher(attr)).find()) {
            action = "clear-local-design";
            this.argPlayer = PlayerTag.objectFromCommand(m.group(1).trim());
        } else if ((m = P_LOCAL_TITLE.matcher(attr)).find()) {
            action = "local-title";
            this.argString = m.group(1);
            this.argPlayer = PlayerTag.objectFromCommand(m.group(2).trim());
        } else if ((m = P_WORKING.matcher(attr)).find()) {
            action = "working";
            this.argBool = "true".equalsIgnoreCase(m.group(1)) || "1".equals(m.group(1));
            this.argLocation = LocationTag.locationFromCommand(m.group(2).trim());
        } else if ((m = P_BLOCK_DATA.matcher(attr)).find()) {
            action = "block-data";
            String kv = m.group(1);
            int colon = kv.lastIndexOf(':');
            if (colon <= 0) {
                throw new MechanicError("block data syntax: <key>:<value>");
            }
            this.argKey = kv.substring(0, colon);
            this.argString = kv.substring(colon + 1);
            this.argLocation = LocationTag.locationFromCommand(m.group(2).trim());
            this.argBlockId = m.group(3) == null ? null : m.group(3).trim();
        } else if ((m = P_BLOCK_SLOT.matcher(attr)).find()) {
            action = "block-slot";
            this.argItem = ItemTag.itemFromCommand(m.group(1).trim());
            this.argSlot = Integer.parseInt(m.group(2));
            this.argLocation = LocationTag.locationFromCommand(m.group(3).trim());
        } else {
            throw new MechanicError(
                    "unknown cgui mechanic (see @Syntax: local design / clear local design / "
                            + "local title / working / block data / block slot)");
        }
    }

    private ItemTag argItem;
    private int argSlot;
    private PlayerTag argPlayer;
    private String argString;
    private boolean argBool;
    private LocationTag argLocation;
    private String argKey;
    private String argBlockId;

    @Override
    protected void execute() {
        CustomGuiReworked plugin = (CustomGuiReworked) getPlugin();
        GuiService service = plugin.service();
        if (service == null) {
            return;
        }
        switch (action) {
            case "local-design":
                if (argPlayer == null) {
                    return;
                }
                ItemStack item = argItem == null ? null : argItem.getItemStack();
                service.setLocalDesign(argPlayer.getPlayerEntity(), argSlot, item);
                break;
            case "clear-local-design":
                if (argPlayer == null) {
                    return;
                }
                service.clearLocalDesign(argPlayer.getPlayerEntity());
                break;
            case "local-title":
                if (argPlayer == null || argString == null) {
                    return;
                }
                service.setLocalTitle(argPlayer.getPlayerEntity(), argString);
                break;
            case "working":
                Location loc = argLocation == null ? null : argLocation.asLocation();
                if (loc == null) {
                    return;
                }
                service.setWorking(loc, argBool);
                break;
            case "block-data":
                Location loc = argLocation == null ? null : argLocation.asLocation();
                if (loc == null || argKey == null) {
                    return;
                }
                FunctionalBlockData data = argBlockId == null
                        ? service.blockData(loc)
                        : service.blockData(argBlockId, loc);
                if (data != null) {
                    data.set(argKey, argString);
                }
                break;
            case "block-slot":
                Location loc = argLocation == null ? null : argLocation.asLocation();
                if (loc == null) {
                    return;
                }
                ItemStack item = argItem == null ? null : argItem.getItemStack();
                service.setBlockSlotItem(loc, argSlot, item);
                break;
            default:
                break;
        }
    }

    @Override
    protected String getPlaceholder() {
        return "cgui";
    }

    @Override
    public String getName() {
        return "Cgui";
    }
}
