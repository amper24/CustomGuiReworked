package dev.moonaticks.customGuiReworked.command;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Команда /gui: create, edit, open, delete, list, command, reload.
 *
 * <p>Права: cgui.create, cgui.edit, cgui.open, cgui.delete,
 * cgui.command, cgui.reload (в коде и в plugin.yml одинаковый
 * префикс — исправлено расхождение старой версии).
 */
public class GuiCommand implements CommandExecutor {

    private final CustomGuiReworked plugin;

    public GuiCommand(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().msg("cmd.playersOnly"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(plugin.lang().msg("cmd.usage"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "edit" -> edit(player, args);
            case "open" -> open(player, args);
            case "delete" -> delete(player, args);
            case "list" -> list(player);
            case "command" -> commandSub(player, args);
            case "reload" -> reload(player);
            default -> player.sendMessage(plugin.lang().msg("cmd.usage"));
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (!player.hasPermission("cgui.create")) {
            deny(player);
            return;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.lang().msg("cmd.create.usage"));
            return;
        }
        String name = Gui.normalizeName(args[1]);
        Gui gui = plugin.registry().get(name);
        if (gui == null) {
            gui = plugin.registry().create(name);
        }
        player.sendMessage(plugin.lang().msg("cmd.create.done", name));
        plugin.editor().openMain(player, gui);
    }

    private void edit(Player player, String[] args) {
        if (!player.hasPermission("cgui.edit")) {
            deny(player);
            return;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.lang().msg("cmd.edit.usage"));
            return;
        }
        Gui gui = plugin.registry().get(Gui.normalizeName(args[1]));
        if (gui == null) {
            notFound(player, args[1]);
            return;
        }
        plugin.editor().openMain(player, gui);
    }

    private void open(Player player, String[] args) {
        if (!player.hasPermission("cgui.open")) {
            deny(player);
            return;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.lang().msg("cmd.open.usage"));
            return;
        }
        Gui gui = plugin.registry().get(Gui.normalizeName(args[1]));
        if (gui == null) {
            notFound(player, args[1]);
            return;
        }
        plugin.opener().openForPlayer(player, gui);
    }

    private void delete(Player player, String[] args) {
        if (!player.hasPermission("cgui.delete")) {
            deny(player);
            return;
        }
        if (args.length != 2) {
            player.sendMessage(plugin.lang().msg("cmd.delete.usage"));
            return;
        }
        String name = Gui.normalizeName(args[1]);
        if (plugin.registry().delete(name)) {
            player.sendMessage(plugin.lang().msg("cmd.delete.done", name));
        } else {
            notFound(player, args[1]);
        }
    }

    private void list(Player player) {
        List<String> names = List.copyOf(plugin.registry().names());
        if (names.isEmpty()) {
            player.sendMessage(plugin.lang().msg("cmd.list.empty"));
            return;
        }
        player.sendMessage(plugin.lang().msg("cmd.list.header", names.size()));
        for (String name : names) {
            player.sendMessage(plugin.lang().msg("cmd.list.item", name));
        }
    }

    private void reload(Player player) {
        if (!player.hasPermission("cgui.reload")) {
            deny(player);
            return;
        }
        plugin.reloadPluginData();
        player.sendMessage(plugin.lang().msg("cmd.reload.done"));
    }

    private void commandSub(Player player, String[] args) {
        if (!player.hasPermission("cgui.command")) {
            deny(player);
            return;
        }
        if (args.length < 2) {
            usage(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                // /gui command add <slot> <gui> [delay] <command...>
                if (args.length < 6) {
                    usage(player);
                    return;
                }
                int slot = parseInt(player, args[2]);
                if (slot < 0) {
                    return;
                }
                Gui gui = plugin.registry().get(Gui.normalizeName(args[3]));
                if (gui == null) {
                    notFound(player, args[3]);
                    return;
                }
                if (slot >= gui.slots()) {
                    player.sendMessage(plugin.lang().msg("cmd.cmd.slotOutOfRange", gui.slots()));
                    return;
                }
                int delay = 0;
                int commandStart;
                if (isInt(args[4])) {
                    delay = Math.max(0, Integer.parseInt(args[4]));
                    commandStart = 5;
                } else {
                    commandStart = 4;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = commandStart; i < args.length; i++) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(args[i]);
                }
                String command = sb.toString().trim();
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                gui.addCommand(new SlotCommand(slot, command, delay));
                plugin.registry().save(gui);
                player.sendMessage(plugin.lang().msg("cmd.cmd.added", slot));
            }
            case "get" -> {
                if (args.length < 4) {
                    usage(player);
                    return;
                }
                int slot = parseInt(player, args[2]);
                if (slot < 0) {
                    return;
                }
                Gui gui = plugin.registry().get(Gui.normalizeName(args[3]));
                if (gui == null) {
                    notFound(player, args[3]);
                    return;
                }
                List<SlotCommand> commands = gui.commandsForSlot(slot);
                if (args.length >= 5) {
                    int index = parseInt(player, args[4]);
                    if (index < 0 || index >= commands.size()) {
                        player.sendMessage(plugin.lang().msg("cmd.cmd.notFound", slot));
                        return;
                    }
                    SlotCommand command = commands.get(index);
                    player.sendMessage(plugin.lang().msg("cmd.cmd.value", slot, command.command(), command.delay()));
                } else {
                    if (commands.isEmpty()) {
                        player.sendMessage(plugin.lang().msg("cmd.cmd.emptySlot", slot));
                        return;
                    }
                    player.sendMessage(plugin.lang().msg("cmd.cmd.listHeader", gui.name(), slot));
                    int i = 0;
                    for (SlotCommand command : commands) {
                        player.sendMessage(plugin.lang().msg("cmd.cmd.listItem", i++, command.command(), command.delay()));
                    }
                    player.sendMessage(plugin.lang().msg("cmd.cmd.listFooter"));
                }
            }
            case "delete" -> {
                if (args.length < 5) {
                    usage(player);
                    return;
                }
                int slot = parseInt(player, args[2]);
                if (slot < 0) {
                    return;
                }
                Gui gui = plugin.registry().get(Gui.normalizeName(args[3]));
                if (gui == null) {
                    notFound(player, args[3]);
                    return;
                }
                int index = parseInt(player, args[4]);
                if (index < 0) {
                    return;
                }
                if (gui.removeCommand(slot, index)) {
                    plugin.registry().save(gui);
                    player.sendMessage(plugin.lang().msg("cmd.cmd.deleted", slot));
                } else {
                    player.sendMessage(plugin.lang().msg("cmd.cmd.notFound", slot));
                }
            }
            default -> usage(player);
        }
    }

    private void deny(Player player) {
        player.sendMessage(plugin.lang().msg("noPermission"));
    }

    private void usage(Player player) {
        player.sendMessage(plugin.lang().msg("cmd.cmd.usage"));
    }

    private void notFound(Player player, String name) {
        player.sendMessage(plugin.lang().msg("cmd.guiNotFound", name));
    }

    private int parseInt(Player player, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.lang().msg("cmd.numberRequired"));
            return -1;
        }
    }

    private boolean isInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
