# Умный блок — котёл (пошагово)

Референсный пример «умного» блока: котёл, у которого есть ингредиенты,
прогресс, стрелка, огонь, топливо-логика и — главное — **варка
продолжается, когда игрок закрыл окно** (как ванильная печь).

Готовый плагин целиком: [EXAMPLES.md §5](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#5-функциональный-блок-котёл-полный-пример).
Скелет того же на Skript — [Skript и Denizen](scripts.md).

## Шаг 1. GUI (редактором или билдером)

`/gui create cooking_pot` (27 слотов) или в коде. Макет:

| Слот | Тип | Назначение |
|---|---|---|
| 1, 2, 3 / 10, 11, 12 | `CRAFT` | ингредиенты (персистятся на блок) |
| 7 | `RESULT` | блюдо «в миску» (только забор) |
| 22 | `CONTAINER` | миска игрока (персистится) |
| 24 | `RESULT` | готовый результат (только забор) |
| 5 | `DESIGN` | стрелка прогресса (локальный оверрайд) |
| 20 | `DESIGN` | огонь (локальный оверрайд / анимация) |
| остальное | `DESIGN` | рамка |

Важно: `StorageType = BLOCK` — данные живут на блоке
(`world:x,y,z`), а не у игрока.

```java
Gui gui = CustomGuiAPI.builder("cooking_pot")
        .title("§6Котёл")
        .size(27)
        .storage(StorageType.BLOCK)
        .slots(List.of(1, 2, 3, 10, 11, 12), SlotType.CRAFT)
        .slots(List.of(7, 24), SlotType.RESULT)
        .slots(List.of(22), SlotType.CONTAINER)
        .design(5, arrowFrame0)
        .design(20, new ItemStack(Material.BLAZE_ROD))
        .build();
CustomGuiAPI.registerGui(gui, true);
```

## Шаг 2. Функциональный блок (колбэки)

```java
FunctionalBlock.builder("farmersdelight:cooking_pot")
    .gui("cooking_pot")                       // ПКМ по блоку → GUI
    .canOpen((player, block) -> player.hasPermission("cookingpot.use"))
    .onOpen((player, block, inv) -> {
        fireAnimation.startForBlock(block);   // огонь (DesignAnimation)
        paintNow(block, player);              // отрисовать текущее состояние
    })
    .onTick((block, inv) ->                   // per-зритель, каждые 5 тиков
        for (Player v : CustomGuiAPI.getViewers(block)) paintNow(block, v))
    .onItemChanged((player, block, slot, type, oldItem, newItem) -> {
        // ФАКТУЧЕСКИЕ «было/стало» (null — пустой), следующий тик:
        if (type == SlotType.CRAFT)
            CustomGuiAPI.setWorking(block, evaluateCookable(block)); // запуск/стоп
        if (type == SlotType.RESULT && slot == 24
                && oldItem != null && newItem == null) {
            // результат забрали: опыт + пере-оценка
            player.giveExp((int) Math.round(CustomGuiAPI.blockData(block).getDouble("xp", 0)));
            CustomGuiAPI.setWorking(block, evaluateCookable(block));
        }
    })
    .onClose((player, block) -> fireAnimation.stopForBlock(block))
    .onBlockTick((block, data) -> {           // ВАРКА БЕЗ ОТКРЫТОГО GUI, каждые 5 тиков
        if (!isHeated(block) || !evaluateCookable(block)) { data.setInt("cook", 0); return; }
        int cook = data.getInt("cook", 0) + 1;
        data.setInt("total", 200);
        if (cook >= 200) {
            data.setInt("cook", 0);
            data.setDouble("xp", 3.0);
            CustomGuiAPI.consumeBlockSlotItem(block, 1, 1);
            CustomGuiAPI.consumeBlockSlotItem(block, 2, 1);
            CustomGuiAPI.consumeBlockSlotItem(block, 3, 1);
            CustomGuiAPI.setBlockSlotItem(block, 24, new ItemStack(Material.MILK_BUCKET));
        } else {
            data.setInt("cook", cook);
        }
    })
    .register();
```

## Шаг 3. Отрисовка (стрелка + огонь) — только для зрителя

```java
List<ItemStack> arrow = List.of(arrow1, arrow2, arrow3, arrow4); // CE-предметы

void paintNow(Location block, Player viewer) {
    FunctionalBlockData data = CustomGuiAPI.blockData(block);
    int cook = data.getInt("cook", 0);
    if (cook > 0) {
        int stage = DesignAnimation.stageForProgress(cook, 200, arrow.size());
        CustomGuiAPI.setLocalDesign(viewer, 5, arrow.get(stage));
    } else {
        CustomGuiAPI.setLocalDesign(viewer, 5, null);  // дизайн из файла
    }
}
```

`setLocalDesign` — пер-зритель оверрайд: файл GUI, другие игроки и
другие блоки не затрагиваются; при закрытии окна оверрайды очищаются.
Кадры — любые предметы: ванильные `Material` и кастомные
CraftEngine/ItemsAdder.

Циклическая анимация (огонь) — `DesignAnimation`:

```java
fireAnimation = DesignAnimation.builder()
        .slot(20)
        .frames(List.of(fireCharge, blazePowder))
        .intervalTicks(6)
        .loop(true)
        .build();
fireAnimation.startForBlock(block);   // в onOpen; stopForBlock — в onClose
```

## Что где живёт

| Состояние | Где | Почему |
|---|---|---|
| Ингредиенты/миска/результат | хранилище блока (`CRAFT`/`CONTAINER`/`RESULT` слоты) | персистится по слотам, анти-дюп |
| Прогресс, «total», xp | `blockData(block)` (`FunctionalBlockData`, KV) | живёт без GUI, переживает рестарт |
| «Варится ли» | `setWorking(block, true/false)` + флаг в данных | тикер знает, кого гонять |
| Стрелка/огонь | локальные оверрайды (`setLocalDesign`) | per-зритель, не трогает файл |

## Как это складывается

1. **Зритель открыл GUI** — слоты подгружаются из хранилища блока;
   `onOpen`/`onTick` рисуют стрелку и огонь.
2. **Игрок закрыл GUI** — `onBlockTick` продолжает тикать: прогресс
   копится в `blockData`, результат появляется через `setBlockSlotItem`.
3. **Результат готов, кто-то смотрит** — `setBlockSlotItem` мгновенно
   перерисовывает зрителя и вызывает `GuiSlotChangedEvent`
   (old=null → new=result).
4. **Перезагрузка сервера** — данные и флаг «работает» восстанавливаются,
   варка продолжается с того же места.
5. **Блок разбили** — предметы выпадают, данные и работа удаляются,
   `onBlockBroken(block)` вызывается, анимации останавливаются.

## Примечания

- `setWorking(block, ...)` находит ID блока через CraftEngine; если ID
  известен явно — `setWorking(blockId, block, ...)` быстрее.
- `onBlockTick` — только загруженные чанки, основной поток, каждые 5 тиков.
- `isHeated`-логика (магма/плита/костёр под блоком) — ваша.
- Тот же паттерн работает для печи, верстака, бочки (уровни жидкости —
  `setLocalDesigns`), генератора (энергия в `blockData`).
- Без Java — [Skript-скетч котла](https://github.com/amper24/CustomGuiReworked/blob/main/API.md#17-работа-блока-без-открытого-gui-котёлпечь-варят-без-зрителя)
  и [Skript и Denizen](scripts.md).
