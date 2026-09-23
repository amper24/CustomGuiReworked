package dev.moonaticks.customGuiReworked.listeners;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotCommand;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.event.GuiCraftEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiDragEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Взаимодействие игроков с открытыми GUI.
 *
 * <p>Гарантии:
 * <ul>
 *   <li><b>shift-click работает по-ванильному</b>:
 *     <ul>
 *       <li>из слотов CONTAINER/CRAFT/FUEL/RESULT верхнего инвентаря → в
 *           инвентарь игрока (отдаём нативной механике, вниз предметы уносятся
 *           корректно и не попадают в дизайн/result-слоты по определению);</li>
 *       <li>из инвентаря игрока → вверх делаем вручную: предметы доливаются
 *           в подходящие (similar) стаки, затем в пустые слоты — и только в
 *           CONTAINER/CRAFT/FUEL. DESIGN/RESULT слоты при shift-подъёме
 *           полностью игнорируются, как будто их «нет».</li>
 *       <li>из дизайн-слотов shift-клик заблокирован (сами слоты
 *           неизменяемы, клик по ним обрабатывается как кнопка).</li>
 *     </ul>
 *   </li>
 *   <li>double-click <b>работает</b>: одинаковые предметы нормально
 *       собираются в один стак и в нижнем инвентаре, и в контейнерных/
 *       крафт/топливных слотах GUI. Дизайн от кражи защищён двумя
 *       механизмами: на декоративных предметах стоит PDC-маркер
 *       ({@code isSimilar} с обычными предметами = false) и
 *       {@code maxStackSize} равен количеству — в слот не влезет больше
 *       предметов. Как последний рубеж {@code rescueDesignItems}
 *       восстанавливает оформление на следующий тик;</li>
 *   <li>дизайн-слоты неизменяемы, но клики по ним (кнопки) запускают
 *       привязанные команды и генерируют {@link GuiSlotClickEvent};</li>
 *   <li>result-слоты — только «на выход»: любые попытки положить туда
 *       предмет (курсор, цифровые клавиши, свап с офхендом, драг,
 *       ручная кладка из shift-click) заблокированы;</li>
 *   <li>изменения читаются со снапшота <b>следующего тика</b> (внутри
     *     InventoryClickEvent инвентарь ещё содержит старые предметы),
     *     дифом по baseline — в хранилище пишутся только изменившиеся
     *     слоты, не затирая параллельные сессии других игроков;</li>
 *   <li>по каждому изменившемуся слоту (CONTAINER/CRAFT/FUEL/RESULT) на
     *     этом же следующем тике вызывается {@link GuiSlotChangedEvent}
     *     с предметами «было/стало» — готовая точка для запуска крафта,
     *     топлива и анимаций от действий игрока;</li>
 *   <li>команды выполняются через {@link Bukkit#dispatchCommand} без
 *       setOp-эксплойта;</li>
 *   <li>каждый клик по верхнему инвентарю и каждый драг генерируют
 *       события API — точки расширения для других плагинов.</li>
 * </ul>
 */
public class GuiInteractionListener implements Listener {

    private final CustomGuiReworked plugin;

    public GuiInteractionListener(CustomGuiReworked plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Gui gui = holder.gui();
        Inventory clicked = event.getClickedInventory();
        boolean inTop = clicked != null && clicked == top;
        int slot = event.getSlot();
        ClickType click = event.getClick();

        // Shift-click обрабатываем отдельно:
        //   • вверх (из инвентаря игрока в GUI) — ручной перенос, чтобы
        //     предметы попадали ТОЛЬКО в CONTAINER/CRAFT/FUEL слоты
        //     (ваниль без этого раскладывает по DESIGN/RESULT);
        //   • вниз (из CONTAINER/CRAFT/FUEL/RESULT в инвентарь игрока) —
        //     отдаём ваниле, она работает корректно (вниз предметы
        //     уносятся в инвентарь игрока, минуя дизайн/result по определению);
        //   • по DESIGN-слотам — заблокирован (isCancelled=true уже в ветке DESIGN).
        if (event.isShiftClick()) {
            handleShiftClick(player, gui, holder, event, inTop, slot);
            return;
        }

        if (!inTop) {
            // Клик по своему инвентарю. Разрешены все не-shift действия ванили
            // (взять/положить, цифровые клавиши, свап с офхендом, double-click).
            // Дизайн-предметы от кражи защищены двумя механизмами:
            //   1. PDC-маркер: isSimilar с обычными предметами = false,
            //      значит double-click не стягивает декор на курсор;
            //   2. maxStackSize == amount: в слот не влезет больше предметов.
            // Для double-click ваниль может перераспределить предметы по
            // CONTAINER/CRAFT/FUEL слотам — реконсилируем всё.
            if (click == ClickType.DOUBLE_CLICK) {
                try {
                    if (!SlotInteractionPolicy.canCollect(player, gui, top, event.getCursor())) {
                        event.setCancelled(true);
                        return;
                    }
                } catch (RuntimeException e) {
                    event.setCancelled(true); // ошибка фильтра не должна обходить запрет
                    plugin.getLogger().warning("Slot type take filter failed: " + e.getMessage());
                    return;
                }
                holder.addAllCandidates();
                plugin.opener().scheduleReconcile(holder);
            }
            return;
        }

        SlotType type = gui.slotType(slot);

        // Дизайн-слоты неизменяемы, но клик по кнопке работает.
        if (type.isDecorative()) {
            event.setCancelled(true);
            boolean runCommands = fireClickEvent(player, gui, top, slot, type, click, event, type.isRegistered(), holder);
            if (runCommands) {
                runCommands(player, gui, slot);
            }
            return;
        }

        // Double-click затрагивает и другие слоты, даже если клик был по
        // обычному контейнеру: не даём украсть предметы из запертых типов.
        if (click == ClickType.DOUBLE_CLICK) {
            try {
                if (!SlotInteractionPolicy.canCollect(player, gui, top, event.getCursor())) {
                    event.setCancelled(true);
                    return;
                }
            } catch (RuntimeException e) {
                event.setCancelled(true);
                plugin.getLogger().warning("Slot type take filter failed: " + e.getMessage());
                return;
            }
        }

        // Result: кладка предмета запрещена всеми способами.
        boolean placementBlocked = false;
        if (type == SlotType.RESULT) {
            placementBlocked = isPlacementIntoResult(player, event);
            if (placementBlocked) {
                event.setCancelled(true);
            }
            // Крафт-событие: игрок берёт результат (или кликает по нему),
            // а в CRAFT-слотах есть что «крафтить» — точка для валидации
            // рецепта и расхода CRAFT/FUEL.
            fireCraftEvent(player, gui, top, slot, click, event);
        } else if (!type.isBuiltin()) {
            try {
                placementBlocked = !SlotInteractionPolicy.allowed(player, gui, top, slot, event);
            } catch (RuntimeException e) {
                placementBlocked = true;
                plugin.getLogger().warning("Slot type interaction filter failed: " + e.getMessage());
            }
            if (placementBlocked) {
                event.setCancelled(true);
            }
        }

        boolean runCommands = fireClickEvent(player, gui, top, slot, type, click, event, !placementBlocked, holder);

        // Кандидаты на запись: одиночный клик затрагивает один слот,
        // double-click может перераспределить предметы по всему GUI.
        if (click == ClickType.DOUBLE_CLICK) {
            holder.addAllCandidates();
        } else {
            holder.addCandidate(slot);
        }
        plugin.opener().scheduleReconcile(holder);

        // Команды result-слота при заблокированной кладке не запускаем
        // (это «ошибочный» клик, а не настоящее нажатие кнопки).
        if (runCommands && !placementBlocked) {
            runCommands(player, gui, slot);
        }
    }

    /**
     * Обработка shift-click:
     * <ul>
     *   <li>клик по DESIGN-слоту — отменяем, запускаем команды (кнопка);</li>
     *   <li>клик из верхнего инвентаря (CONTAINER/CRAFT/FUEL/RESULT) —
     *       отдаём ваниле, но только после проверки что кладка в результат
     *       не произошла (для RESULT-слота shift = забрать, это разрешено);</li>
     *   <li>клик из нижнего инвентаря — вручную переносим предмет в подходящие
     *       CONTAINER/CRAFT/FUEL слоты верхнего инвентаря (сначала похожие
     *       стаки, потом пустые), минуя DESIGN/RESULT.</li>
     * </ul>
     */
    private void handleShiftClick(Player player, Gui gui, GuiHolder holder,
                                  InventoryClickEvent event, boolean inTop, int slot) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (inTop) {
            SlotType type = gui.slotType(slot);
            if (type.isDecorative()) {
                // Дизайн-слот: shift как обычный клик-кнопка, предмет не двигаем.
                event.setCancelled(true);
                boolean runCommands = fireClickEvent(player, gui, top, slot, type,
                        event.getClick(), event, type.isRegistered(), holder);
                if (runCommands) {
                    runCommands(player, gui, slot);
                }
                return;
            }
            // Для кастомного типа правила изъятия работают и при shift-выносе.
            if (!type.isBuiltin()) {
                try {
                    ItemStack current = event.getCurrentItem();
                    if (current != null && current.getType() != Material.AIR
                            && !type.canTake(gui, top, slot, player, current)) {
                        event.setCancelled(true);
                        fireClickEvent(player, gui, top, slot, type, event.getClick(), event, false, holder);
                        return;
                    }
                } catch (RuntimeException e) {
                    event.setCancelled(true);
                    plugin.getLogger().warning("Slot type take filter failed: " + e.getMessage());
                    return;
                }
            }
            // Из CONTAINER/CRAFT/FUEL/RESULT в свой инвентарь: отдаём ваниле
            // (предметы уходят в player-inventory, минуя DESIGN/RESULT).
            // Результат-слот при shift-клике может запускать привязанные
            // команды (например выдача награды) — это ванильное поведение,
            // раньше команды тоже срабатывали.
            if (type == SlotType.RESULT) {
                fireCraftEvent(player, gui, top, slot, event.getClick(), event);
            }
            boolean runCommands = fireClickEvent(player, gui, top, slot, type,
                    event.getClick(), event, true, holder);
            holder.addAllCandidates();
            plugin.opener().scheduleReconcile(holder);
            if (runCommands) {
                runCommands(player, gui, slot);
            }
            return;
        }

        // Shift-клик из своего инвентаря вверх: ваниль раскидывает предмет
        // по всем слотам без разбора (в т.ч. DESIGN/RESULT), поэтому
        // делаем перенос вручную ТОЛЬКО в CONTAINER/CRAFT/FUEL.
        ItemStack source = event.getCurrentItem();
        if (source == null || source.getType() == Material.AIR || source.getAmount() <= 0) {
            return;
        }
        int moved;
        try {
            moved = moveToTop(player, top, gui, source);
        } catch (RuntimeException e) {
            event.setCancelled(true);
            plugin.getLogger().warning("Slot type insert filter failed: " + e.getMessage());
            return;
        }
        if (moved <= 0) {
            // Некуда класть — полностью отменяем, ваниль ничего не делает.
            event.setCancelled(true);
            return;
        }
        // Обновляем слот-источник в инвентаре игрока ЯВНО: в ивенте
        // getCurrentItem() может возвращать копию, простой .setAmount()
        // на ней не всегда применяется к реальному инвентарю.
        int remaining = source.getAmount() - moved;
        ItemStack left;
        if (remaining <= 0) {
            left = null;
        } else {
            left = source.clone();
            left.setAmount(remaining);
        }
        clicked.setItem(slot, left);
        event.setCancelled(true);
        holder.addAllCandidates();
        plugin.opener().scheduleReconcile(holder);
    }

    /**
     * Переносит как можно больше предметов из {@code source} в подходящие
     * персистентные слоты ({@code CONTAINER/CRAFT/FUEL}) верхнего
     * инвентаря: сначала в существующие similar-стаки, потом в пустые.
     * DESIGN и RESULT слоты полностью пропускаются.
     *
     * @return количество фактически перенесённых единиц
     */
    int moveToTop(Player player, Inventory top, Gui gui, ItemStack source) {
        int maxStack = Math.max(1, source.getMaxStackSize());
        int remaining = source.getAmount();
        if (remaining <= 0) {
            return 0;
        }
        // Проверяем ВСЕ фильтры до первого изменения инвентаря: исключение
        // чужого обработчика не должно оставить скопированный предмет сверху.
        boolean[] accepting = new boolean[gui.slots()];
        for (int i = 0; i < gui.slots(); i++) {
            SlotType type = gui.slotType(i);
            accepting[i] = type.isPersistable() && type.canInsert(gui, top, i, player, source);
        }

        // 1-й проход: доливаем в похожие стаки.
        for (int i = 0; i < gui.slots() && remaining > 0; i++) {
            if (!accepting[i]) {
                continue;
            }
            ItemStack target = top.getItem(i);
            if (target == null || target.getType() == Material.AIR) {
                continue;
            }
            if (!target.isSimilar(source)) {
                continue;
            }
            int targetMax = Math.min(maxStack, Math.max(1, target.getMaxStackSize()));
            int canAdd = targetMax - target.getAmount();
            if (canAdd <= 0) {
                continue;
            }
            int add = Math.min(canAdd, remaining);
            target.setAmount(target.getAmount() + add);
            remaining -= add;
        }

        // 2-й проход: кладём в пустые слоты.
        for (int i = 0; i < gui.slots() && remaining > 0; i++) {
            if (!accepting[i]) {
                continue;
            }
            ItemStack target = top.getItem(i);
            if (target != null && target.getType() != Material.AIR) {
                continue;
            }
            int place = Math.min(Math.min(maxStack, source.getMaxStackSize()), remaining);
            ItemStack placed = source.clone();
            placed.setAmount(place);
            top.setItem(i, placed);
            remaining -= place;
        }

        return source.getAmount() - remaining;
    }

    /**
     * Определяет, пытается ли данный клик ПОЛОЖИТЬ предмет в result-слот.
     * Возвращает {@code false} для кликов, которые только ЗАБИРАЮТ предмет
     * (DROP, двойной клик, shift-вынос) или не затрагивают слот.
     */
    private boolean isPlacementIntoResult(Player player, InventoryClickEvent event) {
        ClickType click = event.getClick();
        // Только-забирающие / не затрагивающие слот действия — не блокируем.
        if (event.isShiftClick()
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || click == ClickType.WINDOW_BORDER_LEFT
                || click == ClickType.WINDOW_BORDER_RIGHT
                || click == ClickType.UNKNOWN) {
            return false;
        }
        // Свап с хотбаром: кладка только если в хотбаре есть предмет.
        if (click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && hotbar < 9) {
                ItemStack hot = player.getInventory().getItem(hotbar);
                return hot != null && hot.getType() != Material.AIR;
            }
            return false;
        }
        // Свап с офхендом: кладка если в офхенде есть предмет.
        if (click == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            return offhand != null && offhand.getType() != Material.AIR;
        }
        // В творческом режиме MIDDLE (pick block) / CREATIVE могут
        // как дублировать предмет, так и ставить; безопаснее запретить
        // любую постановку не через пустой курсор.
        // LEFT/RIGHT/MIDDLE/CREATIVE: прямая кладка с курсора.
        ItemStack cursor = event.getCursor();
        return cursor != null && cursor.getType() != Material.AIR;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Gui gui = holder.gui();
        List<Integer> affected = new ArrayList<>();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                continue;
            }
            SlotType type = gui.slotType(rawSlot);
            if (type.isDecorative()) {
                event.setCancelled(true);
                return;
            }
            ItemStack incoming = event.getNewItems() == null ? null : event.getNewItems().get(rawSlot);
            if (incoming == null) {
                incoming = event.getOldCursor();
            }
            if (incoming != null && incoming.getType() != Material.AIR) {
                try {
                    if (!type.canInsert(gui, top, rawSlot, player, incoming)) {
                        event.setCancelled(true);
                        return;
                    }
                } catch (RuntimeException e) {
                    event.setCancelled(true);
                    plugin.getLogger().warning("Slot type insert filter failed: " + e.getMessage());
                    return;
                }
            }
            affected.add(rawSlot);
        }
        if (affected.isEmpty()) {
            return;
        }
        GuiDragEvent dragEvent = new GuiDragEvent(player, gui, top, affected, event);
        Bukkit.getPluginManager().callEvent(dragEvent);
        if (dragEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        for (int slot : affected) {
            holder.addCandidate(slot);
        }
        plugin.opener().scheduleReconcile(holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        plugin.opener().handleClose(player, holder);
        plugin.dispatcher().onInventoryClosed(player, holder.key());
        // onClose функционального обработчика — после сохранения/консолидации
        // хранилища и очистки локальных оверрайдов.
        plugin.dispatcher().onGuiClosed(player, holder);
    }

    // ================= события API =================

    /**
     * Вызывает {@link GuiSlotClickEvent}.
     *
     * <p>Перед внешними слушателями событие проходит через
     * функциональный обработчик блока ({@code BlockHookDispatcher#onGuiClick}) —
     * он может пометить {@code interactionCancelled} и ванильный клик
     * отменится.
     *
     * @param commandsEnabled разрешены ли привязанные команды при незапамятном
     *                        состоянии события (false — заблокированная кладка)
     * @return true, если команды нужно выполнить
     */
    private boolean fireClickEvent(Player player, Gui gui, Inventory top, int slot, SlotType type,
                                   ClickType click, InventoryClickEvent handle,
                                   boolean commandsEnabled, GuiHolder holder) {
        GuiSlotClickEvent guiEvent = new GuiSlotClickEvent(player, gui, top, slot, type, true, click, handle);
        // Функциональный обработчик блока (внутренний) — до внешних слушателей.
        plugin.dispatcher().onGuiClick(player, holder, slot, type, guiEvent);
        try {
            type.handleClick(guiEvent);
        } catch (RuntimeException e) {
            guiEvent.setInteractionCancelled(true);
            plugin.getLogger().warning("Slot type '" + type.id() + "' onClick failed: " + e.getMessage());
        }
        Bukkit.getPluginManager().callEvent(guiEvent);
        if (guiEvent.isInteractionCancelled()) {
            handle.setCancelled(true);
        }
        return commandsEnabled && !guiEvent.isCancelled();
    }

    /**
     * Вызывает {@link GuiCraftEvent} для клика по RESULT-слоту
     * (когда в GUI есть CRAFT-слоты; «крафт потенциально валиден»).
     * Отмена события отменяет исходный клик (предмет не забирается).
     */
    private void fireCraftEvent(Player player, Gui gui, Inventory top, int slot,
                                ClickType click, InventoryClickEvent handle) {
        boolean hasCraftItems = false;
        for (int i = 0; i < gui.slots(); i++) {
            if (gui.slotType(i) != SlotType.CRAFT) {
                continue;
            }
            ItemStack item = top.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                hasCraftItems = true;
                break;
            }
        }
        if (!hasCraftItems) {
            return; // «крафт» невозможен — событие не генерируем
        }
        GuiCraftEvent craftEvent = new GuiCraftEvent(player, gui, top, slot, click, hasCraftItems);
        Bukkit.getPluginManager().callEvent(craftEvent);
        if (craftEvent.isCancelled()) {
            handle.setCancelled(true);
        }
    }

    // ================= команды =================

    private void runCommands(Player player, Gui gui, int slot) {
        boolean asOp = plugin.getConfig().getBoolean("commands.execute-as-op", false);
        for (SlotCommand command : gui.commandsForSlot(slot)) {
            String resolved = command.command()
                    .replace("%player%", player.getName())
                    .replace("%slot%", String.valueOf(slot));
            if (resolved.startsWith("/")) {
                resolved = resolved.substring(1);
            }
            if (resolved.isBlank()) {
                continue;
            }
            final String commandLine = resolved;
            if (command.delay() <= 0) {
                dispatch(player, commandLine, asOp);
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        dispatch(player, commandLine, asOp);
                    }
                }.runTaskLater(plugin, command.delay());
            }
        }
    }

    private void dispatch(Player player, String command, boolean asOp) {
        if (!player.isOnline()) {
            return;
        }
        boolean wasOp = player.isOp();
        try {
            if (asOp && !wasOp) {
                player.setOp(true);
            }
            Bukkit.dispatchCommand(player, command);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute bound command '" + command
                    + "' for " + player.getName() + ": " + e.getMessage());
        } finally {
            if (asOp && !wasOp) {
                player.setOp(false);
            }
        }
    }
}
