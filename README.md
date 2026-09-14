# CustomGuiReworked

Skeleton-based custom GUI framework for **Paper 26.2** (Java 25): visual
in-game editor, pluggable high-performance storage, custom-block
integration (ItemsAdder + CraftEngine) and a library-ready public API.

---

## Features

- **GUI editor in-game** (`/gui create <name>`) — size, slot-type
  skeleton, item design, title, storage type, custom-block bindings,
  live preview. All actions save instantly, no window flicker.
- **Slot type skeleton** — every slot is typed:
  `DESIGN` (decorative, immutable), `CONTAINER`, `CRAFT`, `RESULT`
  (take-only), `FUEL`.
- **5 storage types**: block, personal (per-player), global, team
  (Bukkit team), temporary (items returned on close).
- **Optimized storage engine**:
  - in-memory cache — reopening a GUI never re-reads the disk;
  - asynchronous, coalesced writes — a burst of clicks produces one
    disk write, and only the *touched* slots are re-serialized
    (one NBT call instead of 54 per click);
  - atomic writes (temp file + move) — no corruption on crash;
  - autosave + guaranteed save on close / quit / server stop.
- **Custom blocks**: right-click an ItemsAdder or CraftEngine block to
  open the bound GUI; breaking the block drops its stored items and
  closes open interfaces. Both integrations are optional and load
  their classes only when the plugin is present.
- **NBT handling on the new standard** (tr7zw NBTAPI / CraftEngine
  ecosystem). NBTAPI is now an *optional* soft-depend: without it the
  plugin falls back to a pure-Bukkit item codec, and old data keeps
  working thanks to per-payload codec tags.
- **Public API**: use CustomGuiReworked as a library from any other
  plugin (Bukkit Services + fluent builder + events).

## Requirements

| Component | Version |
|---|---|
| Paper | **26.2+** (Java 25) |
| NBTAPI | optional, recommended (2.16+) |
| ItemsAdder | optional (4.x) |
| CraftEngine | optional (26.x) |
| Skript | optional (2.14+; syntax, conditions, effects, events) |
| Denizen | optional (1.3.x; script events, `<cgui.*>` tags) |

## Installation

