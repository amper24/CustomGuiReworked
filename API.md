# CustomGuiReworked — гайд по API для разработчиков плагинов

Фреймворк кастомных GUI для **Paper 26.2 (Java 25)**. Этот документ —
полное руководство по использованию CustomGuiReworked как библиотеки
из вашего плагина: подключение, создание/открытие интерфейсов,
программное хранилище, события, кастомные блоки и скриптовые интеграции.

> Требование на сервере: установленный плагин `CustomGuiReworked`
> совместимой сборки. Ваш плагин подключает API только как `compileOnly` —
> в свой jar классы фреймворка не вшиваются.
>
> **Версия 2.4.5:** категории и расширяемые `SlotType` доступны начиная с
> тега `2.4.5`. Выпущенная ранее `2.4.0` этих API не содержит. Для примеров
> ниже используйте одну и ту же версию `2.4.5` при сборке аддона и на сервере.
> Старые плагины, скомпилированные против `SlotType enum`, нужно пересобрать.

---

## 1. Подключение зависимости

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io") // API CustomGuiReworked
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    compileOnly("com.github.amper24:CustomGuiReworked:2.4.5")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://jitpack.io' }
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.2.build.123-stable'
    compileOnly 'com.github.amper24:CustomGuiReworked:2.4.5'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.amper24</groupId>
        <artifactId>CustomGuiReworked</artifactId>
        <version>2.4.5</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

JitPack собирает артефакт по тегу/релизу с GitHub при первом обращении
(первая сборка занимает ~1–2 минуты, потом раздаётся из кэша). Вместо
тега можно указать короткий хеш коммита или `main-SNAPSHOT`.

Если ваша среда не использует Maven-репозитории — скачайте
`CustomGuiReworked.jar` из релизов на GitHub и подключите его как
`compileOnly` файл:

```groovy
compileOnly files('libs/CustomGuiReworked.jar')
```

Для категорий и собственных `SlotType` используйте jar `2.4.5` —
файл из релиза либо указанные выше координаты JitPack. Установите
эту же версию на сервер. Версия `2.4.0` методов 4.1–4.2 не содержит.

### plugin.yml вашего плагина

```yaml
name: MyAddon
version: '1.0.0'
main: com.example.myaddon.MyAddon
api-version: '26.2'
softdepend: [CustomGuiReworked]
```

