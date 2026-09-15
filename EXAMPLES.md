# CustomGuiReworked — примеры API

Отдельная подборка **готовых примеров**: от «первый GUI за 10 строк» до
полного «умного» блока (котёл, который варит без открытого GUI).
Полный справочник методов — в [`API.md`](API.md), внутренности — в
[`MECHANICS.md`](MECHANICS.md).

---

## Содержание

- [0. Что можно делать на API](#0-что-можно-делать-на-api)
- [1. Минимальное подключение](#1-минимальное-подключение)
- [2. Создать GUI в коде](#2-создать-gui-в-коде)
- [3. Открытие и хранилище](#3-открытие-и-хранилище)
- [4. Локальные оверрайды (per-зритель)](#4-локальные-оверрайды-per-зритель)
- [5. Функциональный блок: котёл (полный пример)](#5-функциональный-блок-котёл-полный-пример)
- [6. Анимация дизайн-слотов](#6-анимация-дизайн-слотов)
- [7. События API](#7-события-api)
- [8. Skript и Denizen](#8-skript-и-denizen)

---

## 0. Что можно делать на API

| Задача | API | Пример |
|---|---|---|
| Создать / изменить / удалить GUI | `GuiBuilder`, `registerGui`, `unregisterGui`, `deleteGui`, `loadGui`, `saveGui` | [§2](#2-создать-gui-в-коде) |
| Открыть GUI (в т.ч. на блоке, временно) | `openGui(player, name[, block[, StorageType]])`, `getOpenGui` | [§3](#3-открытие-и-хранилище) |
| Читать/писать хранилище без открытого GUI | `readStorage`, `writeStorage`, `deleteStorage` | [§3](#3-открытие-и-хранилище) |
| Название/дизайн окна **только для одного игрока** | `setLocalTitle`, `setLocalDesign(s)`, `clearLocalDesign`, `clearAllLocalDesigns`, `getLocalDesign` (+ per-блок варианты) | [§4](#4-локальные-оверрайды-per-зритель) |
| Блок → GUI (ПКМ по ItemsAdder/CraftEngine блоку) | `registerBlockGui`, `FunctionalBlock.builder(...).gui(...)` | [§5](#5-функциональный-блок-котёл-полный-пример) |
| Блок «работает» без открытого GUI (варка) | `setWorking(block, true)`, `isWorking(block)`, `.onBlockTick(...)` | [§5](#5-функциональный-блок-котёл-полный-пример) |
| Персистентные данные блока (прогресс, флаги) | `blockData(block)` / `blockData(blockId, block)` → `FunctionalBlockData` | [§5](#5-функциональный-блок-котёл-полный-пример) |
| Предметы слотов блока при закрытом GUI | `getBlockSlotItem`, `setBlockSlotItem`, `consumeBlockSlotItem` | [§5](#5-функциональный-блок-котёл-полный-пример) |
| Крафт / топливо / результат | `matchesCraft`, `consumeFuel`, `produceResult`, `CraftingRecipe` | [§5](#5-функциональный-блок-котёл-полный-пример) |
| Стрелка прогресса / анимация дизайна | `DesignAnimation`, `DesignAnimation.stageForProgress` | [§6](#6-анимация-дизайн-слотов) |
| Кто смотрит блок / на каком блоке игрок | `getViewers(block)`, `getOpenBlockLocation(player)` | [§5](#5-функциональный-блок-котёл-полный-пример) |
| «Предмет положен/забран в слот» (фактические «было/стало») | `GuiSlotChangedEvent`, `.onItemChanged(...)` | [§5, §7](#7-события-api) |
| Клики / открытие / закрытие / drag | `GuiSlotClickEvent`, `GuiOpenEvent`, `GuiCloseEvent`, `GuiDragEvent` | [§7](#7-события-api) |
| Скриптовая настройка без Java | Skript-события/эффекты/выражения, Denizen-события/теги/`adjust` | [§8](#8-skript-и-denizen) |

---

## 1. Минимальное подключение

Правила (подробно — [`API.md#1`](API.md#1-подключение-зависимости)):
`compileOnly` / `provided` (НЕТ shade), `softdepend: [CustomGuiReworked]`,
вызовы API — из `onEnable` и позже.

```java
// build.gradle
compileOnly 'com.github.amper24:CustomGuiReworked:2.4.0'   // JitPack

// plugin.yml
// softdepend: [CustomGuiReworked]
```

```java
public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Без compileOnly-зависимости (рефлексия/Bukkit Services):
        // GuiService service = Bukkit.getServicesManager()
        //         .load(GuiService.class).stream().findFirst().orElse(null);
        if (!CustomGuiAPI.isInitialized()) {
            getLogger().warning("CustomGuiReworked не найден — интеграция выключена");
            return;
        }
        // теперь API готов:
        CustomGuiAPI.registerGui(buildGui(), true);
    }

    @Override
    public void onDisable() {
        CustomGuiAPI.unregisterGui("shop", true);
    }
}
```

---

## 2. Создать GUI в коде

```java
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

Gui gui = CustomGuiAPI.builder("shop")          // имя: [a-z0-9_-]
        .title("§6Магазин")                     // legacy-коды § поддерживаются
        .size(27)                               // 9..54, кратно 9 — ДО slot()/design()!
        .storage(StorageType.PERSONAL)          // block | personal | global | team | temporary
        .slots(List.of(10, 11, 12, 13), SlotType.CONTAINER) // хранилище (персистится)
        .slot(22, SlotType.RESULT)              // только забор
        .slot(14, SlotType.CRAFT)               // семантика «крафта»
        .slot(20, SlotType.FUEL)                // «топливо»
        .design(0, new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
        .command(4, "say %player% открыл магазин", 0)        // команда слота
        .command(22, "give %player% diamond 1", 5)           // задержка 5 тиков
        .blockId("myblocks:shop_block")                  // ПКМ по блоку откроет GUI
        .build();

CustomGuiAPI.registerGui(gui, true);   // true → файл custom/shop.yml (переживёт рестарт)
// registerGui(gui) — тоже persist=true; false — только в памяти
```

Полезное:

```java
CustomGuiAPI.getGui("shop");                  // Gui или null
CustomGuiAPI.guiExists("shop");
CustomGuiAPI.getGuiNames();                   // Set<String>
CustomGuiAPI.sourceOf("shop");                // "table" | "custom" | "runtime" | "none"
CustomGuiAPI.loadGui("shop");                 // перечитать файл
CustomGuiAPI.deleteGui("shop");               // удалить файл (данные хранилища не трогает)
CustomGuiAPI.saveGui(gui);                    // сохранить изменения модели в файл
CustomGuiAPI.createGui("fresh");              // GUI по умолчанию (27 слотов, все DESIGN)

// Изменить существующий GUI:
Gui shop = CustomGuiAPI.getGui("shop");
shop.setSlotType(15, SlotType.CONTAINER);
shop.setDesignItem(0, new ItemStack(Material.RED_STAINED_GLASS_PANE));
shop.addCommand(new SlotCommand(15, "msg %player% Куплено!", 0));
CustomGuiAPI.saveGui(shop);
```

---

## 3. Открытие и хранилище

```java
// Обычное открытие (тип хранилища — из GUI):
CustomGuiAPI.openGui(player, "shop");

// BLOCK-GUI — локация блока (данные живут «world:x,y,z»):
CustomGuiAPI.openGui(player, "furnace", blockLocation);

// Временно открыть чужое GUI как TEMPORARY (ничего не сохранится):
CustomGuiAPI.openGui(player, "shop", StorageType.TEMPORARY);

Gui open = CustomGuiAPI.getOpenGui(player);   // что открыт прямо сейчас (или null)
```

**Хранилище без открытого GUI** — прямой доступ к данным:

```java
// owner: PERSONAL → ник; TEAM → команда; BLOCK → "world:x,y,z"; GLOBAL → ""; TEMPORARY → UUID
List<ItemStack> items = CustomGuiAPI.readStorage(StorageType.GLOBAL, "", "shop");
items.set(0, new ItemStack(Material.DIAMOND, 8));
CustomGuiAPI.writeStorage(StorageType.GLOBAL, "", "shop", items);

// Данные блока (как если бы GUI было открыто на blockLocation):
List<ItemStack> blockItems = CustomGuiAPI.readStorage(
        StorageType.BLOCK, "world:10,64,20", "furnace");
```

`table` — имя таблицы (обычно имя GUI; суффикс `.yml` необязателен),
`items` — список по слотам, `null`/`AIR` = пусто.

---

## 4. Локальные оверрайды (per-зритель)

Подменяют название/дизайн **только в окне конкретного игрока**:
файл GUI, другие игроки и другие блоки не затрагиваются. Оверрайды
живут сессию и очищаются при закрытии GUI.

```java
// Название окна (поддерживает §):
CustomGuiAPI.setLocalTitle(player, "§6Печь 42%");
String title = CustomGuiAPI.getLocalTitle(player);   // null — название из файла
CustomGuiAPI.clearLocalTitle(player);                // вернуть название из файла

// Дизайн DESIGN/RESULT-слотов (null — вернуть дизайн из файла):
CustomGuiAPI.setLocalDesign(player, 4, progressItem);
CustomGuiAPI.setLocalDesigns(player, Map.of(4, arrow, 5, fire));
ItemStack shown = CustomGuiAPI.getLocalDesign(player, 4);  // null — дизайн из файла
CustomGuiAPI.clearLocalDesign(player, 4);      // один слот
CustomGuiAPI.clearAllLocalDesigns(player);     // все слоты сессии

// Per-блок: если у игрока открыто GUI НА ЭТОМ блоке (не даст перепутать сессию):
CustomGuiAPI.setLocalDesign(player, blockLocation, 4, progressItem);
CustomGuiAPI.setLocalTitle(player, blockLocation, "§6Котёл");
```

Типичные сценарии:

```java
// Стрелка прогресса (4 кадра) + огонь — видно только этому игроку:
List<ItemStack> arrow = List.of(arrow1, arrow2, arrow3, arrow4);
int stage = DesignAnimation.stageForProgress(cook, total, arrow.size()); // 0..3
CustomGuiAPI.setLocalDesign(player, 5, arrow.get(stage));
CustomGuiAPI.setLocalDesign(player, 20, isHeated ? fireIcon : null);
```

```java
// Бочка с жидкостью: уровень в 3 слотах (обычно в GuiOpenEvent/onTick):
CustomGuiAPI.setLocalDesigns(player, Map.of(10, fluid(level), 11, fluid(level), 12, fluid(level)));
```

Утилиты:

```java
Location block = CustomGuiAPI.getOpenBlockLocation(player); // на каком блоке GUI открыт (или null)
List<Player> viewers = CustomGuiAPI.getViewers(blockLocation); // кто смотрит блок прямо сейчас
ItemStack designSafe = CustomGuiAPI.prepareDesignItem(item);   // готовит предмет к дизайну (анти-дюп)
```

---

## 5. Функциональный блок: котёл (полный пример)

Самый важный паттерн: **состояние живёт на блоке, а не в GUI** —
игрок закрыл окно, варка продолжается (как ванильная печь). Составные
части: GUI (скелет + дизайн), `FunctionalBlock` (колбэки),
`blockData` (прогресс), `setWorking` + `onBlockTick` (варка без зрителя),
`setBlockSlotItem` (результат при закрытом GUI), локальные оверрайды
(стрелка/огонь).

**Макет GUI `cooking_pot` (27 слотов):**

```
0  1  2  3  4  5  6        DESIGN | CRAFT | CRAFT | CRAFT | DESIGN | DESIGN(стрелка) | DESIGN
7  8  9 10 11 12 13        RESULT(блюдо) | DESIGN | DESIGN | CRAFT | CRAFT | CRAFT | DESIGN
14 15 16 17 18 19 20       DESIGN | DESIGN | DESIGN | DESIGN | DESIGN | DESIGN | DESIGN(огонь)
21 22 23 24 25 26 27       DESIGN | CONTAINER(миска) | DESIGN | RESULT(готовое) | DESIGN...
```

| Слот | Тип | Назначение |
|---|---|---|
| 1, 2, 3 / 10, 11, 12 | `CRAFT` | ингредиенты (персистится) |
| 7 | `RESULT` | блюдо «в миску» |
| 22 | `CONTAINER` | личная миска игрока (персистится) |
| 24 | `RESULT` | готовый результат (только забор) |
| 5 | `DESIGN` | стрелка прогресса (локальный оверрайд) |
| 20 | `DESIGN` | огонь (локальный оверрайд / анимация) |

Полный плагин-пример:

```java
package com.example.cookingpot;

import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.animation.DesignAnimation;
import dev.moonaticks.customGuiReworked.api.functional.CraftingRecipe;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlock;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class CookingPotExamplePlugin extends JavaPlugin {

    private static final String BLOCK_ID = "farmersdelight:cooking_pot";
    private static final String GUI = "cooking_pot";
    private static final int TOTAL = 200; // тиков на варку

    /** Кадры стрелки (в реальности — предметы CraftEngine). */
    private final List<ItemStack> arrowFrames = List.of(
            arrow(Material.GRAY_STAINED_GLASS_PANE),
            arrow(Material.LIGHT_GRAY_STAINED_GLASS_PANE),
            arrow(Material.WHITE_STAINED_GLASS_PANE),
            arrow(Material.ORANGE_STAINED_GLASS_PANE));

    /** Циклическая анимация огня (DESIGN слот 20). */
    private DesignAnimation fireAnimation;

    @Override
    public void onEnable() {
        if (!CustomGuiAPI.isInitialized()) {
            getLogger().severe("CustomGuiReworked not found — plugin disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerGui();
        registerBlock();
    }

    // ================= GUI =================

    private void registerGui() {
        Gui gui = CustomGuiAPI.builder(GUI)
                .title("§6Котёл")
                .size(27)
                .storage(StorageType.BLOCK)                       // данные живут на блоке
                .slots(List.of(1, 2, 3, 10, 11, 12), SlotType.CRAFT)
                .slots(List.of(7, 24), SlotType.RESULT)
                .slots(List.of(22), SlotType.CONTAINER)
                .design(5, arrowFrames.get(0))                    // стрелка (0%)
                .design(20, new ItemStack(Material.BLAZE_ROD))    // «огонь выключен»
                .build();
        CustomGuiAPI.registerGui(gui, true);
    }

    // ================= Функциональный блок =================

    private void registerBlock() {
        fireAnimation = DesignAnimation.builder()
                .slot(20)
                .frames(List.of(
                        new ItemStack(Material.FIRE_CHARGE),
                        new ItemStack(Material.BLAZE_POWDER)))
                .intervalTicks(6)
                .loop(true)
                .build();

        FunctionalBlock.builder(BLOCK_ID)
                .gui(GUI)                                          // ПКМ по блоку → GUI
                .canOpen((player, block) -> player.hasPermission("cookingpot.use"))
                .onOpen((player, block, inv) -> {                  // окно ещё не показано
                    fireAnimation.startForBlock(block);            // огонь видят все зрители
                    paintNow(block, player);                       // отрисовать текущее состояние
                })
                .onTick((block, inv) -> {                          // per-зритель, каждые 5 тиков
                    for (Player viewer : CustomGuiAPI.getViewers(block)) {
                        paintNow(block, viewer);
                    }
                })
                .onItemChanged((player, block, slot, type, oldItem, newItem) -> {
                    // ФАКТУЧЕСКИЕ «было/стало» (null — пустой), следующий тик:
                    if (type == SlotType.CRAFT) {
                        // заложили/убрали ингредиент → пере-оценить, пора ли варить
                        CustomGuiAPI.setWorking(block, evaluateCookable(block));
                    }
                    if (type == SlotType.RESULT && slot == 24
                            && oldItem != null && newItem == null) {
                        // результат забрали: опыт + пере-оценка
                        FunctionalBlockData data = CustomGuiAPI.blockData(block);
                        player.giveExp(Math.max(1, (int) Math.round(data.getDouble("xp", 0))));
                        CustomGuiAPI.setWorking(block, evaluateCookable(block));
                    }
                })
                .onClose((player, block) ->
                        fireAnimation.stopForBlock(block))         // зрителей не осталось
                .onBlockTick((block, data) -> {                    // ВАРКА БЕЗ ОТКРЫТОГО GUI
                    if (!isHeated(block) || !evaluateCookable(block)) {
                        data.setInt("cook", 0);
                        return;
                    }
                    int cook = data.getInt("cook", 0) + 1;
                    data.setInt("total", TOTAL);
                    if (cook >= TOTAL) {
                        data.setInt("cook", 0);
                        data.setDouble("xp", 3.0);
                        // расходуем по ингредиенту (даже при закрытом GUI):
                        CustomGuiAPI.consumeBlockSlotItem(block, 1, 1);
                        CustomGuiAPI.consumeBlockSlotItem(block, 2, 1);
                        CustomGuiAPI.consumeBlockSlotItem(block, 3, 1);
                        // результат появляется и сохраняется; открытые зрители
                        // мгновенно перерисуются + GuiSlotChangedEvent:
                        CustomGuiAPI.setBlockSlotItem(block, 24, new ItemStack(Material.MILK_BUCKET));
                    } else {
                        data.setInt("cook", cook);
                    }
                })
                .craftingRecipe(CraftingRecipe.simple(            // опционально: рецепт+время
                        Map.of(1, new ItemStack(Material.BEEF)),
                        Map.of(24, new ItemStack(Material.COOKED_BEEF)),
                        TOTAL))
                .register();                                       // ID блока → GUI + реестр
    }

    // ================= Логика =================

    /** Стрелка прогресса + огонь — только для этого зрителя (локальные оверрайды). */
    private void paintNow(Location block, Player viewer) {
        FunctionalBlockData data = CustomGuiAPI.blockData(block);
        int cook = data.getInt("cook", 0);
        if (cook > 0) {
            int stage = DesignAnimation.stageForProgress(cook, TOTAL, arrowFrames.size());
            CustomGuiAPI.setLocalDesign(viewer, 5, arrowFrames.get(stage));
        } else {
            CustomGuiAPI.setLocalDesign(viewer, 5, null);          // дизайн из файла
        }
        // Огонь — у DesignAnimation (startForBlock), тут не трогаем.
    }

    /** Все три «главных» ингредиента заложены? */
    private boolean evaluateCookable(Location block) {
        return CustomGuiAPI.getBlockSlotItem(block, 1) != null
                && CustomGuiAPI.getBlockSlotItem(block, 2) != null
                && CustomGuiAPI.getBlockSlotItem(block, 3) != null;
    }

    /** В реальности — состояние плиты/магмы под блоком (Lightable/CE-state). */
    private boolean isHeated(Location block) {
        return block.getBlockY() > 0; // заглушка: всегда «горит»
    }

    private static ItemStack arrow(Material material) {
        // В реальном проекте — предметы-стрелки CraftEngine/ItemsAdder
        return new ItemStack(material);
    }
}
```

> Простой рецепт с топливом и временем — через `craftingRecipe` +
> `fuelConsumption` (см. [`API.md#15`](API.md#15-функциональные-блоки-печь-верстак-бочка-генератор)):

```java
FunctionalBlock.builder("my_furnace")
    .gui("furnace")
    .craftingRecipe(CraftingRecipe.of(
            Map.of(13, new ItemStack(Material.IRON_ORE)),          // CRAFT
            Map.of(22, new ItemStack(Material.IRON_INGOT)),        // RESULT
            Map.of(14, new ItemStack(Material.COAL)),              // FUEL
            600))                                                  // 30 секунд
    .fuelConsumption(Map.of(14, 1))                                // FUEL слот → 1 шт.
    .register();
```

Программа-минимум «крафт + топливо + результат» (без своего tикера):

```java
// в GuiSlotClickEvent / onItemChanged:
boolean ok   = CustomGuiAPI.matchesCraft(inventory, recipe);   // CRAFT = ингредиентам
int consumed = CustomGuiAPI.consumeFuel(inventory, 1);         // снять топливо
boolean gave = CustomGuiAPI.produceResult(inventory,
        Map.of(22, new ItemStack(Material.IRON_INGOT)));       // все-or-nothing в RESULT
```

**Как это складывается:**

1. **Зритель открыл GUI** — CRAFT/CONTAINER/RESULT слоты подгружаются из
   хранилища блока; `onOpen`/`onTick` рисуют стрелку и огонь через
   `setLocalDesign` (не трогает файл GUI и других зрителей).
2. **Игрок закрыл GUI** — `onBlockTick` продолжает тикать: прогресс копится
   в `blockData`, результат появляется через `setBlockSlotItem` и сохраняется.
3. **Результат готов, кто-то смотрит** — `setBlockSlotItem` мгновенно
   перерисовывает инвентарь зрителя и вызывает `GuiSlotChangedEvent`
   (old=null → new=result).
4. **Перезагрузка сервера** — данные и флаг «работает» восстанавливаются —
   варка продолжается с того же места.
5. **Блок разбили** — предметы выпадают, данные/работа удаляются,
   `onBlockBroken(block)` вызывается, per-блок анимации останавливаются.

Примечания:

- `setWorking(block, ...)` находит ID блока через CraftEngine; если ID
  известен — `setWorking(blockId, block, ...)` быстрее.
- `onBlockTick` вызывается только для загруженных чанков, каждые 5 тиков,
  на основном потоке.
- Кадры стрелки/огня — любые `ItemStack`: ванильные и кастомные
  (ItemsAdder/CraftEngine) — `setLocalDesign` не различает.

---

## 6. Анимация дизайн-слотов

`DesignAnimation` крутит кадры в DESIGN/RESULT-слотах через локальные
оверрайды: файл GUI не затрагивается, другие игроки видят обычный дизайн.

```java
DesignAnimation flame = DesignAnimation.builder()
        .slot(4, 5)                      // DESIGN-слот(ы)
        .frames(List.of(f1, f2, f3, f4)) // кадры (ItemStack)
        .intervalTicks(5)                // тиков между кадрами
        .loop(true)                       // по кругу (по умолчанию)
        .build();

flame.start(player);                      // per-плеер: в его GUI, свой прогресс
flame.startForBlock(blockLocation);       // per-блок: ОДИН общий прогресс для всех зрителей
flame.stopForPlayer(player);
flame.stopForBlock(blockLocation);
flame.stop();                             // все сессии
flame.isRunning();
```

Автостоп: закрытие инвентаря (per-блок — когда зрителей не осталось),
разрушение блока, конец кадров без `loop(true)`, явный `stop()`.

**Стрелка прогресса** (не цикл, а «сколько кадров показать сейчас»):

```java
int stage = DesignAnimation.stageForProgress(cook, total, arrowFrames.size());
// cook=0 → кадр 0; cook=total → последний кадр; иначе — пропорционально
CustomGuiAPI.setLocalDesign(viewer, 5, arrowFrames.get(stage));
```

---

## 7. События API

Все в `dev.moonaticks.customGuiReworked.api.event` — [каталог](API.md#7-события-api):

| Event | Когда | Cancellable | Ключевое |
|---|---|---|---|
| `GuiOpenEvent` | до `openInventory` | **да** | `getGui()`, `getInventory()`, `getStorageKey()`, `getStorageType()` (с учётом override) |
| `GuiCloseEvent` | после close + save | нет | `getGui()`, `getStorageKey()` |
| `GuiSlotClickEvent` | клик по верхнему инвентарю (включая DESIGN-кнопки) | два уровня | `getSlot()`, `getSlotType()`, `getClick()`, `getCurrentItem()`, `setCancelled()` (команды), `setInteractionCancelled()` (команды + ванильный клик) |
| `GuiDragEvent` | drag по слотам | **да** | `getTopSlots()` |
| `GuiSlotChangedEvent` | содержимое слота реально изменилось (след. тик) | нет | `getSlot()`, `getSlotType()`, `getOldItem()`, `getNewItem()` — фактические «было/стало» |

```java
@EventHandler
public void onOpen(GuiOpenEvent e) {
    if (!"shop".equals(e.getGui().name())) return;
    if (!e.getPlayer().hasPermission("shop.use")) {
        e.setCancelled(true);                       // окно не откроется
    }
}

@EventHandler
public void onClick(GuiSlotClickEvent e) {
    if (e.getSlotType() == SlotType.RESULT && e.getClick().isRightClick()) {
        e.setInteractionCancelled(true);            // ванильный клик отменён
        // кастомная логика продажи...
    }
}

// «Предмет положен/забран/перенесён» — без ручной диф-логики:
@EventHandler
public void onSlotChanged(GuiSlotChangedEvent e) {
    if (!"furnace".equals(e.getGui().name())) return;
    if (e.getSlotType() == SlotType.CRAFT) {
        // zaloжили/убрали ингредиент → запустить/остановить крафт
    }
    if (e.getSlotType() == SlotType.RESULT
            && e.getOldItem() != null && e.getNewItem() == null) {
        // результат забрали
    }
}
```

Особенности `GuiSlotChangedEvent`:

- срабатывает на **следующем тике** — `getOldItem()`/`getNewItem()` уже
  фактические предметы (в `GuiSlotClickEvent` инвентарь ещё «старый»);
- DESIGN-слоты не отслеживаются; локальные оверрайды/анимации не генерируют
  события (baseline синхронизируется вместе с ними);
- для функциональных блоков есть более ранний колбэк
  `FunctionalBlockHandler#onItemChanged` (вызывается ДО внешних слушателей).

---

## 8. Skript и Denizen

Полная справочная таблица — [`API.md#10`](API.md#10-skript-и-denizen).

### Skript

```skript
on cgui slot changed:
    # event-player, event-string (GUI), event-number (слот), event-location (блок)
    event-string is "cooking_pot"
    if event-number is 1, 2, 3, 10, 11, 12:
        set cgui working of event-location to true

# Локальные оверрайды / работа блока / данные / слоты:
set cgui local design of player at slot 5 to arrow stage item
set cgui local title of player to "&bКотёл 42%"
clear cgui local designs of player
set cgui working of location to true
if location is cgui working:
    # ...
set cgui block data of location key "cook" to "42"
set cgui block item at slot 24 of location to cooked stew
give player cgui block item at slot 24 of location
the cgui block of player            # локация блока, GUI которого открыт
all cgui viewers of location        # зрители блока
cgui progress stage of 51 out of 200 in 4 frames
cgui old item of event              # «было» в cgui slot changed
```

### Denizen

```denizen
on cgui slot changed:
    - if <context.gui> == cooking_pot && <context.slot> in 1, 2, 3, 10, 11, 12:
        - adjust <context.block> cgui_working:true
    # контексты: player, gui, slot, slot_type, block, old_item, new_item

# Изменения — механизмами (adjust):
- adjust <player> cgui_local_design:[5|arrow item]
- adjust <player> cgui_local_title:"&bКотёл 42%"
- adjust <player> cgui_clear_local_design
- adjust <loc> cgui_block_data:[cook|42]
- adjust <loc> cgui_block_item:[24|cooked stew]

# Чтение:
- if <cgui.working[<loc>]>: ...
- if <cgui.block_item[[<loc>]|24]> is an item: ...
- <cgui.block_of[<player>]>   # блок, GUI которого открыт
- <cgui.viewers[<loc>]>       # зрители
```

В этой линии Denizen (1.3.x) нет «mechanics» — запись делается
механизмами через `adjust`, «мульти-теги» принимают список (`|`),
локацию лучше писать в скобках: `<cgui.block_item[[<loc>]|24]>`.
