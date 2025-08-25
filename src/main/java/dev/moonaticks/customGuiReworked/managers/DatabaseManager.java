package dev.moonaticks.customGuiReworked.managers;

import com.google.gson.*;
import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import dev.moonaticks.customGuiReworked.tools.ItemDrops;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

public class DatabaseManager {
    private final File playersFolder;
    private final File globalsFolder;
    private final File teamsFolder;
    private final ItemDrops itemDrops;
    CustomGuiReworked plugin;
    public DatabaseManager(File pluginDataFolder, ItemDrops itemDrops, CustomGuiReworked plugin) {
        this.itemDrops = itemDrops;
        this.plugin = plugin;

        File dataFolder = new File(pluginDataFolder, "data");
        this.playersFolder = new File(dataFolder, "players");
        this.globalsFolder = new File(dataFolder, "globals");
        this.teamsFolder = new File(dataFolder, "teams");

        // Создаем структуру папок
        if (!dataFolder.exists()){dataFolder.mkdirs();}
        if (!playersFolder.exists()) {playersFolder.mkdirs();}
        if (!globalsFolder.exists()) {globalsFolder.mkdirs();}
        if (!teamsFolder.exists()) {teamsFolder.mkdirs();}
    }
    public void saveToBlock(Location loc, String tableName, JsonArray storage) {
        if (storage == null) {
                plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("FailedToSaveNullStorage", "Failed to save null storage to block!"));
                return;
            }
        if (loc == null) {
                plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("PlayerDidNotClickBlock", "Игрок не кликнул по блоку!"));
                return;
            }

        World world = loc.getWorld();
        if (world == null) {
                plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("WorldNotFoundForBlock", "Мир для блока не найден!"));
                return;
            }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        int xRegion = x >> 4;
        int zRegion = z >> 4;
        
        // Сохраняем в папку мира
        File blocksFolder = getWorldBlocksFolder(world);
        File regionFolder = new File(blocksFolder, xRegion + "_" + zRegion + ".json");
        // Загружаем данные региона
        JsonObject regionData = loadRegionData(regionFolder);
        JsonObject blockData = new JsonObject();
        blockData.addProperty("table", tableName);
        blockData.add("storage", storage);
        // Добавляем мир в ключ
        String key = world.getName() + ":" + x + "," + y + "," + z;
        regionData.add(key, blockData);
        try {
            // Сохраняем данные региона
            saveRegionData(regionFolder, regionData);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorSavingDataToBlock", "Error saving data to block: ") + e.getMessage());
            e.printStackTrace();
        }
    }
    public void saveToPlayer(Player player, String tableName, JsonArray storage) {
        if (storage == null) {
            plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("AttemptToSaveNullStorageForPlayer", "Попытка сохранить null storage для игрока!"));
            return;
        }
        // Создаем файл для игрока
        File playerFile = new File(playersFolder, player.getName() + "_" + tableName);
        try {
            saveDataToFile(playerFile, storage);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorSavingPlayerData", "Ошибка при сохранении данных игрока: ") + e.getMessage());
            e.printStackTrace();
        }
    }
    public void saveGlobally(String tableName, JsonArray storage) {
        if (storage == null) {
            plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("AttemptToSaveNullStorageGlobally", "Попытка сохранить null storage глобально!"));
            return;
        }
        // Создаем глобальный файл
        File globalFile = new File(globalsFolder, tableName);
        try {
            saveDataToFile(globalFile, storage);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorSavingGlobalData", "Ошибка при глобальном сохранении данных: ") + e.getMessage());
            e.printStackTrace();
        }
    }
    public void saveToTeam(Player player, String tableName, JsonArray storage) {
        if (storage == null) {
            plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("AttemptToSaveNullStorageForTeam", "Попытка сохранить null storage для команды!"));
            return;
        }
        // Получаем название команды
        String team = player.getScoreboard().getPlayerTeam(player) != null
                ? Objects.requireNonNull(player.getScoreboard().getPlayerTeam(player)).getName()
                : "defaultTeam_GUARD265";
        // Создаем файл для команды
        File teamFile = new File(teamsFolder, team + "_" + tableName);
        try {
            saveDataToFile(teamFile, storage);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorSavingTeamData", "Ошибка при сохранении данных команды: ") + e.getMessage());
            e.printStackTrace();
        }
    }

    public JsonArray loadFromBlock(Location loc, String tableName) {
        try {
            // Получаем координаты блока из слушателя
            if (loc == null) {
                plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("PlayerDidNotClickBlock", "Игрок не кликнул по блоку!"));
                return new JsonArray();
            }

            // Получаем мир
            World world = loc.getWorld();
            if (world == null) {
                plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("WorldNotFoundForBlock", "Мир для блока не найден!"));
                return new JsonArray();
            }

            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            // Вычисляем регион
            int xRegion = x >> 4;
            int zRegion = z >> 4;
            
            // Загружаем из папки мира
            File blocksFolder = getWorldBlocksFolder(world);
            File regionFile = new File(blocksFolder, xRegion + "_" + zRegion + ".json");

            // Загружаем данные региона
            JsonObject regionData = loadRegionData(regionFile);

            // Добавляем мир в ключ
            String key = world.getName() + ":" + x + "," + y + "," + z;

            if (regionData.has(key)) {
                JsonObject blockData = regionData.getAsJsonObject(key);
                if (blockData.get("table").getAsString().equals(tableName)) {
                    return blockData.getAsJsonArray("storage");
                }
            }
            return new JsonArray(); // Возвращаем пустой массив, если данные не найдены
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorLoadingBlockData", "Ошибка при загрузке данных блока: ") + e.getMessage());
            e.printStackTrace();
            return new JsonArray();
        }
    }
    public JsonArray loadFromPlayer(Player player, String tableName) {
        try {
            File playerFile = new File(playersFolder, player.getName() + "_" + tableName);
            return loadDataFromFile(playerFile);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorLoadingPlayerData", "Ошибка при загрузке данных игрока: ") + e.getMessage());
            e.printStackTrace();
            return new JsonArray();
        }
    }
    public JsonArray loadGlobally(String tableName) {
        try {
            File globalFile = new File(globalsFolder, tableName);
            return loadDataFromFile(globalFile);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorLoadingGlobalData", "Ошибка при загрузке глобальных данных: ") + e.getMessage());
            e.printStackTrace();
            return new JsonArray();
        }

    }
    public JsonArray loadFromTeam(Player player, String tableName) {
        try {
            // Получаем название команды
            String team = player.getScoreboard().getPlayerTeam(player) != null
                    ? Objects.requireNonNull(player.getScoreboard().getPlayerTeam(player)).getName()
                    : "defaultTeam_GUARD265";

            File teamFile = new File(teamsFolder, team + "_" + tableName);
            return loadDataFromFile(teamFile);
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorLoadingTeamData", "Ошибка при загрузке данных команды: ") + e.getMessage());
            e.printStackTrace();
            return new JsonArray();
        }
    }

    private void saveDataToFile(File file, JsonArray data) {
        try {
            // Если данные пустые, удаляем файл
            if (data == null || data.size() == 0) {
                if (file.exists()) {
                    file.delete();
                }
                return;
            }
            
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(data.toString());
            }
        } catch (IOException e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorSavingDataToFile", "Ошибка при сохранении данных в файл: ") + e.getMessage());
            e.printStackTrace();
        }
    }
    private JsonArray loadDataFromFile(File file) {
        try {
            if (!file.exists()) {
                return new JsonArray(); // Возвращаем пустой массив, если файл не существует
            }

            String content = Files.readString(file.toPath());
            if (content == null || content.trim().isEmpty()) {
                return new JsonArray(); // Возвращаем пустой массив, если файл пустой
            }

            JsonElement jsonElement = JsonParser.parseString(content);
            if (jsonElement.isJsonArray()) {
                return jsonElement.getAsJsonArray();
            } else {
                throw new IllegalArgumentException("Некорректный формат данных в файле: " + file.getName());
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorLoadingDataFromFile", "Ошибка при загрузке данных из файла: ") + e.getMessage());
            e.printStackTrace();
            return new JsonArray(); // Возвращаем пустой массив в случае ошибки
        }
    }
    private JsonObject loadRegionData(File regionFile) {
        try {
            if (!regionFile.exists()) {
                return new JsonObject(); // Возвращаем пустой объект, если файл не существует
            }

            String content = Files.readString(regionFile.toPath());
            if (content == null || content.trim().isEmpty()) {
                return new JsonObject(); // Возвращаем пустой объект, если файл пустой
            }

            JsonElement jsonElement = JsonParser.parseString(content);
            if (!jsonElement.isJsonObject()) {
                plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("ExpectedJsonObjectButGotOtherType", "Ошибка: Ожидался JSON-объект, но получен другой тип в файле ") + regionFile.getName());
                return new JsonObject();
            }

            return jsonElement.getAsJsonObject();
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorLoadingRegionData", "Ошибка при загрузке данных региона: ") + e.getMessage());
            e.printStackTrace();
            return new JsonObject();
        }
    }
    /**
     * Получить папку блоков для указанного мира
     * @param world мир
     * @return папка блоков
     */
    private File getWorldBlocksFolder(World world) {
        File worldDataFolder = new File(world.getWorldFolder(), "CustomGuiReworked");
        File blocksFolder = new File(worldDataFolder, "blocks");
        if (!blocksFolder.exists()) {
            blocksFolder.mkdirs();
        }
        return blocksFolder;
    }
    
    private void saveRegionData(File regionFile, JsonObject data) {
        try {
            // Если данные пустые, удаляем файл
            if (data == null || data.size() == 0) {
                if (regionFile.exists()) {
                    regionFile.delete();
                }
                return;
            }

            // Гарантируем, что родительская папка существует
            File parentFolder = regionFile.getParentFile();
            if (parentFolder != null && !parentFolder.exists()) {
                parentFolder.mkdirs();
            }

            // Используем `BufferedWriter` для построчной записи
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(regionFile), StandardCharsets.UTF_8))) {
                Gson gson = new GsonBuilder().disableHtmlEscaping().create(); // `disableHtmlEscaping` для корректной обработки JSON

                writer.write("{\n"); // Открываем JSON-объект

                int count = 0;
                int total = data.size();

                for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                    writer.write("  \"" + entry.getKey() + "\": " + gson.toJson(entry.getValue()));
                    count++;
                    if (count < total) writer.write(","); // Запятая после каждой строки, кроме последней
                    writer.write("\n");
                }
                    writer.write("}"); // Закрываем JSON-объект
            }
        } catch (IOException e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorSavingRegionData", "Ошибка при сохранении данных региона: ") + e.getMessage());
                e.printStackTrace();
        }
    }
    public void removeBlockData(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            plugin.getLogger().warning(CustomGuiReworked.languageMap.getOrDefault("WorldNotFoundForBlock", "Мир для блока не найден!"));
            return;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        int xRegion = x >> 4;
        int zRegion = z >> 4;
        
        // Удаляем из папки мира
        File blocksFolder = getWorldBlocksFolder(world);
        File regionFile = new File(blocksFolder, xRegion + "_" + zRegion + ".json");

        JsonObject regionData = loadRegionData(regionFile);
        String key = world.getName() + ":" + x + "," + y + "," + z;
        try {
            if (regionData.has(key)) {
                JsonArray storage = regionData.getAsJsonObject(key).getAsJsonArray("storage");
                itemDrops.dropItems(loc, storage); // Выбрасываем предметы на локацию
                regionData.remove(key);
                saveRegionData(regionFile, regionData); // Сохраняем изменения
            }
        } catch (Exception e) {
            plugin.getLogger().severe(CustomGuiReworked.languageMap.getOrDefault("ErrorDeletingBlockData", "Ошибка при удалении данных блока: ") + e.getMessage());
            e.printStackTrace();
        }
    }
}