package dev.moonaticks.customGuiReworked.command;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Табуляция для /gui.
 */
public class GuiTabCompleter implements TabCompleter {

    private final CustomGuiReworked plugin;

    public GuiTabCompleter(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || args.length == 0) {
            return out;
        }
        String sub0 = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (player.hasPermission("cgui.create")) {
                out.add("create");
            }
            if (player.hasPermission("cgui.edit")) {
                out.add("edit");
            }
            if (player.hasPermission("cgui.open")) {
                out.add("open");
            }
            if (player.hasPermission("cgui.delete")) {
                out.add("delete");
            }
            out.add("list");
            if (player.hasPermission("cgui.command")) {
                out.add("command");
            }
            if (player.hasPermission("cgui.reload")) {
                out.add("reload");
            }
        } else if (args.length == 2) {
            if (Objects.equals(sub0, "command")) {
                out.addAll(List.of("add", "get", "delete"));
            } else {
                out.addAll(plugin.registry().names());
            }
        } else if (Objects.equals(sub0, "command") && args.length == 3) {
            for (int i = 0; i < 54; i++) {
                out.add(String.valueOf(i));
            }
        } else if (Objects.equals(sub0, "command") && args.length == 4) {
            out.addAll(plugin.registry().names());
        } else if (Objects.equals(sub0, "command") && args.length == 5) {
            for (int i = 0; i <= 10; i++) {
                out.add(String.valueOf(i));
            }
        }
        return filter(out, args[args.length - 1]);
    }

    private List<String> filter(List<String> in, String prefix) {
        if (prefix.isEmpty()) {
            return in;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : in) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
