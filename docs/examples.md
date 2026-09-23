# Примеры

Главный файл готовых примеров —
**[EXAMPLES.md](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md)**
в корне репозитория. Структура:

| Раздел | Что внутри |
|---|---|
| [0. Что можно делать на API](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#0-что-можно-делать-на-api) | таблица «задача → метод → пример» |
| [1. Минимальное подключение](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#1-минимальное-подключение) | зависимости, `softdepend`, проверка `onEnable` |
| [2. Создать GUI в коде](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#2-создать-gui-в-коде) | билдер: тип слотов, дизайн, команды слотов, блоки; изменение существующего GUI |
| [3. Открытие и хранилище](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#3-открытие-и-хранилище) | `openGui` (в т.ч. per-блок, TEMPORARY), `read/writeStorage` без GUI |
| [4. Локальные оверрайды (per-зритель)](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#4-локальные-оверрайды-per-зритель) | title/design только для одного игрока, стрелка прогресса, бочка с жидкостью |
| [5. Функциональный блок: котёл](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#5-функциональный-блок-котёл-полный-пример) | **полный плагин целиком**: GUI + колбэки + варка без открытого GUI |
| [6. Анимация дизайн-слотов](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#6-анимация-дизайн-слотов) | `DesignAnimation` (per-плеер/per-блок), `stageForProgress` |
| [7. События API](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#7-события-api) | 5 событий, `GuiSlotChangedEvent` «было/стало» |
| [8. Skript и Denizen](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#8-skript-и-denizen) | скриптовые примеры |
| [9. Категории и пользовательские типы слотов](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#9-категории-и-пользовательские-типы-слотов) | **готовый Java-аддон**: `GuiCategory`, фильтр руды, `watch`, индикатор, `GuiOpenEvent` |

## Категории и расширяемые слоты

В [EXAMPLES.md §9](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md#9-категории-и-пользовательские-типы-слотов)
приведён полный класс аддона: создание категории «Механизмы»,
регистрация namespaced слотов `myaddon:ore` и `myaddon:indicator`,
фильтрация вставки, направленная связь `indicator.watch(ore)` и
`CustomGuiAPI.setSlotItem(...)` для обновления **незабираемого**
индикатора. `GuiOpenEvent` рассчитывает его при открытии сохранённого
меню, а `onDisable` безопасно отключает правила слотов.

Новый API доступен начиная с **`2.4.5`**. Нужна одинаковая версия как
`compileOnly` и на сервере. Формат YAML, ограничения, порядок callback'ов и миграция
`SlotType enum → class` разобраны в
[API.md §4.1–4.2](https://github.com/amper24/CustomGuiReworked/blob/main/API.md#41-категории-в-gui-и-редакторе).

## Топ-3 примера (кратко)

**1. Первый GUI:**

```java
Gui gui = CustomGuiAPI.builder("shop")
        .title("§6Магазин").size(27)
        .storage(StorageType.PERSONAL)
        .slots(List.of(10, 11, 12, 13), SlotType.CONTAINER)
        .slot(22, SlotType.RESULT)
        .design(0, new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
        .command(4, "say %player% открыл магазин", 0)
        .build();
CustomGuiAPI.registerGui(gui, true);
CustomGuiAPI.openGui(player, "shop");
```

**2. Стрелка прогресса, видная только игроку:**

```java
int stage = DesignAnimation.stageForProgress(cook, total, arrow.size());
CustomGuiAPI.setLocalDesign(player, 5, arrow.get(stage));
CustomGuiAPI.setLocalTitle(player, "§6Печь " + cook + "%");
```

**3. Блок варит без открытого GUI:**

```java
CustomGuiAPI.setWorking(block, true);        // запуск серверной работы
// в .onBlockTick((block, data) -> ...):
data.setInt("cook", data.getInt("cook", 0) + 1);
if (data.getInt("cook", 0) >= 200) {
    data.setInt("cook", 0);
    CustomGuiAPI.setBlockSlotItem(block, 24, resultItem); // результат готов
}
```

Полные версии всех примеров — в [EXAMPLES.md](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md).
Пошаговый разбор «умного» блока — [Умный блок — котёл](smart-block.md).
Скрипты вместо Java — [Skript и Denizen](scripts.md).
