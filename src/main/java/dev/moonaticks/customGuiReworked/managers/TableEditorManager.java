package dev.moonaticks.customGuiReworked.managers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.tools.Gui;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TableEditorManager {
    CustomGuiReworked plugin;
    public TableEditorManager(CustomGuiReworked pl, File dir) {
        this.plugin = pl;
        this.dir = new File(dir, "tables");
    }
    File dir;
    public final List<Gui> guis = new ArrayList<>();
    public final Map<String, Gui> blocksToGui = new ConcurrentHashMap<>();


    public final Map<UUID, Gui> lastGui = new HashMap<>();

    public void loadAllGuis() {
        guis.clear();
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".yml"));
        if(files == null) return;
        for(File file : files) {
            guis.add(loadFromFile(file.getName()));
        }
    }
    public void addGui(Gui gui) {
        guis.add(gui);
    }
    public void removeGui(Gui gui) {
        guis.remove(gui);
    }
    public void guisClear(){
        guis.clear();
    }
    public Gui getGuiByFile(String filename) {
        for(Gui gui : guis) {
            if(gui.getFile().equals(filename)) return gui;
        }

        return null;
    }
    //Сохранение данных в json
    public void saveToFile(Gui gui) {
        if(gui == null) return;
        File file = new File(dir, gui.getFile());
        YamlConfiguration config = new YamlConfiguration();
        boolean m = file.getParentFile().mkdirs(); // Создание папки, если её нет
        if(m) {
            plugin.getLogger().info(CustomGuiReworked.languageMap.getOrDefault("FolderCreated", "GUI folder created!"));
        }
        if (gui.getFile() == null || gui.getFile().isEmpty()) {
            plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("GuiFilePathNotSet", "GUI file path is not set!"));
            return;
        }
        // переделать на yml
        // Basic properties
        config.set("file", gui.getFile());
        config.set("slots", gui.getSlots());
        config.set("title", gui.getTitle());
        config.set("saveDataMethod", gui.getSaveDataMethode());

        // Skeleton list
        config.set("skeleton", gui.getSkeleton());

        // Custom block IDs
        config.set("customBlockIDs", gui.getCustomBlockIDs());

        // Design (JsonArray)
        JsonArray design = gui.getDesign();
        List<String> designItems = new ArrayList<>();
        for (JsonElement element : design) {
            designItems.add(element.toString());
        }
        config.set("design", designItems);

        // Command executor (JsonArray)
        JsonArray commands = gui.getCommandExecutor();
        List<String> commandList = new ArrayList<>();
        for (JsonElement element : commands) {
            commandList.add(element.toString());
        }
        config.set("commandExecutor", commandList);

        // Save to file
        try {
            config.save(file);
        } catch (Exception ignored) {}
    }
    public Gui createFile(String name) {
        Gui gui = new Gui(name);
        addGui(gui);
        return gui;
    }
    public Gui loadFromFile(String fileName) {
        File file = new File(dir, fileName);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Gui gui = new Gui(fileName);
        if (!file.exists()) {
            return gui;
        }
        //загрузка из yml
        // Basic properties
        gui.setSlots(config.getInt("slots", 27));
        gui.setTitle(config.getString("title", "Change this"));
        gui.setSaveDataMethode(config.getInt("saveDataMethod", 5));

        // Skeleton list
        if (config.contains("skeleton")) {
            gui.setSkeleton(config.getStringList("skeleton"));
        }
        else {
            // Initialize default skeleton if not present
            List<String> defaultSkeleton = new ArrayList<>();
            for (int i = 0; i < gui.getSlots(); i++) {
                defaultSkeleton.add("design" + i);
            }
            gui.setSkeleton(defaultSkeleton);
        }
        // Custom block IDs
        if (config.contains("customBlockIDs")) {
            gui.setCustomBlockIDs(config.getStringList("customBlockIDs"));
        }

        // Design (JsonArray)
        if (config.contains("design")) {
            JsonArray designArray = new JsonArray();
            for (String itemJson : config.getStringList("design")) {
                designArray.add(JsonParser.parseString(itemJson));
            }
            gui.setDesign(designArray);
        } else {
            // Initialize default design if not present
            gui.resetDesign();
        }
        // Command executor (JsonArray)
        if (config.contains("commandExecutor")) {
            JsonArray commandArray = new JsonArray();
            for (String commandJson : config.getStringList("commandExecutor")) {
                commandArray.add(JsonParser.parseString(commandJson));
            }
            gui.setCommandExecutor(commandArray);
        }

        return gui;
    }

    public void loadAllBlocks() {
        blocksToGui.clear();
        guis.forEach(gui -> {
            List<String> array = gui.getCustomBlockIDs();
            array.forEach(id -> blocksToGui.put(id, gui));
        });
    }

}
