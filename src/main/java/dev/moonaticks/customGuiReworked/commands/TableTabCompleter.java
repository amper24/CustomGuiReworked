package dev.moonaticks.customGuiReworked.commands;

import org.bukkit.command.TabCompleter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TableTabCompleter implements TabCompleter {
    File dir;
    
    public TableTabCompleter(File dir) {
        this.dir = new File(dir, "tables");
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return suggestions;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            if(player.hasPermission("customgui.create")) {
                suggestions.add("create");
            }
            if(player.hasPermission("customgui.delete")) {
                suggestions.add("delete");
            }
            if(player.hasPermission("customgui.open")) {
                suggestions.add("open");
            }
            if(player.hasPermission("customgui.edit")) {
                suggestions.add("edit");
            }
            if(player.hasPermission("customgui.reload")) {
                suggestions.add("reload");
            }
            if(player.hasPermission("customgui.command")) {
                suggestions.add("command");
            }
        }
        else if (args.length == 2) {
            if (Objects.equals(args[0], "open") || Objects.equals(args[0], "edit") || Objects.equals(args[0], "delete")) {
                File[] files = dir.listFiles((dir1, name) -> name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        suggestions.add(file.getName().replace(".yml", ""));
                    }
                }
            }
            else if (Objects.equals(args[0], "command")) {
                suggestions.add("add");
                suggestions.add("delete");
                suggestions.add("get");
            }
        }
        else if (args.length == 3 && Objects.equals(args[0], "command")) {
            suggestions.add("0");
            suggestions.add("1");
            suggestions.add("2");
            suggestions.add("3");
            suggestions.add("4");
            suggestions.add("5");
            suggestions.add("6");
            suggestions.add("7");
            suggestions.add("8");
        }
        else if (args.length == 4 && Objects.equals(args[0], "command")) {
            File[] files = dir.listFiles((dir1, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    suggestions.add(file.getName().replace(".yml", ""));
                }
            }
        }
        else if (args.length == 5 && Objects.equals(args[0], "command")) {
            if (Objects.equals(args[1], "add")) {
                suggestions.add("0");
                suggestions.add("1");
                suggestions.add("2");
                suggestions.add("3");
                suggestions.add("4");
                suggestions.add("5");
            }
            else if (Objects.equals(args[1], "delete") || Objects.equals(args[1], "get")) {
                suggestions.add("0");
                suggestions.add("1");
                suggestions.add("2");
                suggestions.add("3");
                suggestions.add("4");
                suggestions.add("5");
            }
        }
        else if (args.length >= 6 && Objects.equals(args[0], "command") && Objects.equals(args[1], "add")) {
            suggestions.add("say");
            suggestions.add("give");
            suggestions.add("tp");
            suggestions.add("gamemode");
            suggestions.add("time");
            suggestions.add("weather");
        }
        
        return suggestions;
    }
}
