package dev.moonaticks.customGuiReworked.api.animation;

import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.gui.GuiHolder;
import dev.moonaticks.customGuiReworked.gui.GuiOpener;
import dev.moonaticks.customGuiReworked.storage.BlockStorageBackend;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Анимация DESIGN-слотов через локальные (per-viewer) оверрайды:
 * кадры ({@link ItemStack}) по кругу или по разу, с заданным интервалом.
 *
 * <p>Файл GUI не затрагивается — анимация пишет только в открытые
 * инвентари зрителей ({@link GuiOpener#applyLocalDesign}), поэтому
 * другие игроки и другие блоки видят обычный дизайн из файла.
 *
 * <p>Два режима:
 * <ul>
 *   <li><b>per-плеер</b> — {@link #start(Player)}: анимация в GUI
 *       конкретного игрока (каждая сессия — свой прогресс);</li>
 *   <li><b>per-блок</b> — {@link #startForBlock(Location)}: один общий
 *       прогресс, применяемый ко всем зрителям блока каждый тик
 *       (новички подхватывают текущий кадр, вышедшие — просто не видят).</li>
 * </ul>
 *
 * <p>Автостоп:
 * <ul>
 *   <li>закрытие инвентаря сессии (per-плеер — свой, per-блок — когда
 *       зрителей не осталось);</li>
 *   <li>разрушение блока (для per-блок анимаций);</li>
 *   <li>конец кадров без {@code loop(true)};</li>
 *   <li>явный {@link #stop()}.</li>
 * </ul>
 *
 * <pre>{@code
 * DesignAnimation flame = DesignAnimation.builder()
 *         .slots(4)
 *         .frames(List.of(frame1, frame2, frame3, frame4))
 *         .intervalTicks(5)
 *         .loop(true)
 *         .build();
 * flame.startForBlock(blockLocation);
 * }</pre>
 */
public final class DesignAnimation {

    private static final String PLUGIN_NAME = "CustomGuiReworked";

    /** Живые анимации — для авто-стопов (close/break) из внутренних хуков. */
    private static final Set<DesignAnimation> ACTIVE = ConcurrentHashMap.newKeySet();

    private final List<Integer> slots;
    private final List<ItemStack> frames;
    private final int intervalTicks;
    private final boolean loop;
    /** Блок для per-блок режима; null — только per-плеер. */
    private final Location blockLocation;

    /** Per-плеер сессии: инвентарь → задача. */
    private final Map<Inventory, BukkitTask> sessionTasks = new HashMap<>();
    /** Per-плеер сессии: инвентарь → текущий кадр. */
    private final Map<Inventory, Integer> sessionFrames = new HashMap<>();
    /** Per-блок сессия (один общий прогресс на всех зрителей). */
    private BukkitTask blockTask;
    private int blockFrame;
    private Location runningBlock;

    private DesignAnimation(Builder builder) {
        if (builder.slots.isEmpty()) {
            throw new IllegalArgumentException("at least one slot is required");
        }
        if (builder.frames == null || builder.frames.isEmpty()) {
            throw new IllegalArgumentException("at least one frame is required");
        }
        this.slots = List.copyOf(builder.slots);
        this.frames = List.copyOf(builder.frames);
        this.intervalTicks = Math.max(1, builder.intervalTicks);
        this.loop = builder.loop;
        this.blockLocation = builder.blockLocation;
    }

    /** Создаёт builder анимации. */
    public static Builder builder() {
        return new Builder();
    }

    // ================= запуск =================

    /**
     * Запускает анимацию в GUI, который {@code player} открыл прямо сейчас
     * (per-плеер сессия, свой прогресс). Ничего не делает, если у игрока
     * не открыт GUI этого плагина.
     */
    public void start(Player player) {
        if (blockLocation != null) {
            throw new IllegalStateException("built with blockLocation — use startForBlock(Location)");
        }
        Inventory inventory = topInventoryOf(player);
        if (inventory == null) {
            return;
        }
        ACTIVE.add(this);
        startSession(inventory);
    }

    /**
     * Запускает пер-блок анимацию: общий прогресс, применяемый всем
     * зрителям {@code block} каждый тик. Если в builder'е уже задан
     * блок — он должен совпадать с параметром (иначе используется параметр).
     */
    public void startForBlock(Location block) {
        if (block == null || block.getWorld() == null) {
            throw new IllegalArgumentException("block with a loaded world is required");
        }
        if (blockTask != null) {
            return; // уже идёт
        }
        runningBlock = block.clone();
        blockFrame = 0;
        applyBlockFrame();
        blockTask = timer(this::tickBlock);
        ACTIVE.add(this);
    }

    /** То же, что {@link #startForBlock(Location)} (явный alias). */
    public void startForViewersOfBlock(Location block) {
        startForBlock(block);
    }

    // ================= остановка =================

    /** Останавливает все сессии анимации (per-плеер + per-блок). */
    public void stop() {
        for (BukkitTask task : new ArrayList<>(sessionTasks.values())) {
            task.cancel();
        }
        sessionTasks.clear();
        sessionFrames.clear();
        stopBlock();
    }

    /** Останавливает сессию в GUI, который {@code player} открыл прямо сейчас. */
    public void stopForPlayer(Player player) {
        Inventory inventory = topInventoryOf(player);
        if (inventory != null) {
            stopSession(inventory);
        }
    }

    /** Останавливает пер-блок сессию (если идёт для данного блока). */
    public void stopForBlock(Location block) {
        if (block == null || block.getWorld() == null || runningBlock == null) {
            return;
        }
        if (BlockStorageBackend.ownerKey(runningBlock).equals(BlockStorageBackend.ownerKey(block))) {
            stopBlock();
        }
    }

    /** Идёт ли анимация хотя бы в одной сессии. */
    public boolean isRunning() {
        return !sessionTasks.isEmpty() || blockTask != null;
    }

    // ================= внутренние хуки (авто-стоп) =================

    /**
     * Внутренний хук плагина: вызывается при закрытии GUI-инвентаря —
     * пер-плеер сессия этого инвентаря закрывается.
     */
    public static void onInventoryClosed(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (DesignAnimation animation : ACTIVE) {
            animation.stopSession(inventory);
        }
    }

    /**
     * Внутренний хук плагина: вызывается при разрушении блока —
     * per-блок анимации для этой локации останавливаются.
     */
    public static void onBlockBroken(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        String owner = BlockStorageBackend.ownerKey(location);
        for (DesignAnimation animation : ACTIVE) {
            if (animation.runningBlock != null
                    && BlockStorageBackend.ownerKey(animation.runningBlock).equals(owner)) {
                animation.stopBlock();
            }
        }
    }

    // ================= сессии =================

    private void startSession(Inventory inventory) {
        if (sessionTasks.containsKey(inventory)) {
            return;
        }
        sessionFrames.put(inventory, 0);
        applySessionFrame(inventory, 0);
        sessionTasks.put(inventory, timer(() -> tickSession(inventory)));
    }

    private void stopSession(Inventory inventory) {
        BukkitTask task = sessionTasks.remove(inventory);
        sessionFrames.remove(inventory);
        if (task != null) {
            task.cancel();
        }
        if (!isRunning()) {
            ACTIVE.remove(this);
        }
    }

    private void tickSession(Inventory inventory) {
        // Сессия жива, пока инвентарь открыт кем-то.
        if (inventory.getViewers().isEmpty()) {
            stopSession(inventory);
            return;
        }
        int next = sessionFrames.getOrDefault(inventory, 0) + 1;
        if (!loop && next >= frames.size()) {
            stopSession(inventory); // последний кадр уже применён
            return;
        }
        int index = loop ? next % frames.size() : next;
        sessionFrames.put(inventory, index);
        applySessionFrame(inventory, index);
    }

    private void applySessionFrame(Inventory inventory, int frameIndex) {
        if (!(inventory.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        applyFrame(holder, inventory, frames.get(frameIndex));
    }

    private void tickBlock() {
        if (runningBlock == null) {
            stopBlock();
            return;
        }
        List<Player> viewers = GuiOpener.getViewers(runningBlock);
        if (viewers.isEmpty()) {
            stopBlock();
            return;
        }
        int next = blockFrame + 1;
        if (!loop && next >= frames.size()) {
            stopBlock(); // последний кадр уже применён
            return;
        }
        blockFrame = loop ? next % frames.size() : next;
        ItemStack frame = frames.get(blockFrame);
        for (Player viewer : viewers) {
            Inventory inventory = topInventoryOf(viewer);
            if (inventory != null && inventory.getHolder() instanceof GuiHolder holder) {
                applyFrame(holder, inventory, frame);
            }
        }
    }

    private void applyBlockFrame() {
        applyBlockFrameAt(runningBlock);
    }

    private void applyBlockFrameAt(Location block) {
        for (Player viewer : GuiOpener.getViewers(block)) {
            Inventory inventory = topInventoryOf(viewer);
            if (inventory != null && inventory.getHolder() instanceof GuiHolder holder) {
                applyFrame(holder, inventory, frames.get(blockFrame));
            }
        }
    }

    private void stopBlock() {
        if (blockTask != null) {
            blockTask.cancel();
            blockTask = null;
        }
        runningBlock = null;
        if (!isRunning()) {
            ACTIVE.remove(this);
        }
    }

    /**
     * Ставит кадр в слоты анимации через локальные оверрайды
     * (DESIGN/RESULT слоты; прочие типы пропускаются).
     */
    private void applyFrame(GuiHolder holder, Inventory inventory, ItemStack frame) {
        if (frame == null || frame.getType() == Material.AIR) {
            return;
        }
        for (int slot : slots) {
            if (slot < 0 || slot >= holder.gui().slots()) {
                continue;
            }
            SlotType type = holder.gui().slotType(slot);
            if (type != SlotType.DESIGN && type != SlotType.RESULT) {
                continue; // оверрайды только для не-персистентных слотов
            }
            GuiOpener.applyLocalDesign(holder, slot, frame);
        }
    }

    private static Inventory topInventoryOf(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        return top.getHolder() instanceof GuiHolder ? top : null;
    }

    private BukkitTask timer(Runnable action) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (!(plugin instanceof org.bukkit.plugin.java.JavaPlugin)) {
            throw new IllegalStateException(PLUGIN_NAME + " is not enabled");
        }
        return new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    action.run();
                } catch (Exception e) {
                    plugin.getLogger().warning("DesignAnimation tick failed, stopping animation: " + e.getMessage());
                    stop();
                }
            }
        }.runTaskTimer((org.bukkit.plugin.java.JavaPlugin) plugin, intervalTicks, intervalTicks);
    }

    // ================= builder =================

    /**
     * Builder {@link DesignAnimation}.
     */
    public static final class Builder {

        private final Set<Integer> slots = new LinkedHashSet<>();
        private List<ItemStack> frames;
        private int intervalTicks = 5;
        private boolean loop = true;
        private Location blockLocation;

        /** Добавляет слоты (DESIGN/RESULT) анимации. */
        public Builder slot(int... slotIndices) {
            for (int slot : slotIndices) {
                slots.add(slot);
            }
            return this;
        }

        /** Кадры анимации (не пустой список). */
        public Builder frames(List<ItemStack> frames) {
            this.frames = frames;
            return this;
        }

        /** Интервал между кадрами в тиках (минимум 1). */
        public Builder intervalTicks(int ticks) {
            this.intervalTicks = ticks;
            return this;
        }

        /** Зациклить кадры (по умолчанию true). */
        public Builder loop(boolean loop) {
            this.loop = loop;
            return this;
        }

        /**
         * Блок для per-блок режима (опционально; можно не задавать
         * и передать локацию в {@link #startForBlock(Location)}).
         */
        public Builder blockLocation(Location block) {
            this.blockLocation = block;
            return this;
        }

        /** Собирает анимацию. */
        public DesignAnimation build() {
            return new DesignAnimation(this);
        }
    }
}