1. Drop `CustomGuiReworked-x.y.z.jar` into `plugins/`.
2. (Recommended) install **NBTAPI** — full NBT fidelity for stored items.
3. Start the server. Configuration is auto-created:
   - `plugins/CustomGuiReworked/config.yml` — language, autosave, options;
   - `plugins/CustomGuiReworked/lang/{en,ru}.yml` — translations;
   - `plugins/CustomGuiReworked/tables/` — GUI definitions (editor);
   - `plugins/CustomGuiReworked/custom/` — GUI definitions registered by other plugins (API);
   - `plugins/CustomGuiReworked/data/` — storage (players, teams, globals);
   - `<world>/CustomGuiReworked/blocks/` — block storage (region files).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/gui` | **Open the management menu** | `cgui.command` |
| `/gui create <name>` | Create + open editor | `cgui.create` |
| `/gui edit <name>` | Open editor | `cgui.edit` |
| `/gui open <name>` | Open the GUI | `cgui.open` |
| `/gui delete <name>` | Delete GUI file (storage kept) | `cgui.delete` |
| `/gui list` | List GUIs | — |
| `/gui command add <slot> <gui> [delay] <command...>` | Bind command to slot | `cgui.command` |
| `/gui command get <slot> <gui> [index]` | Show bound commands | `cgui.command` |
| `/gui command delete <slot> <gui> <index>` | Unbind command | `cgui.command` |
| `/gui reload` | Reload config, language, GUIs | `cgui.reload` |

Slot commands support placeholders: `%player%`, `%slot%`.

## Editor

`/gui create <name>` opens the editor:

- **Slot count** — 9/18/27/36/45/54 (existing slots survive a resize);
- **Slot types (skeleton)** — click cycles the type of a slot
  (`Design → Container → Craft → Result → Fuel`), shift-click forces
  `Design`;
- **Design** — place items into design slots; right-click an empty
  hand on a slot clears it; skeleton slots are locked;
- **Title** — chat prompt (`/cancel` aborts);
- **Data storage** — choose the storage type;
- **Preview** — opens the GUI as players will see it;
- **Custom blocks** — bind/unbind custom block IDs
  (ItemsAdder: `custom_block`, CraftEngine: `craftengine:custom_block`);
- **Delete** — removes the GUI (permission `cgui.delete`).

## Storage

| Type | Scope | Notes |
|---|---|---|
| `block` | one block | data lives in `<world>/CustomGuiReworked/blocks/`; breaking the block drops items |
| `personal` | per player | `data/players/<name>_<table>` |
| `global` | server-wide | `data/globals/<table>` |
| `team` | per Bukkit team | `data/teams/<team>_<table>` |
| `temporary` | session | nothing persisted; items returned on close |

Data format: JSON array of **tagged** item payloads
(`n1:<NBTAPI-json>` or `b1:<bukkit-json>`). Payloads written by either
codec are readable by both — the codec is pinned per payload, so data
survives installing/removing NBTAPI.

**Upgrading from 1.x**: old storage files and `tables/*.yml` are
read in place and migrated on first access (no manual steps, paths
unchanged). Legacy items require NBTAPI for lossless conversion.

## Configuration

```yaml
language: en            # en / ru

storage:
  autosave-ticks: 600   # periodic flush of dirty data (0 = off)
  max-cached-views: 10000

commands:
  execute-as-op: false  # run bound commands as OP (legacy behavior; keep false)
```

## Developer API

### Using the plugin as a library (no compile dependency)

```java
// Bukkit Services — no import of plugin classes required at runtime
dev.moonaticks.customGuiReworked.api.GuiService service =
        Bukkit.getServicesManager()
                .load(dev.moonaticks.customGuiReworked.api.GuiService.class)
                .stream().findFirst().orElse(null);

if (service != null) {
    service.openGui(player, "shop");
    List<ItemStack> items = service.readStorage(StorageType.PERSONAL, player.getName(), "shop");
}
```

> To resolve `GuiService` by name you only need the interface on your
> compile classpath: `compileOnly files('libs/CustomGuiReworked-2.1.0.jar')`.

### With the full dependency

```groovy
dependencies {
    compileOnly files('libs/CustomGuiReworked-2.1.0.jar')
    // или compileOnly 'dev.moonaticks:customguireworked:2.1.0', если опубликован
}
```

```java
// Fluent GUI creation
Gui gui = GuiBuilder.named("shop")
        .title("§6Shop")
        .size(27)
        .slots(List.of(10, 11, 12), SlotType.CONTAINER)
        .slot(20, SlotType.RESULT)
        .design(0, new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
        .storage(StorageType.PERSONAL)
        .command(10, "give %player% diamond 1", 0)
        .blockId("my_block")
        .build();
CustomGuiAPI.saveGui(gui);
CustomGuiAPI.openGui(player, "shop");

// Block bindings
CustomGuiAPI.registerBlockGui("craftengine:my_block", "shop");

// Direct storage access
CustomGuiAPI.writeStorage(StorageType.GLOBAL, "", "counters",
        List.of(new ItemStack(Material.BARREL)));
```

### Events

| Event | Purpose |
|---|---|
| `GuiOpenEvent` | fired before opening; **cancellable** |
| `GuiCloseEvent` | fired after close (save already scheduled) |
| `GuiSlotClickEvent` | fired per slot click; cancelling blocks bound commands |

### Registering GUIs from another plugin

GUIs built at runtime by other plugins can be registered through the API.
Registered GUIs are **persisted** (written to `custom/*.yml`) and survive
server restarts — exactly like editor-created ones, but in a separate
folder so `/gui delete` in the editor and API ownership stay unambiguous.

```java
// once, e.g. in onEnable (CustomGuiReworked must be in softdepend)
Gui gui = GuiBuilder.named("auction")
        .title("§6Auction House")
        .size(54)
        .slots(List.of(10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34, 35, 46, 47, 48), SlotType.CONTAINER)
        .storage(StorageType.GLOBAL)
        .design(4, new ItemStack(Material.BEACON))
        .build();

// persist=true — saved to custom/auction.yml (default)
CustomGuiAPI.registerGui(gui);
// persist=false — lives in memory only (fresh state after every restart)
CustomGuiAPI.registerGui(gui, false);

// open / use exactly like any other GUI
CustomGuiAPI.openGui(player, "auction");
List<ItemStack> saved = CustomGuiAPI.readStorage(StorageType.GLOBAL, "", "auction");
```

Useful additions:

- `registerGui(gui)` **replaces** an existing GUI with the same name
  (the old file is removed, the new one is written) — safe to call on
  every `onEnable` to keep the definition in sync with your plugin version;
- `unregisterGui(name, deleteFile)` — remove it again;
- `sourceOf(name)` — `"table"` / `"custom"` / `"runtime"` / `"none"`;
- `getOpenGui(player)` — the GUI a player has open right now, or `null`;
- `openGui(player, name, StorageType.TEMPORARY)` — open a GUI with a
  temporary storage override (items returned on close, nothing saved).

---

## Skript support

With **Skript** (2.14+) installed, the following syntax becomes available
(toggle: `integration.skript` in `config.yml`):

**Events** (values: `event-player`, `event-string` = GUI name,
`event-number` = slot for click):

```skript
on cgui open:
    broadcast "A player opened the %event-string% GUI"

on cgui click:
    if %event-player% has permission "shop.special":
        send "You clicked slot %event-number% in %event-string%"
```

**Conditions:**

```skript
if %player% has a cgui open:
    # ...
if cgui "shop" exists:
    # ...
if %player%'s cgui is "shop":
    # ...
```

**Expressions:**

| Syntax | Returns |
|---|---|
| `all cguis` / `all cgui names` | list of all GUI names |
| `cgui of %player%` | name of the GUI the player has open |
| `cgui size of %string%` | slot count |
| `cgui title of %string%` | title |
| `cgui storage of %string%` | storage id |
| `cgui item in slot %number% of %string% for %player%` | item from storage |
| `all cgui items of %string% for %player%` | all items from storage |

**Effects:**

```skript
open cgui "shop" to player
open cgui "shop" to player with storage temporary
close cgui of player
```

## Denizen support

With **Denizen** (1.3.x) installed, scripts get (toggle:
`integration.denizen` in `config.yml`):

**Events:**

```dsc
on cgui open:
    - narrate "<context.player> opened <context.gui>"

on cgui click:
    - narrate "slot <context.slot>"
```

**Tags:**

| Tag | Returns |
|---|---|
| `<cgui.guis>` | list of all GUI names |
| `<cgui.exists[<name>]>` | boolean |
| `<cgui.size[<name>]>` | slot count |
| `<cgui.title[<name>]>` | title |
| `<cgui.storage[<name>]>` | storage id |
| `<cgui.open_of[<player>]>` | GUI open by the player (`none` if none) |

Denizen scripts can also just run the Bukkit command:
`- execute <context.player> 'gui open shop'`.

---

## Building

- **JDK 25**, Gradle 9 (wrapper included).
- `./gradlew build` → `build/libs/CustomGuiReworked-2.1.0.jar`
- `./gradlew runServer` — launches a Paper 26.2 test server.

## Changelog (2.1.0)

- **Management menu** — bare `/gui` opens an in-game manager: paginated
  list, live chat search, create/reload, per-GUI options screen
  (preview / edit / reload / details / delete with confirm).
- **API-registered GUIs** — `registerGui(gui[, persist])` /
  `unregisterGui(...)` / `sourceOf(...)` / `getGuis()`; plugin-created
  GUIs are persisted to `custom/*.yml` and survive restarts;
  `openGui(player, name, StorageType)` — storage-type override on open;
  `getOpenGui(player)` — GUI open right now.
- **Skript support** (optional): `on cgui open/close/click` events,
  conditions (`player has a cgui open`, `cgui x exists`, …), expressions
  (`all cguis`, `cgui of player`, sizes/titles/storage, items from
  storage), effects (`open cgui x to player [with storage y]`,
  `close cgui of player`).
- **Denizen support** (optional): `on cgui open/close/click` script
  events and `<cgui.*>` tags (`guis`, `exists`, `size`, `title`,
  `storage`, `open_of`).
- `config.yml`: `integration.skript` / `integration.denizen` toggles;
  `plugin.yml`: Skript and Denizen added to softdepend.

## Changelog (2.0.0)

- **Paper 26.2 / Java 25**; fixed broken CI; permission prefixes aligned
  (`cgui.*`).
- **Storage rewrite**: in-memory cache, async coalesced saves,
  per-slot serialization, atomic writes, region cache for block storage.
- **Item codec rewrite**: NBTAPI demoted to soft-depend, tagged
  payloads (`n1:`/`b1:`), Bukkit fallback codec, automatic 1.x data
  migration, all `fixJsonNumbers` hacks removed.
- **API v2**: `GuiService` via Bukkit Services, `GuiBuilder`,
  `GuiOpenEvent`/`GuiCloseEvent`/`GuiSlotClickEvent`, `Gui` model
  moved to `dev.moonaticks.customGuiReworked.api`.
- **Editor rewrite**: six screens, in-place updates (no flicker),
  AIR-safe skeleton editing, `/cancel` for prompts, delete/preview.
- **Block integration**: single dispatcher, ItemsAdder + CraftEngine
  hooks loaded only when the plugin is present (no more hard class refs
  crashing the plugin).
- Removed: `SlotTypeAPI` heuristics, `MessageFormatter`,
  setOp command exploit, `reverseLookup`/`lastGui` maps.
  `tables/*.yml` format extended (old files load and resave automatically).
