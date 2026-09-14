# CustomGuiReworked — механики

## Скелет (тип слотов)

Каждый GUI описывает **скелет** — тип для каждого слота:

| Тип | Поведение |
|---|---|
| `DESIGN` | декорация; содержимое из `design` GUI; клики запрещены |
| `CONTAINER` | полноценный слот хранилища |
| `CRAFT` | слот «крафта» (семаантика для кастомных систем) |
| `RESULT` | предметы можно только забирать (постановка блокируется, включая drag) |
| `FUEL` | как CONTAINER, для механик «топлива» |

Тип слота читается из `skeleton` в `tables/<name>.yml`
(старые значения `design_0`, `container_3` и т.п. распознаются).

## Хранилище

```
StorageKey = (StorageType, owner, table)
  BLOCK    owner = "world:x,y,z"
  PERSONAL owner = имя игрока
  TEAM     owner = команда (scoreboard, fallback "default")
  GLOBAL   owner = ""
  TEMPORARY owner = UUID игрока (диск не используется)
```

Слой `StorageService`:

1. `load(key)` — из кэша (`StorageView`), при промахе — с диска;
2. `updateSlot(key, slot, payload)` — меняется один слот массива,
   помечается `dirty`, пишется асинхронно (коалесинг: один write
   на всплеск изменений);
3. `saveNow(key)` — гарантированная запись (close/quit);
4. автосейв каждые `storage.autosave-ticks`;
5. `flushAll()` — синхронный flush при `onDisable`.

Форматы на диске (совместимы с путями 1.x):

- `data/players/<игрок>_<таблица>` — JSON-массив строк;
- `data/teams/<команда>_<таблица>`;
- `data/globals/<таблица>`;
- `<мир>/CustomGuiReworked/blocks/<x/16>_<z/16>.json` —
  `{ "world:x,y,z": { "tables": { "<таблица>": ["n1:...", ...] } } }`.

Все записи атомарны (`.tmp` + move). Блок-регионы кешируются в памяти
с локом на регион.

Миграция 1.x: элементы-объекты (старый NBTAPI-JSON) конвертируются
при чтении через `LegacyPayloads` и записываются обратно в новом
формате. Без NBTAPI — упрощённый разбор (тип, количество, damage,
название, lore, CMD).

## Кодеки предметов

Payload = `<тег>:<данные>`:

- `n1:` — NBTAPI (`NBTContainer.setItemStack/getItemStack`);
- `b1:` — Bukkit `ItemFactory.serializeItem/deserializeItem` + Gson.

Тег фиксируется при записи → данные читаются тем кодеком, которым
записаны, независимо от активного кодека. Активный кодек выбирается
при включении: NBTAPI есть → `n1`, нет → `b1` (с предупреждением).

## Блоки (ItemsAdder / CraftEngine)

`BlockHookManager` при включении: для каждого установленного
плагина (ItemsAdder, CraftEngine) **рефлективно** загружает хук и
регистрирует его слушатели. Без плагина — класс не загружается,
жёсткой зависимости нет.

- Интеракция (ItemsAdder — любой клик по custom block; CraftEngine —
  только правый клик) → `dispatcher.onBlockInteract`: GUI по ID блока
  (реестр O(1), без учёта регистра, fallback на суффикс после `:`) →
  `GuiOpener.openForPlayer(player, gui, blockLocation)` → событие
  отменяется.
- Разрушение блока → предметы всех таблиц блока дропаются, данные
  удаляются, открытые интерфейсы этого блока закрываются.

## Редактор

`EditorSession` (на игрока): GUI + активный промпт
(`TITLE` / `BLOCK_ID`). Экраны: MAIN, SIZE, SKELETON, DESIGN,
STORAGE, BLOCKS. Все клики по верхнему инвентарю отменяются по
умолчанию; DESIGN-слоты на экране DESIGN разрешают работу с
предметами (пересъёмка дизайна на следующем тике). Чат-промпты
отменяются сообщением `/cancel`. Каждое действие сразу
`registry.save(gui)`.

## Команды слотов

`/gui command add <slot> <gui> [delay] <cmd...>` →
`SlotCommand(slot, command, delay)`. При клике:

1. `GuiSlotClickEvent` (отмена → команды не выполняются);
2. `Bukkit.dispatchCommand` с плейсхолдерами `%player%`, `%slot%`;
   опционально `setOp` на время выполнения
   (`commands.execute-as-op`, по умолчанию выключено).
