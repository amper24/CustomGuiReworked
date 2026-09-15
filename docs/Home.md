# CustomGuiReworked — документация

**Skeleton-based GUI framework для Paper 26.2** (Java 25): визуальный
in-game редактор, оптимизированное хранилище, интеграция кастомных блоков
(ItemsAdder + CraftEngine), публичный API как библиотека и готовый
скриптовый инструментарий (Skript + Denizen).

Версия: **2.3.0** (см. `build.gradle` в репозитории).

## Навигация

### Страницы документации (эта папка)
- [API возможности](api.md) — что можно делать на API: таблица задач → методы
- [Умный блок — котёл](smart-block.md) — пошаговый пример «умного» блока (варит без открытого GUI)
- [Skript и Denizen](scripts.md) — полный справочник скриптового инструментария
- [Примеры](examples.md) — куда смотреть готовые примеры

### Файлы в репозитории (глубокая документация)
| Файл | Что внутри |
|---|---|
| [README.md](https://github.com/amper24/CustomGuiReworked/blob/main/README.md) | обзор, установка, команды, конфиг, структура |
| [API.md](https://github.com/amper24/CustomGuiReworked/blob/main/API.md) | полный справочник API (17 разделов) |
| [EXAMPLES.md](https://github.com/amper24/CustomGuiReworked/blob/main/EXAMPLES.md) | готовые примеры: от «первый GUI» до котла |
| [MECHANICS.md](https://github.com/amper24/CustomGuiReworked/blob/main/MECHANICS.md) | внутренние механики: скелет, хранилище, codecs, редактор |

## Быстрый старт

1. Установите jar в `plugins/` (Paper 26.2+, Java 25). Рекомендуем
   [NBTAPI](https://www.spigotmc.org/resources/nbtapi.19624/) — полная NBT-точность.
2. `/gui create mygui` — визуальный редактор: размер, скелет слотов
   (`DESIGN`/`CONTAINER`/`CRAFT`/`RESULT`/`FUEL`), дизайн, титул,
   хранилище, привязка кастомных блоков.
3. `/gui open mygui` — открыть.
4. Скриптить без Java: Skript (`on cgui ...`, `set cgui ...`) или
   Denizen (`on cgui ...`, `<cgui...>`, `adjust`) — см. [Skript и Denizen](scripts.md).
5. Писать плагин-интеграцию: `compileOnly 'com.github.amper24:CustomGuiReworked:2.3.0'`
   (JitPack) + `softdepend` — см. [API возможности](api.md) и
   [API.md](https://github.com/amper24/CustomGuiReworked/blob/main/API.md).

## Главное в 2.3.0

- **Локальные оверрайды (per-зритель)**: название и дизайн слотов можно
  подменять только для одного игрока — стрелки прогресса, огонь, уровни
  жидкости. Не трогает файл GUI, других игроков и другие блоки.
- **Функциональные блоки**: «умные» блоки (печь/верстак/котёл/генератор) —
  крафты, топливо, анимации, **работа без открытого GUI** (варка
  продолжается, когда игрок закрыл окно).
- **`GuiSlotChangedEvent`** — «предмет положен/забран/перенесён» с
  фактическими предметами «было/стало».
- **Пер-блок API**: `blockData` (персистентные данные), `working`
  (флаг работы), предметы слотов блока при закрытом GUI, зрители блока,
  блок игрока.
- **Скриптовый инструментарий**: всё выше через Skript-эффекты/выражения
  и Denizen-теги/`adjust` — без Java-кода под каждый интерфейс.

Подробности — [API возможности](api.md), [Умный блок — котёл](smart-block.md) и
[Skript и Denizen](scripts.md).
