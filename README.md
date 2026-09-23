# CustomGuiReworked

Skeleton-based GUI framework для **Paper 26.2** (Java 25): визуальный in-game редактор, высокопроизводительное хранилище, интеграция кастомных блоков (ItemsAdder + CraftEngine) и готовый публичный API как библиотека.

> **Версия: `2.4.5`.** В этой версии добавлены категории и расширяемые
> типы слотов. Аддонам нужен API `2.4.5` и соответствующий jar на сервере;
> собранные против прежнего `SlotType enum` плагины необходимо пересобрать.

---

## Содержание

- [Документация и структура](#документация-и-структура)
- [Возможности](#возможности)
- [Требования](#требования)
- [Установка](#установка)
- [Команды](#команды)
- [Редактор](#редактор)
- [Категории и пользовательские типы слотов](#категории-и-пользовательские-типы-слотов)
- [Хранилище](#хранилище)
- [Конфигурация](#конфигурация)
- [API для разработчиков](#api-для-разработчиков)
  - [Подключение зависимости](#подключение-зависимости)
  - [Доступ к сервису](#доступ-к-сервису)
  - [Модель GUI](#модель-gui)
  - [GuiBuilder](#guibuilder)
  - [GuiService / CustomGuiAPI](#guiservice--customguiapi)
  - [Storage API](#storage-api)
  - [Block API](#block-api)
  - [Events API](#events-api)
  - [Примеры](#примеры)
- [Структура проекта](#структура-проекта)
- [Сборка](#сборка)
- [Лицензия](#лицензия)

---

## Документация и структура

Основная документация разбита по файлам — все ссылки относительные из корня репозитория:

### Гайды
- **[`API.md`](API.md)** — полный гайд для разработчиков: [категории](API.md#41-категории-в-gui-и-редакторе), [типы слотов и связи](API.md#42-собственные-типы-слотов-и-связи-между-ними), билдер, хранилище, события, функциональные блоки, Skript/Denizen.
- **[`EXAMPLES.md`](EXAMPLES.md)** — готовые примеры кода: первый GUI, полный котёл, [аддон с категорией, фильтром предметов и `watch`](EXAMPLES.md#9-категории-и-пользовательские-типы-слотов).
- **[`docs/`](docs/Home.md)** — wiki-документация: [API возможности](docs/api.md), [умный блок — котёл (пошагово)](docs/smart-block.md), [Skript и Denizen](docs/scripts.md), [примеры](docs/examples.md).
- **[`MECHANICS.md`](MECHANICS.md)** — внутренние механики: расширяемый скелет слотов, shift/double-click, StorageService, редактор и фильтрация менеджера.

### Конфигурация и ресурсы
- **[`src/main/resources/config.yml`](src/main/resources/config.yml)** — дефолтный конфиг (язык, autosave, coalesce, интеграции).
- **[`src/main/resources/lang/en.yml`](src/main/resources/lang/en.yml)** / **[`src/main/resources/lang/ru.yml`](src/main/resources/lang/ru.yml)** — переводы интерфейса.
- **[`src/main/resources/plugin.yml`](src/main/resources/plugin.yml)** — описание плагина, команды, softdepend.
- **[`build.gradle`](build.gradle)** / **[`settings.gradle`](settings.gradle)** / **[`gradle.properties`](gradle.properties)** — сборка, версии, публикация.

### Публичный API
- **[`api/GuiService.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiService.java)** — главный сервисный интерфейс (Bukkit Services).
- **[`api/CustomGuiAPI.java`](src/main/java/dev/moonaticks/customGuiReworked/api/CustomGuiAPI.java)** — статический фасад над `GuiService`.
- **[`api/Gui.java`](src/main/java/dev/moonaticks/customGuiReworked/api/Gui.java)** — модель GUI (имя, категория, титул, размер, скелет, дизайн, команды, блоки).
- **[`api/GuiBuilder.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiBuilder.java)** — fluent-билдер.
- **[`api/GuiCategory.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiCategory.java)** — ID, название, иконка и описание категории.
- **[`api/SlotType.java`](src/main/java/dev/moonaticks/customGuiReworked/api/SlotType.java)** — встроенные и регистрируемые типы слотов, правила взаимодействия и связи.
- **[`api/StorageType.java`](src/main/java/dev/moonaticks/customGuiReworked/api/StorageType.java)** — типы хранилища: `BLOCK`, `PERSONAL`, `GLOBAL`, `TEAM`, `TEMPORARY`.
- **[`api/SlotCommand.java`](src/main/java/dev/moonaticks/customGuiReworked/api/SlotCommand.java)** — команда слота (`slot`, `command`, `delay`).
- **[`api/GuiServiceImpl.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiServiceImpl.java)** — реализация сервиса.
- **Функциональные блоки** ([`api/functional/`](src/main/java/dev/moonaticks/customGuiReworked/api/functional/)):
  - **`FunctionalBlock.java`** — fluent-builder «умного» блока (колбэки onOpen/onClick/onItemChanged/onTick/onBlockTick/onClose, рецепт, топливо).
  - **`FunctionalBlockHandler.java`** — интерфейс колбэков.
  - **`FunctionalBlockData.java`** — персистентные данные блока (KV: прогресс, флаги).
  - **`FunctionalBlockRegistry.java`** — реестр: работа без GUI, данные, working-флаги.
  - **`CraftingRecipe.java`** — рецепт (ингредиенты/результаты/топливо/время).
- **Анимации** ([`api/animation/DesignAnimation.java`](src/main/java/dev/moonaticks/customGuiReworked/api/animation/DesignAnimation.java)) — кадры DESIGN/RESULT-слотов через локальные оверрайды + `stageForProgress`.
- События:
  - **[`api/event/GuiOpenEvent.java`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiOpenEvent.java)** — до открытия, cancellable, есть `StorageKey`.
  - **[`api/event/GuiCloseEvent.java`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiCloseEvent.java)** — после закрытия.
  - **[`api/event/GuiSlotClickEvent.java`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiSlotClickEvent.java)** — клик по слоту, двухуровневая отмена.
  - **[`api/event/GuiDragEvent.java`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiDragEvent.java)** — drag по слотам.
  - **[`api/event/GuiSlotChangedEvent.java`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiSlotChangedEvent.java)** — содержимое слота изменилось (след. тик), предметы «было/стало».

### Внутренние модули (для понимания механик)
- **GUI ядро:** [`gui/GuiHolder.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiHolder.java), [`gui/GuiOpener.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiOpener.java), [`gui/GuiRegistry.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiRegistry.java), [`gui/CategoryRegistry.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/CategoryRegistry.java)
- **Хранилище:** [`storage/StorageService.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageService.java), [`storage/StorageKey.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageKey.java), [`storage/StorageView.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageView.java), [`storage/StorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageBackend.java), [`storage/SimpleStorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/SimpleStorageBackend.java), [`storage/BlockStorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/BlockStorageBackend.java)
- **Кодеки предметов:** [`codec/Codecs.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/Codecs.java), [`codec/ItemCodec.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/ItemCodec.java), [`codec/BukkitItemCodec.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/BukkitItemCodec.java), [`codec/NbtApiItemCodec.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/NbtApiItemCodec.java), [`codec/LegacyPayloads.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/LegacyPayloads.java)
- **Редактор:** [`editor/EditorSession.java`](src/main/java/dev/moonaticks/customGuiReworked/editor/EditorSession.java), [`editor/EditorManager.java`](src/main/java/dev/moonaticks/customGuiReworked/editor/EditorManager.java), [`editor/EditorHolder.java`](src/main/java/dev/moonaticks/customGuiReworked/editor/EditorHolder.java), [`editor/EditorListener.java`](src/main/java/dev/moonaticks/customGuiReworked/editor/EditorListener.java)
- **Менеджер `/gui`:** [`manager/ManagerMenu.java`](src/main/java/dev/moonaticks/customGuiReworked/manager/ManagerMenu.java), [`manager/ManagerSession.java`](src/main/java/dev/moonaticks/customGuiReworked/manager/ManagerSession.java), [`manager/ManagerHolder.java`](src/main/java/dev/moonaticks/customGuiReworked/manager/ManagerHolder.java)
- **Интеграции блоков:** [`integration/BlockHookManager.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockHookManager.java), [`integration/BlockHookDispatcher.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockHookDispatcher.java), [`integration/BlockPluginHook.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockPluginHook.java), [`integration/ItemsAdderHook.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/ItemsAdderHook.java), [`integration/CraftEngineHook.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/CraftEngineHook.java)
- **Команды:** [`command/GuiCommand.java`](src/main/java/dev/moonaticks/customGuiReworked/command/GuiCommand.java), [`command/GuiTabCompleter.java`](src/main/java/dev/moonaticks/customGuiReworked/command/GuiTabCompleter.java)
- **Слушатели:** [`listeners/GuiInteractionListener.java`](src/main/java/dev/moonaticks/customGuiReworked/listeners/GuiInteractionListener.java), [`listeners/SlotInteractionPolicy.java`](src/main/java/dev/moonaticks/customGuiReworked/listeners/SlotInteractionPolicy.java), [`listeners/PlayerListener.java`](src/main/java/dev/moonaticks/customGuiReworked/listeners/PlayerListener.java)
- **Skript:** [`skript/SkriptSupport.java`](src/main/java/dev/moonaticks/customGuiReworked/skript/SkriptSupport.java) + эффекты/условия/выражения в [`skript/`](src/main/java/dev/moonaticks/customGuiReworked/skript/)
- **Denizen:** [`denizen/CguiDenizenSupport.java`](src/main/java/dev/moonaticks/customGuiReworked/denizen/CguiDenizenSupport.java), [`denizen/CguiTagBase.java`](src/main/java/dev/moonaticks/customGuiReworked/denizen/CguiTagBase.java) + events в [`denizen/events/`](src/main/java/dev/moonaticks/customGuiReworked/denizen/events/)

### Тесты
- **[`src/test/`](src/test/java/dev/moonaticks/customGuiReworked/)** — unit-тесты: API категорий/типов, меню, механики слотов, `codec/`, `storage/`, `editor/`, `lang/`.

---

## Возможности

- **Визуальный редактор** `/gui create <name>` — размер, скелет слотов, дизайн, титул, тип хранилища, привязки блоков, live preview. Всё сохраняется мгновенно, без мерцания окон.
- **Скелет слотов** — каждый слот типизирован: `DESIGN` (декорация), `CONTAINER`, `CRAFT`, `RESULT` (только забор), `FUEL`.
- **Категории в `/gui`** — по умолчанию «Без категории», фильтр вместе с поиском и пагинацией; категория выбирается/создаётся в редакторе или регистрируется через API.
- **Собственные `SlotType`** — регистрация через API: фильтры вставки/изъятия, обработчики кликов/изменений, направленные связи `watch` и обновление отслеживаемых слотов. См. [категории и типы](#категории-и-пользовательские-типы-слотов).
- **5 типов хранилища**: `block`, `personal`, `global`, `team`, `temporary` — см. [`StorageType.java`](src/main/java/dev/moonaticks/customGuiReworked/api/StorageType.java) и раздел [Хранилище](#хранилище).
- **Оптимизированный движок хранения**: in-memory кэш, асинхронные коалесированные записи, диф по baseline, атомарные записи (tmp + move), autosave + гарантированный save на close/quit/stop.
- **Кастомные блоки**: ПКМ по ItemsAdder / CraftEngine блоку открывает привязанный GUI; слом блока дропает содержимое и закрывает интерфейсы. Интеграции грузятся рефлексивно — см. [`integration/`](src/main/java/dev/moonaticks/customGuiReworked/integration/).
- **«Умные» функциональные блоки** — builder `FunctionalBlock` (котёл/печь/верстак/генератор): крафты и топливо, **блок продолжает работать без открытого GUI** (`setWorking` + `onBlockTick`), персистентные данные блока (`blockData`), предметы слотов блока при закрытом GUI, зрители блока, стрелки прогресса — см. [`api/functional/`](src/main/java/dev/moonaticks/customGuiReworked/api/functional/) и [API.md §15–17](API.md).
- **Локальные оверрайды (per-зритель)**: название и дизайн слотов окна подменяются только для одного игрока (стрелки, огонь, уровни жидкости) — файл GUI, другие игроки и блоки не затрагиваются — [API.md §14](API.md).
- **`GuiSlotChangedEvent`** — «предмет положен/забран/перенесён» с фактическими предметами «было/стало» (следующий тик) — готовая точка запуска крафта.
- **Скриптовый инструментарий без Java**: Skript (события `on cgui ...`/`cgui slot changed`, эффекты, условия, выражения) и Denizen (события, теги `<cgui...>`, изменения через `adjust`) — [API.md §10](API.md#10-skript-и-denizen) и [docs/scripts.md](docs/scripts.md).
- **NBT на новом стандарте**: NBTAPI — optional soft-depend; без него — fallback на Paper `serializeAsBytes()` + Base64, старые данные читаются через теги `n1:` / `b2:` / `b1:` — см. [`codec/`](src/main/java/dev/moonaticks/customGuiReworked/codec/).
- **Публичный API**: Bukkit Services + fluent builder + events — полностью описан в [`API.md`](API.md) и ниже.

---

## Требования

| Компонент | Версия | Обязателен | Файл |
|---|---|---|---|
| Paper | **26.2+** (Java 25) | да | [`build.gradle`](build.gradle) `paperVersion` |
| NBTAPI | 2.16+ | нет, рекомендуется | [`codec/NbtApiItemCodec.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/NbtApiItemCodec.java) |
| ItemsAdder | 4.x | нет | [`integration/ItemsAdderHook.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/ItemsAdderHook.java) |
| CraftEngine | 26.x | нет | [`integration/CraftEngineHook.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/CraftEngineHook.java) |
| Skript | 2.14+ | нет | [`skript/`](src/main/java/dev/moonaticks/customGuiReworked/skript/) |
| Denizen | 1.3.x | нет | [`denizen/`](src/main/java/dev/moonaticks/customGuiReworked/denizen/) |

---

## Установка

1. Скачай `CustomGuiReworked-x.y.z.jar` из релизов или собери `./gradlew build` → `build/libs/`.
2. (Рекомендуется) установи **NBTAPI** — полная NBT-точность.
3. Закинь в `plugins/`, запусти сервер. Создастся:
   - `plugins/CustomGuiReworked/config.yml` — из [`src/main/resources/config.yml`](src/main/resources/config.yml)
   - `plugins/CustomGuiReworked/lang/{en,ru}.yml` — из [`lang/`](src/main/resources/lang/)
   - `plugins/CustomGuiReworked/tables/` — GUI из редактора
   - `plugins/CustomGuiReworked/custom/` — GUI из API других плагинов
   - `plugins/CustomGuiReworked/categories.yml` — описания категорий (создаётся при регистрации первой сохраняемой категории)
   - `plugins/CustomGuiReworked/data/` — `players/`, `teams/`, `globals/`
   - `<world>/CustomGuiReworked/blocks/` — регион-файлы блоков

---

## Команды

| Команда | Описание | Право | Код |
|---|---|---|---|
| `/gui` | Менеджер GUI (категории, поиск, пагинация) | `cgui.command` | [`ManagerMenu.java`](src/main/java/dev/moonaticks/customGuiReworked/manager/ManagerMenu.java) |
| `/gui create <name>` | Создать + открыть редактор | `cgui.create` | [`GuiCommand.java`](src/main/java/dev/moonaticks/customGuiReworked/command/GuiCommand.java) |
| `/gui edit <name>` | Открыть редактор | `cgui.edit` | |
| `/gui open <name>` | Открыть GUI | `cgui.open` | [`GuiOpener.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiOpener.java) |
| `/gui delete <name>` | Удалить файл GUI | `cgui.delete` | [`GuiRegistry.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiRegistry.java) |
| `/gui list` | Список GUI | — | |
| `/gui command add <slot> <gui> [delay] <cmd...>` | Привязать команду | `cgui.command` | [`SlotCommand.java`](src/main/java/dev/moonaticks/customGuiReworked/api/SlotCommand.java) |
| `/gui command get <slot> <gui> [index]` | Показать команды | `cgui.command` | |
| `/gui command delete <slot> <gui> <index>` | Отвязать | `cgui.command` | |
| `/gui reload` | Перечитать конфиг/язык/GUI | `cgui.reload` | |

Плейсхолдеры в командах слотов: `%player%`, `%slot%`.

---

## Редактор

`/gui create <name>` открывает редактор — см. [`editor/EditorSession.java`](src/main/java/dev/moonaticks/customGuiReworked/editor/EditorSession.java):

- **Размер** — 9/18/27/36/45/54 (существующие слоты сохраняются при ресайзе) — [`Gui.java#slots()`](src/main/java/dev/moonaticks/customGuiReworked/api/Gui.java)
- **Скелет** — клик циклит встроенные и зарегистрированные через API типы (`Design → Container → Craft → Result → Fuel → …`), shift-клик форсит `Design` — [`SlotType.java`](src/main/java/dev/moonaticks/customGuiReworked/api/SlotType.java)
- **Дизайн** — бери предметы из своего инвентаря: на курсор + клик по слоту, shift-клик из инвентаря, drag. ПКМ пустой рукой чистит слот. Double-click отключён.
- **Титул** — чат-промпт, `/cancel` отменяет.
- **Хранилище** — выбор `StorageType`.
- **Preview** — открывает GUI как игрок.
- **Категория** — по умолчанию «Без категории»; выбор существующей или создание в чате (`id Название`). В `/gui` отдельный список категорий (в том числе «Все» и «Без категории») фильтрует GUI вместе с поиском и пагинацией. Иконку и описание можно задать через API или `categories.yml` — см. [ниже](#категории-и-пользовательские-типы-слотов).
- **Custom blocks** — bind/unbind ID (`custom_block`, `craftengine:custom_block`) — [`BlockHookManager.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockHookManager.java)
- **Delete** — удаление (право `cgui.delete`).

Подробнее — [MECHANICS.md#редактор](MECHANICS.md#редактор).

---

## Категории и пользовательские типы слотов

**Администратору.** В `/gui` нажми **Категория** → **Все** / **Без
категории** / нужная категория. Фильтр работает вместе с поиском по
имени и листанием страниц. В редакторе GUI выбери **Категория** и
существующий пункт либо **Создать** и введи в чат, например,
`machines Механизмы` (`/cancel` — отмена). Новый GUI без выбора
получает категорию `none`, старые GUI без поля `category` — тоже. Редактор
скелета показывает как встроенные, так и зарегистрированные аддонами
типы; неизвестный тип показан как `BARRIER` и остаётся заблокированным.

**Файлы.** В `tables/<name>.yml` или `custom/<name>.yml` хранится
`category: myaddon:machines` и список ID слотов `skeleton:`.
Название, иконка и описание категории живут в отдельном
`plugins/CustomGuiReworked/categories.yml`:

```yaml
categories:
  - id: 'myaddon:machines'
    name: '§6Механизмы'
    icon: FURNACE
    description: '§7Интерфейсы станков'
```

`/gui reload` перечитывает эти файлы после ручного изменения. Если
описания категории нет, меню всё равно покажет её ID; удаление описания
не сбрасывает категорию у GUI. `none` — зарезервированное значение, а не
запись в `categories.yml`.

**Разработчику.** API позволяет отдельно зарегистрировать описание
категории и назначить её GUI, а типу слота задать собственные правила
и связь с другими типами:

```java
GuiCategory machines = CustomGuiAPI.registerCategory(new GuiCategory(
        "myaddon:machines", "§6Механизмы", Material.FURNACE, "§7Станки"));
SlotType ore = CustomGuiAPI.registerSlotType(SlotType.builder("myaddon:ore")
        .allowInsert(true).allowTake(true).persist(true)
        .acceptInsert(ctx -> ctx.item().getType() == Material.IRON_ORE)
        .build());
SlotType indicator = CustomGuiAPI.registerSlotType(SlotType.builder("myaddon:indicator")
        .track(true).watch(ore)
        .onRelatedChange(relation -> CustomGuiAPI.setSlotItem(
                relation.change().getInventory(), relation.relatedSlot(),
                relation.change().getNewItem() == null ? null : new ItemStack(Material.LIME_DYE)))
        .build()); // индикатор нельзя забрать — безопасный пример, не выдача результата
Gui gui = GuiBuilder.named("ore_status").size(27).category(machines)
        .slot(13, ore).slot(22, indicator).build();
CustomGuiAPI.registerGui(gui, true);
```

`watch(ore)` направлен от изменившегося входа к индикатору **в том же
открытом GUI**; `onRelatedChange` получает и исходное событие, и номер
слота наблюдателя. Содержимое входа сохраняется, индикатора — нет.
Тип с `allowInsert(true)` обязан иметь `persist(true)`. Только ID типа
сохраняется в YAML: колбэки нужно регистрировать при каждом `onEnable`;
до регистрации/после отключения слот остаётся запертым без потери данных.
`SlotType` теперь **класс, не enum** — сторонние аддоны нужно пересобрать
под новый jar. Подробные правила, варианты callback'ов, безопасность и
полный пример аддона: [API.md §4.1–4.2](API.md#41-категории-в-gui-и-редакторе),
[EXAMPLES.md §9](EXAMPLES.md#9-категории-и-пользовательские-типы-слотов).

---

## Хранилище

| Тип | Scope | Где лежит | Код |
|---|---|---|---|
| `block` | один блок | `<world>/CustomGuiReworked/blocks/` | [`BlockStorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/BlockStorageBackend.java) |
| `personal` | per-player | `data/players/<name>_<table>` | [`SimpleStorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/SimpleStorageBackend.java) |
| `global` | server-wide | `data/globals/<table>` | |
| `team` | per Bukkit team | `data/teams/<team>_<table>` | |
| `temporary` | сессия | нигде, возврат при закрытии | [`StorageService.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageService.java) |

**Формат на диске:** JSON-массив тегированных payload'ов — см. [`codec/Codecs.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/Codecs.java):

- `n1:` — NBTAPI JSON (при наличии NBTAPI)
- `b2:` — Paper `ItemStack.serializeAsBytes()` + Base64 (fallback)
- `b1:` — старый Bukkit-map, read-only, переписывается при save

**Ключ хранилища:** [`storage/StorageKey.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageKey.java)

```java
StorageKey = (StorageType, owner, table)
  BLOCK    owner = "world:x,y,z"
  PERSONAL owner = имя игрока
  TEAM     owner = команда (fallback "default")
  GLOBAL   owner = ""
  TEMPORARY owner = UUID игрока
```

Детали движка — [MECHANICS.md#хранилище](MECHANICS.md#хранилище) и [`storage/StorageService.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageService.java).

---

## Конфигурация

Файл [`src/main/resources/config.yml`](src/main/resources/config.yml):

```yaml
language: en            # en / ru — см. lang/
language-auto-update: true

storage:
  autosave-ticks: 600   # 0 = off
  max-cached-views: 10000
  coalesce-ms: 1000     # коалесинг записей

commands:
  execute-as-op: false  # legacy setOp

integration:
  skript: true
  denizen: true
```

Языки — [`lang/en.yml`](src/main/resources/lang/en.yml) / [`lang/ru.yml`](src/main/resources/lang/ru.yml), менеджер — [`lang/LanguageManager.java`](src/main/java/dev/moonaticks/customGuiReworked/lang/LanguageManager.java). Автообновление по `lang-version`.

---

## API для разработчиков

> Полный гайд: **[`API.md`](API.md)**. Здесь — краткая справка с прямыми ссылками на исходники.

### Подключение зависимости

CustomGuiReworked публикуется через **JitPack** — он собирает jar прямо из GitHub по тегу. Файл [`jitpack.yml`](jitpack.yml) указывает JDK 25. Артефакт доступен как `com.github.amper24:CustomGuiReworked:<version>`. Версию бери из [`build.gradle`](build.gradle) `version = '2.4.5'` или из релизов GitHub. Можно указывать:
- конкретный тег: `2.4.5` (категории и свои типы слотов), `2.4.0` (предыдущая версия)
- короткий хеш коммита: `a1b2c3d`
- ветку: `main-SNAPSHOT` (последний коммит main, кэшируется на 24ч)

> Первая сборка на JitPack занимает ~1-2 минуты (смотрит лог на jitpack.io), потом отдаётся из кэша.

#### Важные правила (обязательно!)

1. **Только `compileOnly` / `provided`** — никогда `implementation` / `shade`. Плагин предоставляет классы в рантайме. Вшивание (shade) приведёт к дублированию и крашу.
2. **`softdepend` в `plugin.yml`** — гарантирует загрузку после CustomGuiReworked и регистрацию `GuiService`. См. [`src/main/resources/plugin.yml`](src/main/resources/plugin.yml).
3. **Paper API тоже `compileOnly`** — версия `26.2.build.123-stable` как в [`build.gradle`](build.gradle).
4. **Не добавлять `transitive = false` не нужно** для JitPack-артефакта, но если подключаешь ItemsAdder/CraftEngine/NBTAPI — используй как в примере этого проекта.

---

#### Gradle — Kotlin DSL (`build.gradle.kts`) — рекомендуется

Полный пример для твоего плагина:

```kotlin
plugins {
    id("java")
    // опционально shadow, но НЕ для CustomGuiReworked
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") // Paper
    maven("https://jitpack.io") // <-- обязательно для CustomGuiReworked
}

dependencies {
    // Paper — только для компиляции
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")

    // CustomGuiReworked — только для компиляции
    compileOnly("com.github.amper24:CustomGuiReworked:2.4.5")

    // Для тестов (если нужны) — отдельно, как в этом проекте:
    testCompileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

// Копировать plugin.yml с подстановкой версии, как в этом проекте
tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
```

**Где лежит `build.gradle.kts`:** в корне твоего плагина, рядом с `gradlew` (wrapper). Если используешь wrapper — запускай `./gradlew build` (Linux/macOS) или `gradlew.bat build` (Windows) — он скачает Gradle 9 и зависимости, включая jar с JitPack.

---

#### Gradle — Groovy DSL (`build.gradle`)

Эквивалент на Groovy — самый популярный вариант:

```groovy
plugins {
    id 'java'
}

group = 'com.example'
version = '1.0.0'

repositories {
    mavenCentral()
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
    maven { url = 'https://jitpack.io' } // JitPack
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.2.build.123-stable'
    compileOnly 'com.github.amper24:CustomGuiReworked:2.4.5'

    testCompileOnly 'io.papermc.paper:paper-api:26.2.build.123-stable'
    testImplementation 'org.junit.jupiter:junit-jupiter:6.0.3'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release.set(25)
}

processResources {
    def props = [version: project.version]
    inputs.properties props
    filesMatching(['plugin.yml']) {
        expand props
    }
}
```

**Проверка:** после `./gradlew build` в `build/libs/` будет твой jar **без** CustomGuiReworked внутри (проверь `jar tf build/libs/*.jar | grep customGuiReworked` — должен быть пустым).

Если используешь ShadowJar — обязательно исключи:

```groovy
shadowJar {
    dependencies {
        exclude(dependency('com.github.amper24:CustomGuiReworked'))
    }
}
```

---

#### Maven (`pom.xml`) — полная конфигурация

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>MyAddon</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <paper.version>26.2.build.123-stable</paper.version>
        <cgui.version>2.4.5</cgui.version>
    </properties>

    <repositories>
        <!-- Paper -->
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
        <!-- JitPack — для CustomGuiReworked -->
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Paper API — provided -->
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>${paper.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- CustomGuiReworked — provided -->
        <dependency>
            <groupId>com.github.amper24</groupId>
            <artifactId>CustomGuiReworked</artifactId>
            <version>${cgui.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <defaultGoal>clean package</defaultGoal>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>25</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <!-- Никогда не шейдим CustomGuiReworked -->
                            <artifactSet>
                                <excludes>
                                    <exclude>com.github.amper24:CustomGuiReworked</exclude>
                                    <exclude>io.papermc.paper:paper-api</exclude>
                                </excludes>
                            </artifactSet>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Подстановка версии в plugin.yml, как в этом проекте -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.1</version>
                <configuration>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
        </plugins>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
            </resource>
        </resources>
    </build>
</project>
```

**Команды Maven:**
```bash
mvn clean package -U   # -U форсит проверку JitPack
# результат: target/MyAddon-1.0.0.jar
```

---

#### Локальный jar (без JitPack)

Если нет доступа к Maven-репозиториям:

1. Скачай `CustomGuiReworked-2.4.5.jar` из релизов GitHub в папку `libs/` твоего проекта.
2. Подключи:

**Gradle Kotlin:**
```kotlin
dependencies {
    compileOnly(files("libs/CustomGuiReworked-2.4.5.jar"))
}
```

**Gradle Groovy:**
```groovy
dependencies {
    compileOnly files('libs/CustomGuiReworked-2.4.5.jar')
}
```

**Maven (system scope — не рекомендуется, лучше install):**
```bash
mvn install:install-file -Dfile=libs/CustomGuiReworked-2.4.5.jar -DgroupId=com.github.amper24 -DartifactId=CustomGuiReworked -Dversion=2.4.5 -Dpackaging=jar
```
А потом зависимость как выше с `provided`.

---

#### `plugin.yml` твоего плагина

Обязательно добавь `softdepend` — см. как сделано в [`src/main/resources/plugin.yml`](src/main/resources/plugin.yml) этого проекта:

```yaml
name: MyAddon
version: '${version}' # подставится из Gradle/Maven
main: com.example.myaddon.MyAddon
api-version: '26.2'
softdepend: [CustomGuiReworked] # <-- критично!

# Если используешь ItemsAdder/CraftEngine/NBTAPI — тоже добавь:
# softdepend: [CustomGuiReworked, NBTAPI, ItemsAdder, CraftEngine]

commands:
  myaddon:
    description: My addon command
    usage: /myaddon
```

Почему `softdepend`, а не `depend`? Плагин должен работать и без CustomGuiReworked (с сообщением об ошибке), а не падать при загрузке. Проверка в `onEnable`:

```java
@Override
public void onEnable() {
    if (Bukkit.getPluginManager().getPlugin("CustomGuiReworked") == null) {
        getLogger().warning("CustomGuiReworked not found! MyAddon features disabled.");
        return;
    }
    // теперь безопасно вызывать API
    if (!CustomGuiAPI.isInitialized()) {
        getLogger().warning("CustomGuiReworked service not ready yet");
        return;
    }
    CustomGuiAPI.registerGui(buildGui(), true);
}
```

---

#### Выбор версии

| Что указать | Пример | Когда использовать |
|---|---|---|
| Релиз-тег | `2.4.5` | Категории и расширяемые типы слотов |
| Предыдущий релиз | `2.4.0` | Без категорий и нового API типов |
| Коммит | `a1b2c3d` (7 символов) | Тест фикса до релиза |
| Ветка | `main-SNAPSHOT` | Разработка, всегда последний main |
| PR | `PR-123-SNAPSHOT` | Тест PR (JitPack поддерживает) |

**Для категорий и собственных `SlotType` используйте `2.4.5`** и в
аддоне, и на сервере. Пересоберите аддоны, использовавшие прежний
`SlotType enum`: бинарной совместимости с `2.4.0` нет.

Актуальную версию смотри в:
- [`build.gradle`](build.gradle) `version = '...'`
- GitHub Releases
- JitPack бейдж: `https://jitpack.io/#amper24/CustomGuiReworked`

Если JitPack не видит новую версию — нажми "Get it" на https://jitpack.io/#amper24/CustomGuiReworked и подожди лог сборки.

---

#### Troubleshooting

- **`IllegalStateException: service not registered`** — ты вызываешь API до включения CustomGuiReworked. Решение: `softdepend` + вызов из `onEnable`, а не из конструктора / static init.
- **`ClassNotFoundException: GuiService`** — забыл `compileOnly` зависимость или не добавил JitPack репозиторий.
- **Jar вырос на 5+ MB** — ты зашейдил CustomGuiReworked. Проверь `compileOnly` / `provided` и `shadowJar { exclude }`.
- **JitPack 401 / не находит артефакт** — первая сборка ещё идёт. Открой https://jitpack.io/com/github/amper24/CustomGuiReworked/2.4.5/build.log и дождись `Build OK`.
- **`UnsupportedClassVersionError`** — собираешь под Java 25, а сервер на Java 21. Этот плагин требует **Java 25** — см. [`jitpack.yml`](jitpack.yml) и `targetJavaVersion = 25` в [`build.gradle`](build.gradle).

### Доступ к сервису

**Через Bukkit Services (без compileOnly зависимости на этапе загрузки):**

```java
GuiService service = Bukkit.getServicesManager().load(GuiService.class).orElse(null);
if (service != null) service.openGui(player, "shop");
```

**Через фасад (если подключил jar):**

```java
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
if (CustomGuiAPI.isInitialized()) {
    CustomGuiAPI.openGui(player, "shop");
}
```

Исходники: [`GuiService.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiService.java) и [`CustomGuiAPI.java`](src/main/java/dev/moonaticks/customGuiReworked/api/CustomGuiAPI.java).

### Модель GUI

Класс [`Gui.java`](src/main/java/dev/moonaticks/customGuiReworked/api/Gui.java):

| Поле | Тип | Описание |
|---|---|---|
| `name` | `String` | Уникальное имя, нормализуется в `[a-z0-9_-]`, без `.yml` — `normalizeName()` |
| `title` | `String` | Заголовок с `§` |
| `slots` | `int` | 9..54, кратно 9 — `clampSlots()` |
| `skeleton` | `List<SlotType>` | Тип каждого слота — см. [`SlotType.java`](src/main/java/dev/moonaticks/customGuiReworked/api/SlotType.java) |
| `design` | `List<String>` | Payloads предметов дизайна — [`Codecs.java`](src/main/java/dev/moonaticks/customGuiReworked/codec/Codecs.java) |
| `storage` | `StorageType` | Тип хранилища — [`StorageType.java`](src/main/java/dev/moonaticks/customGuiReworked/api/StorageType.java) |
| `category` | `String` | ID категории (по умолчанию `none`), описание — в `categories.yml` |
| `commands` | `List<SlotCommand>` | Команды слотов — [`SlotCommand.java`](src/main/java/dev/moonaticks/customGuiReworked/api/SlotCommand.java) |
| `blockIds` | `Set<String>` | Привязки блоков — [`BlockHookManager.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockHookManager.java) |
| `source` | `Source` | `TABLE` (`tables/`), `CUSTOM` (`custom/`), `RUNTIME` (память) |

Ключевые методы `Gui`:
- `title(String)`, `slots(int)`, `storage(StorageType)`, `category(String)`, `category(GuiCategory)`, `source(Source)`
- `slotType(int)`, `setSlotType(int, SlotType)`, `slotsOf(SlotType)`, `replaceSkeleton(List)`, `resetSkeleton()`
- `designAt(int)`, `setDesignAt(int, String)`, `setDesignItem(int, ItemStack)`, `replaceDesign(List)`, `resetDesign()`
- `commands()`, `addCommand(SlotCommand)`, `commandsForSlot(int)`, `removeCommand(int, int)`
- `blockIds()`, `addBlockId(String)`, `removeBlockId(String)`
- `fileName()` → `<name>.yml`, `rename(String)`

### GuiBuilder

Файл [`GuiBuilder.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiBuilder.java) — fluent API:

```java
Gui gui = GuiBuilder.named("shop")
        .title("§6Shop")
        .size(27) // сначала size!
        .storage(StorageType.PERSONAL)
        .category("shops") // необязательно; по умолчанию none
        .slot(10, SlotType.CONTAINER)
        .slots(List.of(11,12,13), SlotType.CONTAINER)
        .slot(20, SlotType.RESULT)
        .design(0, new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
        .command(4, "say %player% открыл магазин", 0)
        .command(22, "give %player% diamond 1", 5)
        .blockId("myblocks:shop")
        .build();
CustomGuiAPI.registerGui(gui, true);
```

Важно: `size()` вызывать до `slot()` / `design()` / `command()`.

### GuiService / CustomGuiAPI

Интерфейс [`GuiService.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiService.java) — 1:1 дублируется в [`CustomGuiAPI.java`](src/main/java/dev/moonaticks/customGuiReworked/api/CustomGuiAPI.java):

**GUI CRUD:**
```java
Gui getGui(String name)
Set<String> getGuiNames()
List<Gui> getGuis()
Gui createGui(String name)
Gui registerGui(Gui gui)                    // persist=true → custom/*.yml
Gui registerGui(Gui gui, boolean persist)   // false → только память
boolean unregisterGui(String name, boolean deleteFile)
String sourceOf(String name)                // "table" | "custom" | "runtime" | "none"
Gui loadGui(String name)                    // перечитать файл
boolean deleteGui(String name)              // файл удаляет, данные хранит
void saveGui(Gui gui)
```

**Категории и расширяемые типы слотов** (доступны только в новой сборке):
```java
GuiCategory registerCategory(GuiCategory category)                 // categories.yml
GuiCategory registerCategory(GuiCategory category, boolean persist) // false = только память
boolean unregisterCategory(String id)                              // оставляет привязки GUI
GuiCategory getCategory(String id)
List<GuiCategory> getCategories()                                   // только описания
SlotType registerSlotType(SlotType type)                            // SlotType.builder(id).build()
boolean unregisterSlotType(String id)
SlotType getSlotType(String id)                                     // null, если не зарегистрирован
List<SlotType> getSlotTypes()                                       // включая встроенные
```
Правила и пример `watch(...)` — [API.md §4.2](API.md#42-собственные-типы-слотов-и-связи-между-ними).

**Открытие:**
```java
void openGui(Player player, String name)
void openGui(Player player, String name, Location blockLocation) // для BLOCK
void openGui(Player player, String name, StorageType storageOverride) // TEMPORARY override
Gui getOpenGui(Player player)
boolean setSlotItem(Inventory inventory, int slot, ItemStack item)  // отслеживаемый слот открытого GUI
```
`setSlotItem` планирует сохранение (если слот персистентный) и событие
изменения; `null`/`AIR` очищает слот. Для закрытого функционального блока
используйте `setBlockSlotItem`. Подробности — [API.md §4.2](API.md#42-собственные-типы-слотов-и-связи-между-ними).

**Блоки:**
```java
void registerBlockGui(String blockId, String guiName)
void unregisterBlockGui(String blockId)
Gui getBlockGui(String blockId)
Set<String> getBlockIds(String guiName)
```

**Локальные оверрайды (per-зритель; очищаются при закрытии GUI):**
```java
void setLocalTitle(Player, String)                       // null — название из файла
String getLocalTitle(Player)
void clearLocalTitle(Player)
void setLocalDesign(Player, int slot, ItemStack item)    // DESIGN/RESULT; null — дизайн из файла
void setLocalDesigns(Player, Map<Integer, ItemStack> slots)
void clearLocalDesign(Player, int slot)
void clearAllLocalDesigns(Player)
ItemStack getLocalDesign(Player, int slot)
void setLocalDesign(Player, Location block, int slot, ItemStack item)  // per-блок
void setLocalTitle(Player, Location block, String title)
```

**Функциональные блоки / работа без открытого GUI:**
```java
FunctionalBlockRegistry getFunctionalBlocks()
ItemStack getBlockSlotItem(Location block, int slot)           // слот блока при закрытом GUI
boolean setBlockSlotItem(Location block, int slot, ItemStack item)  // + перерисовка зрителей + событие
int consumeBlockSlotItem(Location block, int slot, int amount)
FunctionalBlockData blockData(Location block)                  // персистентный KV (через CraftEngine)
FunctionalBlockData blockData(String blockId, Location block)  // с явным ID
void setWorking(Location block, boolean working)               // onBlockTick тикает (флаг персистится)
boolean isWorking(Location block)
Location getOpenBlockLocation(Player player)
List<Player> getViewers(Location block)
boolean matchesCraft(Inventory, CraftingRecipe)
int consumeFuel(Inventory, int amount)
boolean produceResult(Inventory, Map<Integer, ItemStack> results)
```

Реализация — [`GuiServiceImpl.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiServiceImpl.java), открытие — [`GuiOpener.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiOpener.java), реестр — [`GuiRegistry.java`](src/main/java/dev/moonaticks/customGuiReworked/gui/GuiRegistry.java).

### Storage API

Методы из [`GuiService.java`](src/main/java/dev/moonaticks/customGuiReworked/api/GuiService.java):

```java
List<ItemStack> readStorage(StorageType type, String owner, String table)
void writeStorage(StorageType type, String owner, String table, List<ItemStack> items)
void deleteStorage(StorageType type, String owner, String table)
```

- `owner`: `PERSONAL` → ник, `TEAM` → команда, `BLOCK` → `"world:x,y,z"`, `GLOBAL` → `""`, `TEMPORARY` → UUID
- `table`: имя GUI, обычно `gui.fileName()` или просто `"shop"` — суффикс `.yml` добавится автоматически
- `items`: список по слотам, `null` / `AIR` = пусто

Бэкенды: [`StorageService.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/StorageService.java) (I/O поток, коалесинг, retry), [`BlockStorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/BlockStorageBackend.java) (регионы), [`SimpleStorageBackend.java`](src/main/java/dev/moonaticks/customGuiReworked/storage/SimpleStorageBackend.java).

### Block API

```java
CustomGuiAPI.registerBlockGui("itemsadder:ruby_ore", "shop");
CustomGuiAPI.registerBlockGui("craftengine:atm", "shop");
Gui gui = CustomGuiAPI.getBlockGui("itemsadder:ruby_ore");
CustomGuiAPI.unregisterBlockGui("itemsadder:ruby_ore");
```

- Правый клик по блоку → `GuiOpener.openForPlayer` с `BLOCK` хранилищем
- Слом блока → синхронизация открытых инвентарей в кэш региона → дроп → удаление данных
- Поиск O(1), case-insensitive, fallback на суффикс после `:`
- Код: [`BlockHookManager.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockHookManager.java), [`BlockHookDispatcher.java`](src/main/java/dev/moonaticks/customGuiReworked/integration/BlockHookDispatcher.java)

### Events API

Все в [`api/event/`](src/main/java/dev/moonaticks/customGuiReworked/api/event/):

| Event | Когда | Cancellable | Ключевые поля |
|---|---|---|---|
| [`GuiOpenEvent`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiOpenEvent.java) | до `openInventory` | **да** | `getPlayer()`, `getGui()`, `getInventory()`, `getStorageKey()`, `getStorageType()` |
| [`GuiCloseEvent`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiCloseEvent.java) | после close + save | нет | `getPlayer()`, `getGui()`, `getStorageKey()` |
| [`GuiSlotClickEvent`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiSlotClickEvent.java) | клик по верхнему инвентарю, включая DESIGN | два уровня | `getSlot()`, `getSlotType()`, `getClick()`, `getAction()`, `getCurrentItem()`, `getCursor()`, `getHotbarButton()`, `getHandle()`, `isTopInventory()`, `setCancelled()` (только команды), `setInteractionCancelled()` (команды + ванильный клик) |
| [`GuiDragEvent`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiDragEvent.java) | drag по слотам | **да** | `getTopSlots()` (immutable), `getHandle()` |
| [`GuiSlotChangedEvent`](src/main/java/dev/moonaticks/customGuiReworked/api/event/GuiSlotChangedEvent.java) | содержимое отслеживаемого слота реально изменилось (обычно следующий тик; в том числе custom `track(true)`) | нет | `getSlot()`, `getSlotType()`, `getOldItem()`, `getNewItem()` — фактические «было/стало»; для блоков есть более ранний `FunctionalBlockHandler#onItemChanged` |

```java
@EventHandler
public void onOpen(GuiOpenEvent e) {
    if (!"shop".equals(e.getGui().name())) return;
    if (!e.getPlayer().hasPermission("shop.use")) {
        e.setCancelled(true);
    }
    StorageKey key = e.getStorageKey(); // с учётом override
}

@EventHandler
public void onClick(GuiSlotClickEvent e) {
    if (e.getSlotType() == SlotType.RESULT && e.getClick().isRightClick()) {
        e.setInteractionCancelled(true);
        // кастомная логика продажи
    }
}
```

Дублируются в Skript (`on cgui open/close/click/drag`) и Denizen (`on cgui ...`) — см. [`API.md#10`](API.md#10-skript-и-denizen) и [`MECHANICS.md#события-api`](MECHANICS.md#события-api).

### Примеры

**Создать и открыть персональный магазин:**

```java
Gui gui = GuiBuilder.named("shop")
        .title("§6Магазин")
        .size(27)
        .storage(StorageType.PERSONAL)
        .slots(List.of(10,11,12,13,14,15,16), SlotType.CONTAINER)
        .slot(22, SlotType.RESULT)
        .design(0, new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
        .command(4, "say %player% открыл магазин", 0)
        .build();
CustomGuiAPI.registerGui(gui); // custom/shop.yml
CustomGuiAPI.openGui(player, "shop");
```

**Временное открытие чужого GUI:**

```java
CustomGuiAPI.openGui(player, "shop", StorageType.TEMPORARY);
// предметы вернутся при закрытии, ничего не сохранится
```

**Работа с хранилищем без GUI:**

```java
List<ItemStack> items = CustomGuiAPI.readStorage(StorageType.GLOBAL, "", "shop.yml");
items.set(0, new ItemStack(Material.DIAMOND, 8));
CustomGuiAPI.writeStorage(StorageType.GLOBAL, "", "shop.yml", items);
```

**Блоки:**

```java
CustomGuiAPI.registerBlockGui("myblocks:atm", "bank");
CustomGuiAPI.registerBlockGui("craftengine:atm", "bank");
```

**Жизненный цикл в твоём плагине:**

```java
@Override public void onEnable() {
    if (!CustomGuiAPI.isInitialized()) return;
    CustomGuiAPI.registerGui(buildAuction(), true);
}
@Override public void onDisable() {
    CustomGuiAPI.unregisterGui("auction_dynamic", true);
}
```

Больше примеров — в [`API.md`](API.md).

---

## Структура проекта

```
CustomGuiReworked/
├── API.md                          # Гайд по API
├── EXAMPLES.md                     # Готовые примеры (от первого GUI до котла)
├── MECHANICS.md                    # Внутренние механики
├── README.md                       # Этот файл
├── docs/                           # Wiki-документация (Home/api/smart-block/scripts/examples)
├── build.gradle                    # Сборка, зависимости, публикация
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/
├── src/main/
│   ├── java/dev/moonaticks/customGuiReworked/
│   │   ├── CustomGuiReworked.java  # Main plugin class
│   │   ├── api/                    # Публичный API
│   │   │   ├── GuiService.java
│   │   │   ├── CustomGuiAPI.java
│   │   │   ├── Gui.java
│   │   │   ├── GuiBuilder.java
│   │   │   ├── GuiCategory.java
│   │   │   ├── SlotType.java
│   │   │   ├── StorageType.java
│   │   │   ├── SlotCommand.java
│   │   │   ├── GuiServiceImpl.java
│   │   │   ├── functional/         # Функциональные блоки (FunctionalBlock, Data, Registry, CraftingRecipe)
│   │   │   ├── animation/          # DesignAnimation (кадры + stageForProgress)
│   │   │   └── event/              # Включая GuiSlotChangedEvent («было/стало»)
│   │   ├── gui/                    # Ядро GUI
│   │   ├── storage/                # Хранилище
│   │   ├── codec/                  # Кодеки предметов
│   │   ├── editor/                 # Редактор
│   │   ├── manager/                # Менеджер /gui
│   │   ├── integration/            # ItemsAdder / CraftEngine
│   │   ├── command/                # Команды
│   │   ├── listeners/              # Слушатели
│   │   ├── lang/                   # LanguageManager
│   │   ├── skript/                 # Skript интеграция
│   │   ├── denizen/                # Denizen интеграция
│   │   └── util/
│   └── resources/
│       ├── plugin.yml
│       ├── config.yml
│       └── lang/en.yml, ru.yml
│   └── test/java/...               # Unit-тесты
└── .github/
    ├── workflows/build.yml
    └── ISSUE_TEMPLATE/
```

Полное дерево сгенерировано из реального `find` — см. файлы выше.

---

## Сборка

- **JDK 25**, Gradle 9 (wrapper в репо) — см. [`build.gradle`](build.gradle) `targetJavaVersion = 25`
- `./gradlew build` → `build/libs/CustomGuiReworked.jar` (и `-sources.jar` благодаря `withSourcesJar()`)
- `./gradlew runServer` — Paper 26.2 тестовый сервер (плагин `xyz.jpenilla.run-paper`)
- `./gradlew test` — unit-тесты (в том числе категорий и пользовательских слотов), см. [`src/test/`](src/test/java/dev/moonaticks/customGuiReworked/)

Публикация: JitPack по тегу — `com.github.amper24:CustomGuiReworked:2.4.5` (первая сборка ~1-2 мин).

---

## Skript и Denizen (кратко)

Подробно — в [`API.md#10`](API.md#10-skript-и-denizen), [docs/scripts.md](docs/scripts.md) и исходниках [`skript/`](src/main/java/dev/moonaticks/customGuiReworked/skript/) / [`denizen/`](src/main/java/dev/moonaticks/customGuiReworked/denizen/).

**Skript** — события `on cgui open/close/click/drag` и главное `on cgui slot changed` (контекст: `event-player`, `event-string`, `event-number`, `event-location`, `cgui old/new item of event`); эффекты и выражения для всего нового:
```skript
on cgui slot changed:
    event-string is "cooking_pot"
    if event-number is 1, 2, 3, 10, 11, 12:
        set cgui working of event-location to true

set cgui local design of player at slot 5 to arrow stage item
set cgui local title of player to "&bКотёл 42%"
clear cgui local designs of player
set cgui working of location to true
set cgui block data of location key "cook" to "42"
set cgui block item at slot 24 of location to cooked stew
the cgui block of player
all cgui viewers of location
cgui progress stage of 51 out of 200 in 4 frames
```

**Denizen** — события (контексты `player/gui/slot/slot_type/block/old_item/new_item`), теги `<cgui...>` и изменения — механизмами `adjust` (в этой линии Denizen 1.3.x нет «mechanics»):
```denizen
on cgui slot changed:
    - if <context.gui> == cooking_pot && <context.slot> in 1, 2, 3, 10, 11, 12:
        - adjust <context.block> cgui_working:true

- adjust <player> cgui_local_design:[5|arrow item]
- adjust <player> cgui_local_title:"&bКотёл 42%"
- adjust <loc> cgui_block_data:[cook|42]
- adjust <loc> cgui_block_item:[24|cooked stew]

<cgui.working[<loc>]>  <cgui.block_of[<player>]>  <cgui.viewers[<loc>]>
<cgui.block_item[[<loc>]|24]>  <cgui.block_data[[<loc>]|cook]>
```

---

## Лицензия

MIT — см. [`LICENSE`](LICENSE).
