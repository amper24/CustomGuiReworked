# CustomGuiReworked — гайд по API для разработчиков плагинов

Фреймворк кастомных GUI для **Paper 26.2 (Java 25)**. Этот документ —
полное руководство по использованию CustomGuiReworked как библиотеки
из вашего плагина: подключение, создание/открытие интерфейсов,
программное хранилище, события, кастомные блоки и скриптовые интеграции.

> Требование на сервере: установленный плагин `CustomGuiReworked`
> той же мажорной версии (2.x). Ваш плагин подключает API только как
> `compileOnly` — в свой jar классы фреймворка не вшиваются.

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
    compileOnly("com.github.amper24:CustomGuiReworked:2.2.0")
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
    compileOnly 'com.github.amper24:CustomGuiReworked:2.2.0'
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
        <version>2.2.0</version>
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

### plugin.yml вашего плагина

```yaml
name: MyAddon
version: '1.0.0'
main: com.example.myaddon.MyAddon
api-version: '26.2'
softdepend: [CustomGuiReworked]
```

`softdepend` гарантирует, что ваш плагин включается **после**
CustomGuiReworked и сервис API уже зарегистрирован.

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

**Важно:** все вызовы API выполняются в основном потоке сервера
(как и любая работа с инвентарями Bukkit). Дисковый ввод-вывод внутри
фреймворка уже асинхронный — своими потоками его оборачивать не нужно.

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

События вызываются и по DESIGN-кнопкам. Через `getHandle()` доступно
исходное `InventoryClickEvent` для сложных сценариев.

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

Drag по DESIGN/RESULT слотам фреймворк отменяет ещё до события —
подписчики получают только допустимые операции.

Все четыре события также продублированы для Skript и Denizen
(раздел 10).

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
| `/gui` | меню управления (список, поиск, пагинация) |
| `/gui create <name>` | создать меню и открыть редактор |
| `/gui edit <name>` | редактировать существующее |
| `/gui open <name>` | открыть меню |
| `/gui delete <name>` | удалить меню (данные хранилища остаются) |
| `/gui list` | список меню |
| `/gui command ...` | привязка команд к слотам |
| `/gui reload` | перечитать конфиг, язык и все меню |

Редактор — полностью внутриигровой: размер, скелет (клик — смена типа
слота, shift-клик — DESIGN), дизайн (перетаскивание предметов),
заголовок и привязки блоков вводятся в чат, ввод отменяется командой
`/cancel`, таймаут ввода — 5 минут.

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
`all the cgui items from "shop" for player`.

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
`context.slots` (drag). Теги: `<cgui.guis>`, `<cgui.exists[shop]>`,
`<cgui.size[shop]>`, `<cgui.title[shop]>`, `<cgui.storage[shop]>`,
`<cgui.open_of[<player>]>`.

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
        // окno ещё не показано — сюда удобно сетаить локальный title/design
        CustomGuiAPI.setLocalTitle(player,
                "§6Печь " + block.getBlockX() + "," + block.getBlockZ());
        CustomGuiAPI.setLocalDesign(player, 4, progressItem(0));
    })
    .onClick((player, block, slot, type, event) -> {
        // до внешних слушателей GuiSlotClickEvent;
        // event.setInteractionCancelled(true) — ванильный клик отменится
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

При разрушении блока: открытые GUI закрываются, локальные оверрайды
зрителей очищаются, сохранённые предметы выпадают, вызывается
`onBlockBroken(block)`, per-блок анимации останавливаются.

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
