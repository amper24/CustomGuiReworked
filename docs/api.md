# API — что можно делать

CustomGuiReworked — это не только «окна с предметами». Публичный API
(`dev.moonaticks.customGuiReworked.api.*`) позволяет строить интерфейсы
с собственной логикой. Доступ — через Bukkit Services или фасад
`CustomGuiAPI` (см. [API.md#1–2](https://github.com/amper24/CustomGuiReworked/blob/main/API.md#1-подключение-зависимости)).

## Таблица возможностей

| Задача | API |
|---|---|
| Создать / изменить / удалить GUI | `GuiBuilder`, `registerGui(gui[, persist])`, `unregisterGui`, `deleteGui`, `loadGui`, `saveGui`, `createGui` |
| Открыть GUI | `openGui(player, name)`, `openGui(player, name, blockLocation)`, `openGui(player, name, StorageType)` — `getOpenGui(player)` |
| Хранилище без открытого GUI | `readStorage(type, owner, table)`, `writeStorage(...)`, `deleteStorage(...)` |
| **Название/дизайн окна только для одного игрока** | `setLocalTitle`, `getLocalTitle`, `clearLocalTitle`; `setLocalDesign`, `setLocalDesigns`, `clearLocalDesign`, `clearAllLocalDesigns`, `getLocalDesign` (+ per-блок варианты с `Location`) |
| ПКМ по кастомному блоку → GUI | `registerBlockGui(blockId, guiName)`, `FunctionalBlock.builder(blockId).gui(...)` |
| **Блок «работает» без открытого GUI** | `setWorking(block[, blockId], bool)`, `isWorking(block)` + колбэк `.onBlockTick((block, data) -> ...)` (каждые 5 тиков, даже без зрителей) |
| **Персистентные данные блока** (прогресс, флаги — переживают рестарт) | `blockData(block)` / `blockData(blockId, block)` → `FunctionalBlockData`: `getInt/setInt/setDouble/setBoolean/set/getString/...` |
| **Предметы слотов блока при закрытом GUI** | `getBlockSlotItem(block, slot)`, `setBlockSlotItem(block, slot, item)` (зрители перерисуются + событие), `consumeBlockSlotItem(block, slot, amount)` |
| Крафт / топливо / результат | `matchesCraft(inv, recipe)`, `consumeFuel(inv, amount)`, `produceResult(inv, results)`; `CraftingRecipe.simple/of` |
| Стрелка прогресса / анимация дизайна | `DesignAnimation` (кадры по кругу, per-плеер/per-блок), `DesignAnimation.stageForProgress(progress, total, stages)` |
| Кто смотрит блок / на каком блоке игрок | `getViewers(block)`, `getOpenBlockLocation(player)` |
| Анти-дюп дизайна | `prepareDesignItem(item)` |

## События

| Event | Когда | Cancellable |
|---|---|---|
| `GuiOpenEvent` | до `openInventory`; есть `getStorageKey()` (с учётом override) | да |
| `GuiCloseEvent` | после close + save | нет |
| `GuiSlotClickEvent` | клик по верхнему инвентарю (включая DESIGN-кнопки) | два уровня: `setCancelled()` (команды) / `setInteractionCancelled()` (+ ванильный клик) |
| `GuiDragEvent` | drag по слотам | да |
| `GuiSlotChangedEvent` | содержимое слота реально изменилось (следующий тик) — `getOldItem()`/`getNewItem()` фактические «было/стало» | нет |

## Минимальный пример: «первый GUI за 10 строк»

```java
// build.gradle: compileOnly 'com.github.amper24:CustomGuiReworked:2.4.0'
// plugin.yml:    softdepend: [CustomGuiReworked]

@Override
public void onEnable() {
    if (!CustomGuiAPI.isInitialized()) return;
    Gui gui = CustomGuiAPI.builder("shop")
            .title("§6Магазин")
            .size(27)
            .storage(StorageType.PERSONAL)
            .slots(List.of(10, 11, 12, 13), SlotType.CONTAINER)
            .slot(22, SlotType.RESULT)
            .design(0, new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            .command(4, "say %player% открыл магазин", 0)
            .build();
    CustomGuiAPI.registerGui(gui, true);
    // CustomGuiAPI.openGui(player, "shop");
}
```

## Пер-зритель оверрайды — «интерфейс живёт»

Стрелка прогресса, которая видна только одному игроку (файл GUI и
другие игроки не затрагиваются):

```java
List<ItemStack> arrow = List.of(a1, a2, a3, a4);
int stage = DesignAnimation.stageForProgress(cook, total, arrow.size());
CustomGuiAPI.setLocalDesign(player, 5, arrow.get(stage));
CustomGuiAPI.setLocalTitle(player, "§6Печь " + cook + "%");
```

## «Умный» блок (кратко)

Полный разбор — [Умный блок — котёл](smart-block.md), готовый код —
[EXAMPLES.md §5](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#5-функциональный-блок-котёл-полный-пример).

```java
FunctionalBlock.builder("farmersdelight:cooking_pot")
    .gui("cooking_pot")
    .onItemChanged((player, block, slot, type, old, newer) -> {
        if (type == SlotType.CRAFT)
            CustomGuiAPI.setWorking(block, evaluateCookable(block)); // запуск/стоп
    })
    .onBlockTick((block, data) -> {                       // варка БЕЗ открытого GUI
        int cook = data.getInt("cook", 0) + 1;
        if (cook >= 200) {
            data.setInt("cook", 0);
            CustomGuiAPI.setBlockSlotItem(block, 24, resultItem); // результат готов
        } else {
            data.setInt("cook", cook);
        }
    })
    .register();
```

Скрипты вместо Java — [Skript и Denizen](scripts.md), ещё примеры — [Примеры](examples.md).
