package dev.moonaticks.customGuiReworked.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.managers.EditorGUIs;
import dev.moonaticks.customGuiReworked.tools.Gui;
import dev.moonaticks.customGuiReworked.managers.TableEditorManager;
import dev.moonaticks.customGuiReworked.tools.TableGUI;
import dev.moonaticks.customGuiReworked.tools.MessageFormatter;

import java.io.File;

public class GuiTableCommand implements CommandExecutor {
    TableEditorManager manager;
    CustomGuiReworked plugin;
    EditorGUIs editorGUIs;
    TableGUI tableGUI;
    File dir;

    public GuiTableCommand(CustomGuiReworked plugin, TableEditorManager manager, EditorGUIs editorGUIs, TableGUI tableGUI, File dir) {
        this.manager = manager;
        this.plugin = plugin;
        this.editorGUIs = editorGUIs;
        this.tableGUI = tableGUI;
        this.dir = new File(dir, "tables");
    }
    
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("PlayersOnly", "§cЭта команда может быть выполнена только игроками."));
            return true;
        }
        if (args.length == 0) {
            MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandError", "§cИспользуйте: §f/gui [create/open/delete/edit/reload]"));
            return true;
        }
        switch (args[0]) {
            case "create" -> {
                Player player = (Player) sender;
                if(!player.hasPermission("customgui.create")) {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("NoPermission", "§cУ вас нет прав для использования этой команды"));
                    return true;
                }
                if(args.length == 2) {
                    String tableName = args[1];
                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
                    File file = new File(dir, tableName + ".yml");
                    if(!file.exists()) {
                        Gui gui = manager.createFile(tableName + ".yml");
                        gui.resetSkeleton();
                        gui.resetDesign();
                        manager.saveToFile(gui);
                        editorGUIs.openMain(player, gui);
                    } else {
                        Gui gui = manager.getGuiByFile(tableName + ".yml");
                        editorGUIs.openMain(player, gui);
                    }
                } else {
                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandCreateCommandError", "§cИспользуйте: §f/gui create <имя_интерфейса>"));
                }
            }
            case "edit" -> {
                Player player = (Player) sender;
                if(!player.hasPermission("customgui.edit")) {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("NoPermission", "§cУ вас нет прав для использования этой команды"));
                    return true;
                }
                if(args.length == 2) {
                    String tableName = args[1];
                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
                    File file = new File(dir, tableName + ".yml");
                    Gui gui = manager.getGuiByFile(tableName + ".yml");
                    if(file.exists()) {
                        editorGUIs.openMain(player, gui);
                    } else {
                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("ErrorSet", "§cИспользуйте: §f/gui edit <имя_интерфейса>"));
                    }
                } else {
                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandEditCommandError", "§cИспользуйте: §f/gui edit <имя_интерфейса>"));
                }
            }
            case "delete" -> {
                Player player = (Player) sender;
                if(!player.hasPermission("customgui.delete")) {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("NoPermission", "§cУ вас нет прав для использования этой команды"));
                    return true;
                }
                if(args.length == 2) {
                    String tableName = args[1];
                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
                    File file = new File(dir, tableName + ".yml");
                    if(file.exists()) {
                        file.delete();
                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("DeleteSuccess", "§aИнтерфейс §f%s §aбыл удален!"), tableName);
                    } else {
                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("InterfaceNotFound", "§cИнтерфейс не найден!"));
                    }
                } else if(args.length == 3 && args[2].equals("confirm")) {
                    String tableName = args[1];
                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
                    File file = new File(dir, tableName + ".yml");
                    if(file.exists()) {
                        file.delete();
                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("DeleteSuccess", "§aИнтерфейс §f%s §aбыл удален!"), tableName);
                    } else {
                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("InterfaceNotFound", "§cИнтерфейс не найден!"));
                    }
                } else {
                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandDeleteCommandError", "§cИспользуйте: §f/gui delete <имя_интерфейса> [confirm]"));
                }
            }
            case "open" -> {
                Player player = (Player) sender;
                if(!player.hasPermission("customgui.open")) {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("NoPermission", "§cУ вас нет прав для использования этой команды"));
                    return true;
                }
                if(args.length == 2) {
                    String tableName = args[1].replaceAll("[^a-zA-Z0-9_-]", "_");
                    File file = new File(dir, tableName + ".yml");

                    if(file.exists()) {
                        Gui gui = manager.getGuiByFile(tableName + ".yml");
                        manager.lastGui.put(player.getUniqueId(), gui);
                        tableGUI.getCustomInventory(player, gui);
                    } else {
                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("ErrorOpen", "§cИнтерфейс не найден!"));
                    }
                }
            }
            case "command" -> {
                Player player = (Player) sender;
                if(!player.hasPermission("customgui.command")) {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("NoPermission", "§cУ вас нет прав для использования этой команды"));
                    return true;
                }
                if(args.length >= 2) {
                    switch (args[1]) {
                        case "add" -> {
                            if (args.length >= 3) {
                                if (!isInteger(args[2])) {
                                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("SlotsError", "§cСлот должен быть числом!"));
                                    return true;
                                }
                                int slot = Integer.parseInt(args[2]);
                                if (args.length >= 4) {
                                    String tableName = args[3];
                                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");

                                    Gui gui = manager.getGuiByFile(tableName + ".yml");
                                    File file = new File(dir, tableName + ".yml");
                                    if (file.exists()) {
                                        JsonArray array = gui.getCommandExecutor();
                                        int index = 0;
                                        for (JsonElement element : array) {
                                            if (element.getAsJsonObject().get("slot").getAsInt() == slot) {
                                                index++;
                                            }
                                        }
                                        if (args.length >= 5) {
                                            if (!isInteger(args[4])) {
                                                MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("DelayError", "§cЗадержка должна быть числом!"));
                                                return true;
                                            }
                                            int delay = Integer.parseInt(args[4]);
                                            if (args.length >= 6) {
                                                StringBuilder commandBuilder = new StringBuilder(args[5]);
                                                if (args.length >= 7) {
                                                    for (int i = 6; i < args.length; i++) {
                                                        commandBuilder.append(" ").append(args[i]);
                                                    }
                                                }
                                                JsonObject object = new JsonObject();
                                                object.addProperty("command", commandBuilder.toString());
                                                object.addProperty("slot", slot);
                                                object.addProperty("index", index);
                                                object.addProperty("delay", delay);
                                                array.add(object);
                                                gui.setCommandExecutor(array);
                                                manager.saveToFile(gui);
                                                MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandAddSuccess", "§aКоманда добавлена в слот: §f%s"), String.valueOf(slot));
                                            } else {
                                                MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandAddError", "§cИспользуйте: §f/gui command add <слот> <имя_интерфейса> \"<команда>\""));
                                            }
                                        } else {
                                            MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandAddError", "§cИспользуйте: §f/gui command add <слот> <имя_интерфейса> \"<команда>\""));
                                        }
                                    } else {
                                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("InterfaceNotFound", "§cИнтерфейс не найден!"));
                                    }
                                } else {
                                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandAddError", "§cИспользуйте: §f/gui command add <слот> <имя_интерфейса> \"<команда>\""));
                                }
                            } else {
                                MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandAddError", "§cИспользуйте: §f/gui command add <слот> <имя_интерфейса> \"<команда>\""));
                            }
                        }
                        case "delete" -> {
                            if (args.length >= 3) {
                                if (!isInteger(args[2])) {
                                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("SlotsError", "§cСлот должен быть числом!"));
                                    return true;
                                }
                                int slot = Integer.parseInt(args[2]);
                                if (args.length >= 4) {
                                    String tableName = args[3];
                                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
                                    Gui gui = manager.getGuiByFile(tableName + ".yml");
                                    File file = new File(dir, tableName + ".yml");
                                    if (file.exists()) {
                                        if (args.length >= 5) {
                                            if (!isInteger(args[4])) {
                                                MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("IndexError", "§cИндекс должен быть числом!"));
                                                return true;
                                            }
                                            int index = Integer.parseInt(args[4]);
                                            JsonArray array = gui.getCommandExecutor();
                                            boolean found = false;
                                            for (int i = 0; i < array.size(); i++) {
                                                JsonObject o = array.get(i).getAsJsonObject();
                                                if (o.get("slot").getAsInt() == slot && o.get("index").getAsInt() == index) {
                                                    array.remove(i);
                                                    found = true;
                                                    break;
                                                }
                                            }
                                            if (found) {
                                                gui.setCommandExecutor(array);
                                                manager.saveToFile(gui);
                                                MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandDeleteSuccess", "§aКоманда удалена из слота: §f%s"), String.valueOf(slot));
                                            } else {
                                                MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandNotFound", "§cКоманда в слоте §f%s §aдля интерфейса §f%s §cне найдена!"), String.valueOf(slot), tableName);
                                            }
                                        } else {
                                            MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandDeleteError", "§cИспользуйте: §f/gui command delete <слот> <имя_интерфейса> <индекс>"));
                                        }
                                    } else {
                                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("InterfaceNotFound", "§cИнтерфейс не найден!"));
                                    }
                                } else {
                                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandDeleteError", "§cИспользуйте: §f/gui command delete <слот> <имя_интерфейса> <индекс>"));
                                }
                            } else {
                                MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandDeleteError", "§cИспользуйте: §f/gui command delete <слот> <имя_интерфейса> <индекс>"));
                            }
                        }
                        case "get" -> {
                            if (args.length >= 3) {
                                if (!isInteger(args[2])) {
                                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("SlotsError", "§cСлот должен быть числом!"));
                                    return true;
                                }
                                int slot = Integer.parseInt(args[2]);
                                if (args.length >= 4) {
                                    String tableName = args[3];
                                    tableName = tableName.replaceAll("[^a-zA-Z0-9_-]", "_");
                                    Gui gui = manager.getGuiByFile(tableName + ".yml");
                                    File file = new File(dir, tableName + ".yml");
                                    if (file.exists()) {
                                        if (args.length == 4) {
                                            MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandListHeader", "§e################################"));
                                            MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandListTitle", "§aСписок команд для таблицы §f%s §aдля слота §f%s"), tableName, String.valueOf(slot));
                                            JsonArray array = gui.getCommandExecutor();
                                            for (JsonElement e : array) {
                                                JsonObject o = e.getAsJsonObject();
                                                if (o.get("slot").getAsInt() == slot) {
                                                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandListFormat", "§e%s §f- %s"), o.get("index").getAsString(), o.get("command").getAsString());
                                                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("Delay", "§6Задержка: §f%s"), o.get("delay").getAsString());
                                                }
                                            }
                                            MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandListFooter", "§e################################"));
                                            return true;
                                        } else if (args.length >= 5) {
                                            if (!isInteger(args[4])) {
                                                MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("IndexError", "§cИндекс должен быть числом!"));
                                                return true;
                                            }
                                            int index = Integer.parseInt(args[4]);
                                            JsonArray array = gui.getCommandExecutor();
                                            for (JsonElement e : array) {
                                                JsonObject o = e.getAsJsonObject();
                                                if (o.get("slot").getAsInt() == slot && o.get("index").getAsInt() == index) {
                                                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandValue", "§aКоманда в слоте §f%s §aдля интерфейса §f%s §aимеет значение: §f%s"), String.valueOf(slot), tableName, o.get("command").getAsString());
                                                    return true;
                                                }
                                            }
                                            MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandNotFound", "§cКоманда в слоте §f%s §aдля интерфейса §f%s §cне найдена!"), String.valueOf(slot), tableName);
                                        }
                                    } else {
                                        MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("InterfaceNotFound", "§cИнтерфейс не найден!"));
                                    }
                                } else {
                                    MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandGetError", "§cИспользуйте: §f/gui command get <слот> <имя_интерфейса>"));
                                }
                            } else {
                                MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandGetError", "§cИспользуйте: §f/gui command get <слот> <имя_интерфейса>"));
                            }
                        }
                        default -> MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("CommandSetCommandError", "§cИспользуйте: §f/gui command [add/get/delete] <слот> <имя_интерфейса> \"<команда>\""));
                    }
                } else {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("CommandSetCommandError", "§cИспользуйте: §f/gui command [add/get/delete] <слот> <имя_интерфейса> \"<команда>\""));
                }
            }
            case "reload" -> {
                Player player = (Player) sender;
                if(!player.hasPermission("customgui.reload")) {
                    MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("NoPermission", "§cУ вас нет прав для использования этой команды"));
                    return true;
                }
                // Перезагружаем переводы
                plugin.languageManager.reloadLanguage();
                MessageFormatter.sendMessage(player, CustomGuiReworked.languageMap.getOrDefault("ReloadSuccess", "§aПереводы перезагружены успешно!"));
            }
            default -> MessageFormatter.sendMessage(sender, CustomGuiReworked.languageMap.getOrDefault("WrongUsage", "§cНеверное использование, используйте: §f/gui [create/open/delete/edit/reload/command]"));
        }
        return true;
    }
    
    public static boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

