# Skript и Denizen

Весь инструментарий работает **скриптами — без Java под каждый
интерфейс**. Обе интеграции: softdepend + тумблер `integration.*` в
`config.yml`; если скриптовый плагин включается позже CustomGuiReworked —
интеграция активируется автоматически.

Полный справочник с объяснениями: [API.md §10](https://github.com/amper24/CustomGuiReworked/blob/main/API.md#10-skript-и-denizen).

---

## Skript

### События

```skript
on cgui open:            # event-player, event-string (имя GUI)
    event-string is "shop"
    send "Добро пожаловать!"

on cgui close:
    # ...

on cgui click:           # event-player, event-string, event-number (слот)
    event-string is "shop"
    event-number is 22
    cancel event
    execute console command "give %event-player's name% diamond 1"

on cgui drag:            # event-player, event-string; cancel — запрещает весь drag
    cancel event

# Ключевое для «умных» блоков — предмет в слоте изменился
# (положили/забрали/перенесли; следующий тик, предметы фактические):
on cgui slot changed:
    # контекст: event-player, event-string (GUI), event-number (слот),
    #           event-location (блок, если блок-GUI),
    #           cgui old item of event / cgui new item of event
    event-string is "cooking_pot"
    if event-number is 1, 2, 3, 10, 11, 12:
        set cgui working of event-location to true
```

### Действия

```skript
open the cgui "shop" to player
close the cgui of player

# Локальные оверрайды (видит только этот игрок):
set cgui local design of player at slot 5 to arrow stage item
set cgui local title of player to "&bКотёл 42%"
clear cgui local designs of player

# «Работа» блока (варка без открытого GUI):
set cgui working of location to true

# Данные блока (ID блока — через CraftEngine; с явным ID — второй вариант):
set cgui block data of location key "cook" to "42"
set cgui block data of location id "farmersdelight:cooking_pot" key "cook" to "42"

# Слоты блока (работает при ЗАКРЫТОМ GUI; зрители перерисуются):
set cgui block item at slot 24 of location to cooked stew
```

### Условия / выражения

```skript
cgui "shop" exists
the cgui of player                    # GUI, открытый игроком
all cgui names
the cgui size of "shop"
the cgui title of "shop"
the cgui storage of "shop"
the cgui item at slot 3 from "shop" for player
all the cgui items from "shop" for player

location is cgui working              # работает ли блок

the cgui block of player              # локация блока, GUI которого открыт
all cgui viewers of location          # зрители блока
cgui block item at slot 24 of location
cgui block data of location key "cook"
cgui progress stage of 51 out of 200 in 4 frames    # индекс кадра стрелки (0..3)

cgui old item of event                # «было» в on cgui slot changed
cgui new item of event
```

---

## Denizen

### События

```denizen
on cgui click:
    - if <context.gui> == shop && <context.slot> == 22:
        - determine cancelled
        - execute as_server "give <player.name> diamond 1"

on cgui drag:
    - announce "drag over <context.slots>"

# Ключевое для «умных» блоков:
on cgui slot changed:
    - if <context.gui> == cooking_pot && <context.slot> in 1, 2, 3, 10, 11, 12:
        - adjust <context.block> cgui_working:true
```

Контексты: `context.player`, `context.gui`, `context.slot` (click/slot
changed), `context.slots` (drag), `context.slot_type`,
`context.block`, `context.old_item`, `context.new_item`.

### Механизмы (изменения — через `adjust`)

В этой линии Denizen (1.3.x) нет «mechanics» — изменения значений
делаются **механизмами** объектов:

```denizen
# Локальные оверрайды (пер-зритель):
- adjust <player> cgui_local_design:[5|arrow item]
- adjust <player> cgui_local_design:[<context.block>|10|iron_ingot]   # пер-блок
- adjust <player> cgui_local_title:"&bКотёл 42%"
- adjust <player> cgui_clear_local_title
- adjust <player> cgui_clear_local_design

# Работа блока и его данные/слоты:
- adjust <context.block> cgui_working:true
- adjust <loc> cgui_working:false
- adjust <loc> cgui_block_data:[cook|42]
- adjust <loc> cgui_block_data:[farmersdelight:cooking_pot|cook|42]
- adjust <loc> cgui_block_item:[24|cooked stew]
```

### Теги (чтение)

```denizen
<cgui.guis>                         # список имён GUI
<cgui.exists[shop]>
<cgui.size[shop]>
<cgui.title[shop]>
<cgui.storage[shop]>
<cgui.open_of[<player>]>            # GUI, открытый игроком ("none" если нет)
<cgui.block_of[<player>]>           # локация блока, GUI которого открыт
<cgui.viewers[<loc>]>               # игроки, у которых открыт GUI блока
<cgui.working[<loc>]>               # работает ли блок
<cgui.block_item[[<loc>]|24]>       # предмет слота блока (даже при закрытом GUI)
<cgui.block_data[[<loc>]|cook]>     # данные блока
<cgui.block_data[[<loc>]|farmersdelight:cooking_pot|cook]>  # с явным ID
```

> «Мульти-теги» принимают **список** (разделитель `|`). Локацию
> рекомендуется оборачивать в квадратные скобки — иначе запятые
> координат столкнутся с разделителем: `<cgui.block_item[[<loc>]|24]>`.