`softdepend` обеспечивает порядок включения, **если** CustomGuiReworked
установлен; сам по себе он не гарантирует наличие плагина. Если ваш
аддон целиком зависит от этого API, используйте `depend` вместо
`softdepend` (как в [полном примере](EXAMPLES.md#9-категории-и-пользовательские-типы-слотов)).
При опциональной интеграции не загружайте классы с прямыми ссылками на
API до проверки наличия CustomGuiReworked.

---

## 2. Доступ к API

Два эквивалентных способа.

### Через Bukkit Services (без классовой зависимости на этапе загрузки)

```java
RegisteredServiceProvider<GuiService> provider = Bukkit.getServicesManager()
        .getRegistration(GuiService.class);
if (provider == null) {
    getLogger().warning("CustomGuiReworked not installed");
    return;
}
GuiService api = provider.getProvider();
```

### Через статический фасад (если вы подключили API как зависимость)

```java
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;

if (!CustomGuiAPI.isInitialized()) {
    return;
}
Gui gui = CustomGuiAPI.getGui("shop");
```

Все методы фасада `CustomGuiAPI` делегируют в `GuiService` — это один
и тот же API. Ниже примеры на фасаде; сигнатуры сервиса идентичны.

**Важно:** работайте с инвентарями и регистрацией типов в основном
потоке сервера. Предметное хранилище пишет данные асинхронно; сохранение
описаний GUI (`custom/*.yml`) и категорий (`categories.yml`) выполняется
атомарно при регистрации на основном потоке — регистрируйте их при
`onEnable`/смене конфигурации, а не каждый тик. Не вызывайте Bukkit API
из своего фонового потока.

---

## 3. Модель интерфейса

GUI описывается тремя массивами длиной в количество слотов (9..54,
кратно 9):

| Элемент | Назначение |
|---|---|
| **Скелет** (`SlotType`) | правила взаимодействия для каждого слота |
| **Дизайн** | предметы оформления в DESIGN-слотах (не сохраняются) |
| **Команды** | команды, выполняемые по клику на слот |

Типы слотов:

| Тип | Игрок может | Персистентность |
|---|---|---|
| `DESIGN` | только кликать (кнопка); предмет — часть оформления | нет |
| `CONTAINER` | класть/забирать предметы | **да** |
| `CRAFT` | как CONTAINER, семантика «рецепта» | **да** |
| `FUEL` | как CONTAINER, семантика «топлива» | **да** |
| `RESULT` | только **забирать**; любая постановка заблокирована | нет |

Новые типы можно регистрировать через API (см. раздел 4.2). `SlotType`
теперь расширяемый класс с теми же пятью константами; для обновления
старых плагинов, скомпилированных против `enum`, требуется пересборка.
Кроме массивов, у GUI есть `category()` — ID раздела менеджера `/gui`;
по умолчанию `none`, подробности в разделе 4.1.

Типы хранилища (`StorageType`):

| Тип | Где лежат данные | Удаление данных |
|---|---|---|
| `BLOCK` | папка мира, регион-файлы JSON, привязка к координатам блока | при разрушении блока предметы выпадают |
| `PERSONAL` | `plugins/CustomGuiReworked/data/players/`, отдельная копия на игрока | живут, пока не удалить через API |
| `GLOBAL` | `data/globals/`, одна копия на всех | то же |
| `TEAM` | `data/teams/`, копия на команду scoreboard (fallback `default`) | то же |
| `TEMPORARY` | нигде; при закрытии все предметы возвращаются игроку | — |

---

## 4. Создание интерфейса

### Fluent-билдер (рекомендуется)

```java
ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
ItemMeta meta = pane.getItemMeta();
meta.displayName(Component.text(" "));
pane.setItemMeta(meta);

Gui gui = GuiBuilder.named("shop")
        .title("§6Магазин")               // поддерживаются legacy-цвета §
        .size(27)                          // 9/18/27/36/45/54
        // сначала size(), потом slot()/design()/command()
        .storage(StorageType.PERSONAL)
        .category("shops")               // по умолчанию "none"
        .slot(10, SlotType.CONTAINER)
        .slot(11, SlotType.CONTAINER)
        .slot(12, SlotType.RESULT)
        .design(0, pane)
        .design(4, createButton(Material.CHEST, "§aМагазин"))
        .command(4, "say %player% открыл магазин", 0)
        .command(22, "give %player% diamond 1", 5) // через 5 тиков
        .blockId("myblocks:shop")         // привязка к кастомному блоку
        .build();

// persist=true: запись в custom/shop.yml, переживёт рестарт
CustomGuiAPI.registerGui(gui, true);
```

### Прямое создание объекта

```java
Gui gui = new Gui("warp_menu");
gui.title("§3Warp menu");
gui.slots(36);                       // новые слоты по умолчанию DESIGN
gui.storage(StorageType.GLOBAL);
gui.setSlotType(15, SlotType.CONTAINER);
gui.setDesignItem(31, new ItemStack(Material.ENDER_PEARL));
gui.addCommand(new SlotCommand(31, "warp spawn", 0));
CustomGuiAPI.saveGui(gui);          // запись в файл по source()
```

`registerGui(gui)` без второго аргумента всегда персистентный
(`custom/`, переживает рестарт). `registerGui(gui, false)` создаёт
интерфейс только в памяти — он пропадёт после перезагрузки/рестарта,
файл не создаётся (удобно для динамических меню вашего плагина).

Если GUI с таким именем уже существует, он заменяется; повторная
регистрация переименованного экземпляра (`gui.rename(...)`) корректно
убирает старую привязку и файл.

### Жизненный цикл в вашем плагине

```java
@Override
public void onEnable() {
    if (!CustomGuiAPI.isInitialized()) return;
    CustomGuiAPI.registerGui(buildShop(), true);
}

@Override
public void onDisable() {
    // Не обязательно: фреймворк сам закроет меню и сбросит данные.
    // Если меню чисто ваше и должно исчезать вместе с плагином:
    CustomGuiAPI.unregisterGui("my_dynamic_menu", true);
}
```

### 4.1. Категории в `/gui` и редакторе

Категория — **метка для сортировки и фильтрации**, а не право доступа или
отдельное хранилище. У нового GUI `gui.category()` возвращает `"none"`;
это специальное значение «Без категории». Старые файлы без поля
`category` тоже попадают сюда. Категория не мешает открыть меню через
`openGui` или `/gui open`.

- В `/gui` кнопка **Категория** открывает отдельный список: **Все**
  (сброс фильтра), **Без категории** (`none`) и категории, по 27 на
  страницу. Выбор сочетается с поиском по имени GUI и пагинацией списка.
  Зарегистрированные категории видны даже без GUI; ID, встречающиеся
  только в файлах GUI, тоже видны (с иконкой `BOOK`).
- В `/gui edit <name>` (или `/gui create <name>`) выберите **Категория**.
  Там можно назначить существующую, выбрать **Без категории** или создать
  новую: введите в чат `id Название`, например `machines Механизмы`.
  Если название не указано, используется ID; иконка будет `BOOK`,
  описание пустым. `/cancel` отменяет ввод. Изменение GUI сохраняется
  сразу; созданное описание — в `categories.yml`.

Идентификатор категории нормализуется в нижний регистр и состоит из
`[a-z0-9_.-]+` с необязательным суффиксом `:[a-z0-9_.-]+`.
`none` зарезервирован: `new GuiCategory("none", ...)` запрещён, а
`gui.category("none")` или `gui.category(null)` снимает категорию.
Название/описание можно оформить `§`-цветами; иконка — `Material`
предмета. Если `icon == null` или материал не предмет, используется `BOOK`.

```java
GuiCategory machines = CustomGuiAPI.registerCategory(new GuiCategory(
        "myaddon:machines", "§6Механизмы", Material.FURNACE,
        "§7Печи, станки, генераторы")); // persist=true по умолчанию

Gui gui = GuiBuilder.named("ore_status").size(27)
        .category(machines)            // или .category("myaddon:machines")
        .build();
CustomGuiAPI.registerGui(gui, true);

// Категория только на время работы сервера (без записи описания в файл):
CustomGuiAPI.registerCategory(new GuiCategory("temporary", "Временное"), false);

// Поменять категорию зарегистрированного GUI:
gui.category(GuiCategory.NONE);   // снять привязку
CustomGuiAPI.saveGui(gui);        // сохранить изменение самого GUI
```

Сигнатуры доступны как через `CustomGuiAPI`, так и через `GuiService`:

```java
GuiCategory registerCategory(GuiCategory category);                   // persist=true
GuiCategory registerCategory(GuiCategory category, boolean persist);
GuiCategory getCategory(String id);                                   // null, если нет описания
List<GuiCategory> getCategories();                                    // только описания
boolean unregisterCategory(String id);                               // удаляет описание, не GUI
```

В файле GUI (`tables/<name>.yml` или `custom/<name>.yml`) хранится **только
ID** категории; имя, иконка и описание — отдельно в
`plugins/CustomGuiReworked/categories.yml`. Например:

```yaml
# categories.yml
categories:
  - id: 'myaddon:machines'
    name: '§6Механизмы'
    icon: FURNACE
    description: '§7Печи, станки, генераторы'
```

```yaml
# custom/yaml_example.yml — отдельный 9-слотовый GUI
name: yaml_example
title: '§6Пример'
slots: 9
storage: personal
category: 'myaddon:machines'
skeleton:
  - design             # слот 0
  - 'myaddon:ore'      # слот 1
  - design             # слот 2
  - 'myaddon:indicator' # слот 3
  - design
  - design
  - design
  - design
  - design             # слот 8
```

Количество записей в `skeleton` обязано совпадать с `slots`; иначе
скелет будет сброшен в `DESIGN`. Само наличие ID в YAML не создаёт
правил типа: они приходят только из Java-регистрации (раздел 4.2).
Если аддон вызывает `registerGui(gui, true)` при каждом запуске,
ручные правки его `custom/*.yml` могут быть перезаписаны — меняйте
исходное описание GUI в самом аддоне.

Повторная регистрация с тем же ID обновляет описание. `persist=false`
оставляет его только в памяти (а при совпадении с сохранённым ID снимает
старое описание с диска); привязка GUI от этого не зависит. Если описание
удалено или ещё не загружено, GUI сохраняет ID и остаётся в фильтре.
`getCategories()` возвращает только описания, не `none` и не ID из GUI
без описания. После **ручного** изменения `categories.yml` используйте
команду `/gui reload` для перечитывания файла.

### 4.2. Собственные типы слотов и связи между ними

`SlotType` — расширяемый класс, а не enum. Пять встроенных констант
`DESIGN`, `CONTAINER`, `CRAFT`, `RESULT`, `FUEL` сохранены. Создавайте
собственные типы с **уникальным namespaced ID** вида `myaddon:ore`
(`[a-z0-9_.-]+:[a-z0-9_.-]+` — без пробелов; регистр приводится к нижнему).
Встроенные ID нельзя переопределять. По умолчанию новый тип **заперт**:
вставка, изъятие, отслеживание и сохранение выключены. Сервис и
статический фасад предоставляют одинаковые методы:

```java
SlotType registerSlotType(SlotType type);  // возвращает канонический объект
boolean unregisterSlotType(String id);    // отключить правила, сохранив ID
SlotType getSlotType(String id);           // null, если тип не зарегистрирован
List<SlotType> getSlotTypes();             // зарегистрированные, включая встроенные
```

Этот пример делает принимающий только железную руду слот и связанный
с ним **незабираемый индикатор**. Это безопасная демонстрация связи,
а не рецепт, выдающий бесплатные предметы:

```java
SlotType input = CustomGuiAPI.registerSlotType(SlotType.builder("myaddon:ore")
        .displayName("§6Железная руда").icon(Material.IRON_ORE)
        .description("§7Сюда можно положить только железную руду")
        .allowInsert(true).allowTake(true).persist(true)
        .acceptInsert(ctx -> ctx.item().getType() == Material.IRON_ORE)
        .build());

SlotType indicator = CustomGuiAPI.registerSlotType(SlotType.builder("myaddon:indicator")
        .displayName("§aИндикатор").icon(Material.LIME_DYE)
        .track(true)                     // события изменений без сохранения на диск
        .watch(input)                    // indicator наблюдает изменения input
        .onRelatedChange(relation -> {
            ItemStack ore = relation.change().getNewItem();
            Inventory inv = relation.change().getInventory();
            ItemStack signal = ore == null ? null : new ItemStack(Material.LIME_DYE);
            CustomGuiAPI.setSlotItem(inv, relation.relatedSlot(), signal);
        })
        .build());                      // allowTake/allowInsert остаются false

Gui gui = GuiBuilder.named("ore_status").size(27)
        .category("myaddon:machines")
        .storage(StorageType.PERSONAL)
        .slot(13, input).slot(22, indicator)
        .build();
CustomGuiAPI.registerGui(gui, true);
// gui.slotsOf(input) -> [13]; gui.slotType(22).id() -> "myaddon:indicator"
```

Если при открытии в слоте уже лежит сохранённая руда, изменение входа
не происходит — `onRelatedChange` **не** вызывается автоматически при
открытии. Инициализируйте индикатор в `GuiOpenEvent`. Полный рабочий
пример с такой инициализацией и отключением типов —
[`EXAMPLES.md §9`](EXAMPLES.md#9-категории-и-пользовательские-типы-слотов).

| Настройка `SlotType.builder(id)` | Назначение |
|---|---|
| `displayName(String)`, `description(String)`, `icon(Material)` | Название, подсказка и иконка в редакторе (иконка — предмет). |
| `allowInsert(true)`, `allowTake(true)` | Разрешить класть или забирать предметы; по умолчанию оба действия запрещены. |
| `persist(true)` | Сохранять содержимое в постоянном хранилище, возвращать из `TEMPORARY` при закрытии; автоматически включает отслеживание. **Обязательно**, если включена вставка. |
| `track(true)` | Отслеживать фактические изменения и вызывать `GuiSlotChangedEvent` даже для неперсистентного output/индикатора. |
| `acceptInsert(ctx -> ...)`, `acceptTake(ctx -> ...)` | Дополнительные фильтры; контекст: `ctx.gui()`, `ctx.inventory()`, `ctx.slot()`, `ctx.player()`, `ctx.item()`. Проверки выполняются на основном потоке; не изменяйте предмет/инвентарь внутри предиката. |
| `decorative(true)` | Неизменяемый дизайн-слот (как `DESIGN`), доступен экрану дизайна и локальным оверрайдам; вставка, изъятие, сохранение, отслеживание запрещены. |
| `localDesign(true)` | Разрешить per-viewer оверрайд дизайна неперсистентного output; нельзя сочетать со вставкой или сохранением. |
| `onClick(e -> ...)` | Клик по слоту, в том числе по декоративному; `e.setCancelled(true)` отменяет команды, `e.setInteractionCancelled(true)` — также перемещение предметов. |
| `onChange(e -> ...)` | Изменение содержимого слота этого типа (после сравнения с baseline). |
| `watch(input)` / `watch("otherplugin:input")`, `onRelatedChange(relation -> ...)` | Направленная связь: уведомить каждый **другой** слот этого типа в том же GUI, если изменился слот наблюдаемого типа. |

`Builder.build()` откажется создать тип с `allowInsert(true)` без
`persist(true)`, `decorative(true)` с интерактивностью/сохранением или
`localDesign(true)` вместе со вставкой/сохранением. Зарегистрируйте тип
**до** передачи в `GuiBuilder.slot` / `Gui.setSlotType` и используйте
**возвращённый** канонический объект. `getSlotType(id)` вернёт `null`
для неизвестного или отключённого типа; `getSlotTypes()` и
`SlotType.values()` возвращают только зарегистрированные типы, включая
встроенные (для выбора в редакторе). `gui.slotType(slot).id()` работает
и для неразрешённого ID.

Связь направлена: `indicator.watch(input)` означает «изменился input →
уведомить indicator», а не наоборот. В `SlotRelationEvent`
`change()` — `GuiSlotChangedEvent` исходного слота с `getSlot()`,
`getOldItem()`, `getNewItem()`, `getInventory()`, `getPlayer()`;
`relatedSlot()` — индекс **конкретного** слота-получателя. Если в GUI
несколько слотов типа indicator, вызов будет для каждого (кроме самого
изменившегося слота). Источник должен быть отслеживаемым (`persist`
или `track`), событие отправляется только при реальном изменении
содержимого. В обычном случае событие поступает на следующем тике после
клика; изменения блока через `setBlockSlotItem` могут уведомить открытых
зрителей сразу. Сначала выполняются обработчик функционального блока и
Bukkit-событие `GuiSlotChangedEvent`, затем `onChange` источника и
`onRelatedChange` наблюдателей. Обработчики работают в основном потоке;
исключения в пользовательских фильтрах отменяют действие, а в callback
пишутся в лог. Избегайте взаимных `watch` с записью в слоты без проверки
на изменение, чтобы не создать цикл.

`CustomGuiAPI.setSlotItem(inventory, slot, item)` (или метод `GuiService`)
**меняет предмет только в указанном инвентаре сессии GUI**, не у других
зрителей; работает и в `GuiOpenEvent` после прикрепления `GuiHolder`.
`null`/`AIR` очищает слот. Возвращает `false`, если это не инвентарь
актуального `GuiHolder`, индекс вне границ или слот не
отслеживается/декоративный. Изменение персистентного слота будет
записано в хранилище и вызовет `GuiSlotChangedEvent` при следующей
реконсиляции; у неперсистентного — только событие, предмет не переживёт
закрытия/рестарта. Для изменения **закрытого функционального блока**
используйте `setBlockSlotItem` (только отслеживаемые слоты); не путайте
его с локальным `setLocalDesign` для оформления одного игрока.

В файле GUI в `skeleton:` записывается **только ID** типа, а не
Java-предикаты/колбэки/связи. Регистрируйте правила при каждом
`onEnable` своего плагина (`softdepend: [CustomGuiReworked]`). Если
namespaced тип ещё не зарегистрирован или был отключён, GUI не теряет ID
и сохранённые данные слота, но слот остаётся **заблокированным** (в
редакторе — иконка `BARRIER`) до регистрации; незнакомое старое имя без
namespace читается как `DESIGN`. Используйте
`CustomGuiAPI.unregisterSlotType(id)` при `onDisable`: сервис сначала
закроет соответствующие открытые окна/редактор с сохранением, затем
отключит тип. Регистрация через сервис при уже открытых окнах также
закроет их **до** активации (откройте заново); повторная регистрация с
тем же ID обновляет правила. Не вызывайте `SlotType.register(...)`
напрямую для горячей замены открытых GUI: он не закрывает окна.

Готовые `matchesCraft`, `consumeFuel`, `produceResult` работают со
**встроенными** `CRAFT`/`FUEL`/`RESULT`, а не произвольными типами.
Для своей механики используйте события, связи и собственную проверку
рецепта/расход ресурсов. Если создать забираемый «результат» без расхода
входных предметов, игроки смогут получать его повторно. Для общих
хранилищ учитывайте [ограничение нескольких зрителей](MECHANICS.md#хранилище).

**Миграция API:** прежний `SlotType` был `enum`, теперь это класс.
Сторонние плагины необходимо **пересобрать** с jar новой версии;
`switch (slotType)` как по enum нужно переписать (например,
`if (slotType == SlotType.CRAFT)` либо проверку `slotType.id()`).
`EnumSet`/`EnumMap` и `Enum.valueOf(SlotType.class, ...)` также больше не
подходят; используйте обычные `Set`/`Map` или IDs. Методы `name()`,
`valueOf(...)`, `values()` сохранены для удобства, но это **не** бинарная
совместимость с ранее собранным jar.

---

## 5. Открытие интерфейсов

```java
// Обычное открытие (тип хранилища берётся из самого GUI)
CustomGuiAPI.openGui(player, "shop");

// Временная подмена типа хранилища на это открытие:
// например, общую таблицу показать игроку как временную
CustomGuiAPI.openGui(player, "shop", StorageType.TEMPORARY);

// BLOCK-хранилище требует координаты блока:
Location blockLocation = customBlock.getLocation();
CustomGuiAPI.openGui(player, "shop", blockLocation);

// Какой GUI открыт у игрока прямо сейчас?
Gui open = CustomGuiAPI.getOpenGui(player);
```

Полная интроспекция через сервис:

```java
GuiService api = CustomGuiAPI.service();
Set<String> names = api.getGuiNames();
List<Gui> all = api.getGuis();
String source = api.sourceOf("shop"); // "table" | "custom" | "runtime" | "none"
Gui reloaded = api.loadGui("shop");   // перечитать одно меню из файла
```

---

## 6. Программное хранилище

Чтение/запись содержимого слотов без открытия инвентаря.

```java
// PERSONAL — владелец = имя игрока
List<ItemStack> items = CustomGuiAPI.readStorage(
        StorageType.PERSONAL, player.getName(), "shop.yml");

// GLOBAL — владелец игнорируется (передайте "")
List<ItemStack> globalItems = CustomGuiAPI.readStorage(
        StorageType.GLOBAL, "", "shop.yml");

// TEAM — владелец = имя команды scoreboard
List<ItemStack> teamItems = CustomGuiAPI.readStorage(
        StorageType.TEAM, "red", "shop.yml");

// BLOCK — владелец в формате «мир:x,y,z»
List<ItemStack> blockItems = CustomGuiAPI.readStorage(
        StorageType.BLOCK, "world:12,64,-7", "shop.yml");
```

Запись (список = слоты по порядку, воздух = пустой слот):

```java
List<ItemStack> contents = new ArrayList<>();
contents.add(new ItemStack(Material.DIAMOND, 8)); // слот 0
contents.add(null);                               // слот 1 пустой
contents.add(new ItemStack(Material.GOLD_INGOT)); // слот 2
CustomGuiAPI.writeStorage(StorageType.GLOBAL, "", "shop.yml", contents);

// Полная очистка таблицы:
CustomGuiAPI.deleteStorage(StorageType.PERSONAL, player.getName(), "shop.yml");
```

Соглашения:

- имя таблицы — это `gui.fileName()`, то есть `<имя>.yml`; суффикс
  добавляется автоматически, если передать имя без `.yml`;
- запись атомарна (временный файл + `ATOMIC_MOVE`), коалесится и
  немедленно фиксируется при закрытии/выходе/выключении — отдельно
  «сохранять» после `writeStorage` не нужно;
- одновременные правки разных слотов несколькими сессиями не затирают
  друг друга (фреймворк пишет диф по baseline, а не весь массив).

---

## 7. События API

Все события — обычные Bukkit-события в пакете
`dev.moonaticks.customGuiReworked.api.event`. Регистрируются через
стандартный `registerEvents`.

### GuiOpenEvent / GuiCloseEvent

```java
@EventHandler
public void onOpen(GuiOpenEvent event) {
    if (!"shop".equals(event.getGui().name())) return;
    Player player = event.getPlayer();
    if (!player.hasPermission("shop.use")) {
        event.setCancelled(true); // инвентарь не откроется
        player.sendMessage(Component.text("Магазин закрыт!", NamedTextColor.RED));
    }
    // Фактический ключ хранилища (с учётом storage override):
    StorageKey key = event.getStorageKey();
}

@EventHandler
public void onClose(GuiCloseEvent event) {
    // Срабатывает ПОСЛЕ финального сохранения/возврата предметов.
}
```

### GuiSlotClickEvent — клики по слотам верхнего инвентаря

```java
@EventHandler
public void onClick(GuiSlotClickEvent event) {
    if (event.getSlotType() != SlotType.RESULT) return;

    // Полный контекст ванильного клика:
    ClickType click = event.getClick();              // LEFT/RIGHT/SHIFT_LEFT/...
    InventoryAction action = event.getAction();
    ItemStack inSlot = event.getCurrentItem();
    ItemStack cursor = event.getCursor();
    int hotbar = event.getHotbarButton();           // для NUMBER_KEY, иначе -1

    if (click.isRightClick()) {
        event.setInteractionCancelled(true);       // отменить и команды, и сам клик MC
        // ваша логика «продажи» ...
    } else {
        event.setCancelled(true); // не выполнять привязанные команды слота,
                                  // но ванильное перемещение предмета оставить
    }
}
```

Семантика двух уровней отмены:

| Метод | Команды слота | Ванильный клик |
|---|---|---|
| ничего | выполняются | проходит |
| `setCancelled(true)` | **не выполняются** | проходит |
| `setInteractionCancelled(true)` | **не выполняются** | **отменён** |

События вызываются и по DESIGN-кнопкам, и по зарегистрированным
кастомным типам. Для них callback `SlotType.onClick` с тем же событием
выполняется **до** внешних Bukkit-подписчиков. Через `getHandle()`
доступно исходное `InventoryClickEvent` для сложных сценариев.

### GuiDragEvent — растаскивание предмета по слотам

```java
@EventHandler
public void onDrag(GuiDragEvent event) {
    if (event.getTopSlots().contains(22)) {
        event.setCancelled(true); // отменить весь drag
    }
    event.getHandle(); // исходный InventoryDragEvent
}
```

Drag по DESIGN/RESULT и кастомным слотам, запрещающим вставку данного
предмета, фреймворк отменяет ещё до события; подписчики получают только
допустимые операции.

### GuiSlotChangedEvent — предмет в слоте изменился (с «было/стало»)

Главная точка для запуска крафта/топлива/анимаций **от действий игрока**:

```java
@EventHandler
public void onSlotChanged(GuiSlotChangedEvent event) {
    if (event.getSlotType() != SlotType.CRAFT) return;
    ItemStack before = event.getOldItem(); // null — слот был пуст
    ItemStack after = event.getNewItem();  // null — слот стал пуст
    // after — ФАКТИЧЕСКОЕ содержимое слота, изменения уже применены
}
```

Чем отличается от `GuiSlotClickEvent`:

| | `GuiSlotClickEvent` | `GuiSlotChangedEvent` |
|---|---|---|
| Когда | в момент клика (инвентарь ещё старый) | после применения (обычно следующий тик) |
| Что видит | click type, cursor, hotbar | **предметы «было/стало»**, итог любых действий |
| Запускается от | клика по верхнему GUI | клика, драга, shift-click, `setSlotItem`, выдачи/сбора, `produceResult`, `consumeFuel` |
| Слоты | только верхний инвентарь | отслеживаемые встроенные CONTAINER/CRAFT/FUEL/**RESULT** и custom с `track`/`persist` (не DESIGN) |
| Отмена | да (команды/клик) | нет (информационное) |

События:

- вызываются по каждому изменившемуся слоту обычно на следующий тик
  (реконсиляция по baseline — та же, что пишет в хранилище), поэтому
  несколько быстрых кликов дают финальный диф, а не серию;
  `setBlockSlotItem` может сразу уведомить открытых зрителей блока;
- НЕ вызываются для локальных дизайн-оверрайдов (`setLocalDesign`,
  анимации, прогресс-предметы в RESULT из `onTick`) — baseline
  синхронизируется вместе с ними;
- `getGui()` — GUI сессии; `getInventory()` — инвентарь; `getPlayer()` —
  игрок сессии (может быть null при програмном закрытии);
- для функциональных блоков тот же вызов доступен колбэком
  `.onItemChanged(...)` (раздел 15) — он срабатывает **до** внешних
  слушателей этого события;
- для TEMPORARY-хранилища событие тоже вызывается (персистентность не
  требуется);
- то же событие доступно через Skript (`on cgui slot changed`) и
  Denizen (`on cgui slot changed`) — см. раздел 10. Скриптовая
  регистрация **новых типов** слотов не предусмотрена: описание
  правил делается через Java API.

---

## 8. Кастомные блоки (ItemsAdder / CraftEngine)

Привязка программно:

```java
CustomGuiAPI.registerBlockGui("itemsadder:ruby_ore", "shop");
// несколько ID на одно меню — просто вызывайте многократно
CustomGuiAPI.registerBlockGui("craftengine:atm", "shop");

// Узнать меню по ID блока:
Gui gui = CustomGuiAPI.getBlockGui("itemsadder:ruby_ore");

// Отвязать:
CustomGuiAPI.unregisterBlockGui("itemsadder:ruby_ore");
```

Поведение из коробки:

- правый клик по блоку открывает привязанный GUI с BLOCK-хранилищем;
- слом блока: открытые меню закрываются (без повторного сохранения),
  все сохранённые предметы выпадают на землю, данные региона удаляются;
- привязки хранятся в yml самого меню и переживают рестарты;
- классы ItemsAdder/CraftEngine загружаются рефлексивно только при
  установленных плагинах — жёсткой зависимости нет.

Поиск привязки — O(1) по индексу, без учёта регистра, с fallback на
суффикс после `:`. При коллизии одного ID на два меню в лог пишется
warning и выигрывает последняя загруженная привязка.

---

## 9. Редактор и команды (для админов)

| Команда | Действие |
|---|---|
| `/gui` | меню управления (категории, поиск, пагинация) |
| `/gui create <name>` | создать меню и открыть редактор |
| `/gui edit <name>` | редактировать существующее |
| `/gui open <name>` | открыть меню |
| `/gui delete <name>` | удалить меню (данные хранилища остаются) |
| `/gui list` | список меню |
| `/gui command ...` | привязка команд к слотам |
| `/gui reload` | перечитать конфиг, язык, категории и все меню |

В `/gui` кнопка **Категория** открывает экран **Все** / **Без
категории** / описания категорий и неизвестные ID из GUI. Поиск по имени
сохраняется при переключении категории, номера страниц сбрасываются.

Редактор — полностью внутриигровой: размер, скелет (клик — смена
встроенного/зарегистрированного кастомного типа, shift-клик — DESIGN),
дизайн (перетаскивание предметов), заголовок, категория и привязки
блоков. Категория назначается кнопкой или создаётся в чате `id Название`;
незарегистрированный тип в скелете виден как `BARRIER`, но использовать
его предметы нельзя. Ввод отменяется `/cancel`, таймаут — 5 минут.
Подробности для категорий и ID типов — в разделах 4.1–4.2.

Меню, созданные плагинами через API, лежат в папке `custom/`,
созданные в редакторе — в `tables/`; данные хранилища обоих —
в общем каталоге `data/` (и регионах мира), поэтому меню можно
«перемещать» между источниками без потери предметов.

---

## 10. Skript и Denizen

### Skript

```skript
on cgui open:
    event-string is "shop"
    send "Добро пожаловать!"

on cgui click:
    event-string is "shop"
    event-number is 22
    cancel event
    execute console command "give %event-player's name% diamond 1"

on cgui drag:
    # контекст события: event-player и event-string (имя GUI);
    # отмена события запрещает весь drag
    cancel event

open the cgui "shop" to player
close the cgui of player
```

Условия/выражения: `cgui "shop" exists`, `the cgui of player`,
`all cgui names`, `the cgui size of "shop"`,
`the cgui title of "shop"`, `the cgui storage of "shop"`,
`the cgui item at slot 3 from "shop" for player`,
`all the cgui items from "shop" for player`,
`%location% is cgui working`.

#### Слот-изменения, локальные оверрайды и работа блока (Skript)

Главное для «умных» блоков из скрипта — событие `cgui slot changed`
(предмет положен/забран/перенесён; на следующий тик, предметы
фактические):

```skript
on cgui slot changed:
    # контекст: event-player, event-string (имя GUI), event-number (слот),
    #           event-location (блок, если блок-GUI),
    #           cgui old item of event / cgui new item of event
    event-string is "cooking_pot"
    if event-number is 1, 2, 3, 10, 11, or 12:
        # заложили/убрали ингредиент: запустить/остановить работу
        set cgui working of event-location to true
```

Полный набор (раздел 17 — как это складывается):

```skript
# Локальные оверрайды (стрелка/огонь/прогресс, ванильные и кастомные предметы):
set cgui local design of player at slot 5 to arrow stage item
set cgui local title of player to "&bКотёл 42%"
clear cgui local designs of player

# «Работа» блока (варка без открытого GUI; ID блока — через CraftEngine):
set cgui working of location to true
if location is cgui working:
    # ...

# Данные блока (прогресс/флаги; с явным ID — второй вариант):
set cgui block data of location key "cook" to "42"
set cgui block data of location id "farmersdelight:cooking_pot" key "cook" to "42"
do something with cgui block data of location key "cook"

# Слоты блока (при закрытом GUI; зрители перерисуются + cgui slot changed):
set cgui block item at slot 24 of location to cooked stew
if cgui block item at slot 24 of location is an item:
    give player cgui block item at slot 24 of location

# Прочее:
the cgui block of player           # локация блока, GUI которого открыт
all cgui viewers of location       # зрители блока
cgui progress stage of 51 out of 200 in 4 frames   # индекс кадра стрелки (0..3)
cgui old item of event / cgui new item of event    # «было/стало» в cgui slot changed
```

### Denizen

```denizen
on cgui click:
    - if <context.gui> == shop:
        - if <context.slot> == 22:
            - determine cancelled
            - execute as_server "give <player.name> diamond 1"

on cgui drag:
    - announce "drag over <context.slots>"
```

Контексты: `context.player`, `context.gui`, `context.slot` (click),
`context.slots` (drag).

#### `cgui slot changed` + механизмы (Denizen)

В этой линии Denizen (1.3.x) нет «mechanics» — изменения значений
делаются **механизмами** объектов командой `adjust`:

```denizen
# Игрок положил/забрал/перенёс предмет (на следующий тик, предметы фактические):
on cgui slot changed:
    - if <context.gui> == cooking_pot:
        - if <context.slot> in 1, 2, 3, 10, 11, 12:
            - adjust <context.block> cgui_working:true
        - if <context.slot_type> == result && <context.slot> == 24 && <context.new_item> is an empty item:
            - # результат забрали — снять работу, если нечего варить
            - adjust <context.block> cgui_working:false

# Стрелка прогресса / локальные оверрайды (ванильные и кастомные предметы):
- adjust <player> cgui_local_design:[5|arrow item]
- adjust <player> cgui_local_design:[<context.block>|10|iron_ingot]  # пер-блок
- adjust <player> cgui_local_title:"&bКотёл 42%"
- adjust <player> cgui_clear_local_title
- adjust <player> cgui_clear_local_design

# Данные и слоты блока (работают при закрытом GUI):
- adjust <loc> cgui_block_data:[cook|42]
- adjust <loc> cgui_block_data:[farmersdelight:cooking_pot|cook|42]
- adjust <loc> cgui_block_item:[24|cooked stew]
- if <cgui.block_data[[<loc>]|cook]> > 0: ...
- if <cgui.block_item[[<loc>]|24]> is an item: ...
- if <cgui.working[<loc>]>: ...
- <cgui.block_of[<player>]>   # локация блока, GUI которого открыт игрок
- <cgui.viewers[<loc>]>       # зрители блока
```

Ввод механизмов и «мульти-тегов» — список (разделитель `|`). Локацию
рекомендуется оборачивать в квадратные скобки, иначе запятые координат
столкнутся с разделителем: `<cgui.block_item[[<loc>]|24]>`.

---

## 11. Формат предметов и NBT

Содержимое слотов на диске хранится строкой-пейлоадом с тегом кодека:

| Тег | Кодек | Когда используется |
|---|---|---|
| `n1:` | NBT-API (`NBTContainer.setItemStack`, JSON) | активен при установленном NBTAPI 2.16+ — эталонная полнота NBT |
| `b2:` | нативный Paper `ItemStack.serializeAsBytes()` + Base64 | fallback без NBTAPI: компоненты, PDC, зачарования — всё, что умеет сам Paper |
| `b1:` | старый YAML-map формат ранних 2.1.x | только **чтение**, при следующем сохранении переписывается в активный кодек |

Плюс прозрачная миграция данных 1.x («голый» NBTAPI-JSON без тега;
числа с суффиксами `b/s/f/d` санитизируются; без NBTAPI
восстанавливаются тип, количество, damage, имя, lore и custom model
data). Любой пейлоад читается любым кодеком по его тегу — можно
ставить/убирать NBTAPI без потери содержимого.

---

## 12. Гарантии и потоковая модель

- **Запись без лагов:** один выделенный I/O-поток; основной поток
  никогда не ждёт диск (чтение при открытии — асинхронный preload,
  гонка устаревших открытий разрешена по id запроса).
- **Без потерь:** неудачная запись остаётся «грязной» и повторяется
  через 5 с; автосейв каждые 600 тиков; на закрытии меню, выходе
  игрока и выключении сервера запись гарантирована синхронно.
- **Без повреждений:** все файлы (данные, регион-файлы блоков, yml меню)
  пишутся атомарно через уникальный `.tmp-<uuid>` + `ATOMIC_MOVE`;
  осиротевшие tmp после краха подметаются при старте/первом доступе.
- **Без дюпов:** DESIGN не выносят через shift/drag/double-click,
  в RESULT ничего не положить никаким способом; при разрушении блока
  открытые меню сначала синхронизируются в кэш (предмет на курсоре
  не выпадет второй раз); при выключении плагина все меню закрываются.
- **Регионы мира:** блок-данные лежат в
  `<мир>/CustomGuiReworked/blocks/<x/16>_<z/16>.json`, флашатся при
  выгрузке мира и не пишутся в NBT самого блока (нулевой конфликт с
  механиками блога).

Конфигурация (`plugins/CustomGuiReworked/config.yml`):

```yaml
storage:
  autosave-ticks: 600      # страховочный автосейв
  max-cached-views: 10000  # лимит кэша таблиц в памяти
  coalesce-ms: 1000        # пауза схлопывания записей (закрытие пишет сразу)
commands:
  execute-as-op: false     # true = старое поведение setOp (не рекомендуется)
integration:
  skript: true
  denizen: true
```

## 13. Частые ошибки

1. **`IllegalStateException: service not registered`** — вы дёргаете
   API до включения CustomGuiReworked. Добавьте `softdepend` и вызывайте
   из своего `onEnable`.
2. **`size()` после `slot()`/`design()`** — сначала задайте размер,
   потом наполняйте меню (билдер делает это в правильном порядке).
3. **BLOCK-хранилище без локации** — открытие с BLOCK-типом обязано
   получить `Location` блока (иначе игроку придёт сообщение об ошибке).
4. **Вызовы из async-потока** — все методы, открывающие/меняющие
   инвентари, только из основного потока; дисковый слой сам асинхронный.
5. **Вшивание классов в jar (shade)** — так не нужно: это
   `compileOnly`/`provided`, плагин предоставляет API на рантайме.
6. **`GuiCategory`/`SlotType.builder` не найдены** — версия `2.4.0`
   предшествует этим функциям. Подключите `2.4.5` как `compileOnly` и
   установите соответствующий jar на сервер.
7. **Кастомный слот показывает `BARRIER` и не принимает предметы** —
   в `skeleton:` сохранён ID, но соответствующий аддон не зарегистрировал
   тип через `registerSlotType` на `onEnable` или отключил его.
8. **Индикатор пуст после открытия с сохранённым входом** — `watch`
   срабатывает только при изменении, а не при загрузке из хранилища;
   инициализируйте его в `GuiOpenEvent` (пример — EXAMPLES.md §9).

## 14. Локальные оверрайды (per-viewer)

Для каждого открытого инвентаря (сессии) можно временно подменить
название окна и предметы в DESIGN/RESULT слотах — только для этого
конкретного игрока, без изменения файла GUI и без влияния на других
игроков/блоки. Оверрайды живут в holder'е сессии, очищаются при
закрытии GUI и никогда не пишутся в файл.

```java
// Название — только для этого игрока (legacy § цвета поддерживаются)
CustomGuiAPI.setLocalTitle(player, "§6Печь [" + block.getBlockX() + "]");
String title = CustomGuiAPI.getLocalTitle(player);
CustomGuiAPI.clearLocalTitle(player);

// Дизайн — предметы в DESIGN/RESULT слотах
CustomGuiAPI.setLocalDesign(player, 4, progressItem);
CustomGuiAPI.setLocalDesigns(player, Map.of(10, fluid1, 11, fluid2));
ItemStack cur = CustomGuiAPI.getLocalDesign(player, 4);
CustomGuiAPI.clearLocalDesign(player, 4);
CustomGuiAPI.clearAllLocalDesigns(player);

// Пер-блок + пер-плеер (для функциональных блоков)
CustomGuiAPI.setLocalDesign(player, blockLocation, 4, progressItem);
CustomGuiAPI.setLocalTitle(player, blockLocation, "§bБочка 70%");

// Интроспекция
Location block = CustomGuiAPI.getOpenBlockLocation(player); // null, если не BLOCK
List<Player> viewers = CustomGuiAPI.getViewers(blockLocation);

// Утилита: подготовить предмет как дизайн (maxStackSize + PDC, анти-дюп)
ItemStack safe = CustomGuiAPI.prepareDesignItem(item);
```

Слоты `CONTAINER`/`CRAFT`/`FUEL` для оверрайдов недоступны
(`IllegalArgumentException`) — их содержимое персистится в хранилище,
и виртуальный предмет мог стать реальным (риск дюпа). Анти-дюп защита
дизайна (PDC-маркер + `maxStackSize`) и `rescueDesignItems` учитывают
локальные оверрайды: чужой предмет из DESIGN-слота возвращается
игроку, а на место встает именно локальный «ожидаемый» предмет.

Локальный заголовок, заданный до `player.openInventory`
(например, в `GuiOpenEvent` или `onOpen` функционального блока),
применяется точно; если окно уже открыто, применяется через
`InventoryView#setWindowTitle` (когда метод есть в сборке Paper),
иначе — при следующем открытии.

Пример — бочка с жидкостью (пустые DESIGN-слоты в файле, уровень
показывается локально):

```java
@EventHandler
public void onBarrelOpen(GuiOpenEvent e) {
    if (!"barrel".equals(e.getGui().name())) {
        return;
    }
    StorageKey key = e.getStorageKey();
    if (key.type() != StorageType.BLOCK) {
        return;
    }
    Location block = StorageKey.blockLocation(key.owner()); // «world:x,y,z» → Location
    int level = getFluidLevel(block);                        // 0..100
    CustomGuiAPI.setLocalDesigns(e.getPlayer(), Map.of(
            10, createFluidItem(level),
            11, createFluidItem(level),
            12, createFluidItem(level)));
    CustomGuiAPI.setLocalTitle(e.getPlayer(), "§bБочка " + level + "%");
}
```

---

## 15. Функциональные блоки (печь, верстак, бочка, генератор)

Пакет `api/functional` — база для «умных» блоков, где GUI ведёт
собственную логику: локальный title/design, крафты, топливо,
анимации прогресса.

```java
// Печь с топливом и прогрессом в DESIGN-слоте 4
FunctionalBlock.builder("custom_furnace")
    .gui("furnace")                                    // GUI, который открывает блок
    .canOpen((player, block) -> player.hasPermission("furnace.use"))
    .onOpen((player, block, inv) -> {
        // окно ещё не показано — сюда удобно сетаить локальный title/design
        CustomGuiAPI.setLocalTitle(player,
                "§6Печь " + block.getBlockX() + "," + block.getBlockZ());
        CustomGuiAPI.setLocalDesign(player, 4, progressItem(0));
    })
    .onClick((player, block, slot, type, event) -> {
        // до внешних слушателей GuiSlotClickEvent;
        // event.setInteractionCancelled(true) — ванильный клик отменится
    })
    .onItemChanged((player, block, slot, type, oldItem, newItem) -> {
        // ФАКТУЧЕСКИЕ предметы «было/стало» (null — пустой слот),
        // следующий тик после действия игрока — идеально для крафта:
        if (type == SlotType.CRAFT) {
            startOrUpdateCraft(block);            // заложили/убрали ингредиент
        } else if (type == SlotType.FUEL && oldItem != null && newItem == null) {
            refuelIfNeeded(block);                // топливо закончилось
        } else if (type == SlotType.RESULT
                && oldItem != null && newItem == null) {
            consumeFuel(block, 1);                // результат забрали → расход
        }
    })
    .onClose((player, block) -> saveProgress(block))
    .onTick((block, inv) -> {
        // каждые 5 тиков, per-зритель
        int progress = getProgress(block);
        for (Player viewer : CustomGuiAPI.getViewers(block)) {
            CustomGuiAPI.setLocalDesign(viewer, 4, progressItem(progress));
        }
    })
    .craftingRecipe(CraftingRecipe.simple(
            Map.of(13, new ItemStack(Material.IRON_ORE)),   // CRAFT слот
            Map.of(22, new ItemStack(Material.IRON_INGOT)), // RESULT слот
            600))                                            // 30 секунд
    .fuelConsumption(Map.of(14, 1))                          // FUEL слот → 1 шт.
    .register();                                              // ID блока → GUI + реестр
```

Методы `GuiService` для крафта/топлива/результата:

```java
boolean ok   = CustomGuiAPI.matchesCraft(inventory, recipe);   // CRAFT = ингредиентам
int consumed = CustomGuiAPI.consumeFuel(inventory, 1);         // расход FUEL-слотов
boolean gave = CustomGuiAPI.produceResult(inventory,
        Map.of(22, new ItemStack(Material.IRON_INGOT)));       // все-or-nothing в RESULT
```

Событие `GuiCraftEvent` — когда игрок кликает по RESULT-слоту, а в
GUI есть заполненные CRAFT-слоты (крафт потенциально валиден);
отмена запрещает забирание предмета.

**Реакция на действия игрока** — `onItemChanged` (см. выше) или
`GuiSlotChangedEvent` (раздел 7) с предметами «было/стало»: это
готовая точка, чтобы запускать/останавливать крафт от CRAFT-слотов,
реагировать на расход FUEL и на забирание RESULT. Логика запуска:
CRAFT изменился → проверить `matchesCraft` → крафт пошёл;
RESULT: предмет → пусто → результат забрали, расходовать топливо и
ингредиенты.
**Работа без открытого GUI** (варка продолжается, когда игрок
закрыл окно) — `onBlockTick` + `setWorking` + `blockData` +
`setBlockSlotItem`; полный пример котла — раздел 17.


При разрушении блока: открытые GUI закрываются, локальные оверрайды
зрителей очищаются, сохранённые предметы, данные блока и флаг
«работает» удаляются, вызывается `onBlockBroken(block)`,
per-блок анимации останавливаются.

---

## 16. Анимация дизайн-слотов

`DesignAnimation` крутит кадры (ItemStack) в DESIGN/RESULT слотах
через локальные оверрайды — файл GUI не затрагивается, другие игроки
и блоки видят обычный дизайн из файла.

```java
DesignAnimation flame = DesignAnimation.builder()
        .slots(4)                          // DESIGN слот(ы)
        .frames(List.of(f1, f2, f3, f4))   // кадры
        .intervalTicks(5)                  // тиков между кадрами
        .loop(true)                         // по кругу (по умолчанию)
        .build();

flame.start(player);                       // per-плеер: свой прогресс в своём GUI
flame.startForBlock(blockLocation);        // per-блок: общий прогресс для всех зрителей
flame.startForViewersOfBlock(blockLocation); // alias
flame.stopForPlayer(player);
flame.stopForBlock(blockLocation);
flame.stop();                              // всё
```

Авто-стоп: закрытие инвентаря сессии (per-блок — когда зрителей не
осталось), разрушение блока, конец кадров без `loop(true)`, явный
`stop()`.

---

## 17. Работа блока без открытого GUI (котёл/печь варят без зрителя)

Ключевая идея «умных» блоков (котёл, печь, генератор): **состояние и
работа живут на стороне блока, а не в GUI**. Игрок закрыл окно — варка
продолжается; открыл — видит актуальный результат. Для этого:

| Что | API |
|---|---|
| Числа/флаги блока (прогресс, рецепта, «готово») | `CustomGuiAPI.blockData(block)` / `blockData(blockId, block)` — `FunctionalBlockData`, персистентный KV, живёт без GUI и переживает рестарт |
| Включить/выключить серверную работу | `CustomGuiAPI.setWorking(block, true/false)`, `isWorking(block)` |
| Серверный тик работающего блока | `.onBlockTick((block, data) -> ...)` в builder — каждые 5 тиков **без зрителей** (unloaded чанки пропускаются) |
| Чтение/запись предметов слотов при закрытом GUI | `CustomGuiAPI.getBlockSlotItem(block, slot)`, `setBlockSlotItem(block, slot, item)` (зрители мгновенно перерисуются + `GuiSlotChangedEvent`), `consumeBlockSlotItem(block, slot, amount)` |
| Стрелка/кадры прогресса | `DesignAnimation.stageForProgress(cook, total, frames.size())` + `setLocalDesign` (ванильные и кастомные предметы) или `DesignAnimation` для авто-цикла |
| Зрители | `CustomGuiAPI.getViewers(block)` (уже был) |

Всё это работает поверх стандартного функционального блока (раздел 15):
`onOpen/onTick/onItemChanged` по-прежнему доступны.

### Пример: котёл (в духе FarmersDelight)

GUI `cooking_pot` (27 слотов): CRAFT 1,2,3,10,11,12 (ингредиенты),
RESULT 7 (блюдо), CONTAINER 22 (миска), RESULT 24 (готовое), DESIGN 5
(стрелка), DESIGN 20 (огонь).

```java
// onEnable:
FunctionalBlock.builder("farmersdelight:cooking_pot")
    .gui("cooking_pot")
    .onOpen((player, block, inv) -> paintNow(block, player))  // отрисовать текущее состояние
    .onTick((block, inv) -> {                                 // per-зритель, каждые 5 тиков
        for (Player v : CustomGuiAPI.getViewers(block)) {
            paintNow(block, v);
        }
    })
    .onItemChanged((player, block, slot, type, oldItem, newItem) -> {
        // заложили/убрали ингредиент — пере-оценить, пора ли варить
        if (type == SlotType.CRAFT) {
            CustomGuiAPI.setWorking(block, evaluateCookable(block));
        }
        if (type == SlotType.RESULT && oldItem != null && newItem == null
                && slot == 24) {
            // результат забрали: опыт + пере-оценка
            player.giveExp(Math.max(1, Math.round(
                    CustomGuiAPI.blockData(block).getDouble("xp", 0)));
            CustomGuiAPI.setWorking(block, evaluateCookable(block));
        }
    })
    .onBlockTick((block, data) -> {                            // серверная варка, БЕЗ зрителя
        if (!isHeated(block)) {                                // печь/костёр/магма снизу — своя логика
            data.setInt("cook", 0);
            return;
        }
        Object[] recipe = findRecipe(block);                   // своя таблица рецептов
        if (recipe == null) {
            data.setInt("cook", 0);
            return;
        }
        int cook = data.getInt("cook", 0) + 1;
        int total = (int) recipe[5];
        if (cook >= total) {
            data.setInt("cook", 0);
            data.setDouble("xp", (double) recipe[6]);
            consumeIngredients(block, recipe);                 // через consumeBlockSlotItem
            boolean toBowl = hasContainerRecipe(recipe);
            if (toBowl) {
                mergeIntoMeal(block, recipe);                  // setBlockSlotItem(7, dish)
            } else {
                CustomGuiAPI.setBlockSlotItem(block, 24, makeItem(recipe[3], (int) recipe[4]));
            }
        } else {
            data.setInt("cook", cook);
        }
    })
    .register();

// отрисовка: стрелка прогресса + огонь (ванильные и кастомные предметы — без разницы)
List<ItemStack> arrow = List.of(arrow1, arrow2, arrow3, arrow4); // CE-предметы
void paintNow(Location block, Player viewer) {
    FunctionalBlockData data = CustomGuiAPI.blockData(block);
    int cook = data.getInt("cook", 0);
    int total = data.getInt("total", 1);
    if (cook > 0 && total > 0) {
        int stage = DesignAnimation.stageForProgress(cook, total, arrow.size());
        CustomGuiAPI.setLocalDesign(viewer, 5, arrow.get(stage));
    } else {
        CustomGuiAPI.setLocalDesign(viewer, 5, null);
    }
    CustomGuiAPI.setLocalDesign(viewer, 20, isHeated(block) ? fireIcon : null);
}
```

Как это складывается:

- **Зритель открыл GUI** → обычные предметы слотов (CRAFT/CONTAINER/RESULT)
  подгружаются из персистентного хранилища блока; `onOpen`/`onTick`
  рисуют стрелку и огонь через `setLocalDesign` (локальный оверрайд —
  не трогает файл GUI, других зрителей и сохранённые предметы не касаются);
- **игрок закрыл GUI** → `onBlockTick` продолжает тикать: прогресс
  копится в `blockData`, результат появляется через
  `setBlockSlotItem` и сохраняется;
- **результат готов, зритель смотрит** → `setBlockSlotItem`
  мгновенно перерисовывает его инвентарь и вызывает
  `GuiSlotChangedEvent` (old=null → new=result) — `onItemChanged`/
  слушатель узнают, что «результат появился»;
- **перезагрузка сервера** → данные и флаг «работает»
  восстанавливаются из `data/functional/<blockId>.yml` — варка
  продолжается с того же места;
- **блок разбили** → предметы выпадают, данные и работа удаляются,
  `onBlockBroken` вызывается (остановить анимации и прочее).

Примечания:

- `setWorking(block, ...)` с `Location` находит ID блока через
  CraftEngine; если ID известен явно — `setWorking(blockId, block, ...)`
  (быстрее, без CE-обращения).
- `onBlockTick` вызывается только для загруженных чанков;
  `isHeated`-подобные проверки делайте в своём коде (Lightable/CE-state).
- Кадры стрелки — любые `ItemStack` (ванильные `Material` и кастомные
  CraftEngine/ItemsAdder предметы), `setLocalDesign` не различает.
- Если анимация «время-зависимая» (пламя, пузыри), а не прогресс —
  используйте `DesignAnimation` (раздел 16) в `onOpen`:
  `startForBlock(block)` перерисует всех зрителей, `stopForBlock` —
  в `onClose`.
