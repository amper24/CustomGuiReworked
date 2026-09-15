package dev.moonaticks.customGuiReworked.api.functional;

import dev.moonaticks.customGuiReworked.CustomGuiReworked;
import dev.moonaticks.customGuiReworked.gui.GuiRegistry;
import dev.moonaticks.customGuiReworked.integration.BlockHookDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Тесты «работы» функциональных блоков: данные персистятся, флаг
 * работы переживает перезагрузку, onBlockTick тикает без зрителей,
 * разрушение блока очищает данные.
 */
class FunctionalBlockWorkTest {

    @TempDir
    File dataDir;

    private World world;
    private Location block;
    private FunctionalBlockRegistry registry;
    private CustomGuiReworked plugin;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        block = new Location(world, 1, 2, 3);
        plugin = mock(CustomGuiReworked.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-functional"));
        when(plugin.registry()).thenReturn(mock(GuiRegistry.class));
        registry = new FunctionalBlockRegistry(plugin);
        when(plugin.functionalBlocks()).thenReturn(registry);
        FunctionalBlock.Builder builder =
                new FunctionalBlock.Builder("custom_pot", registry).gui("pot");
        registry.registerHandler("custom_pot", builder.build());
    }



    @Test
    void workingFlagAndDataSurviveRestart() throws Exception {
        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        assertTrue(registry.isWorking("custom_pot", block));
        FunctionalBlockData data = registry.data("custom_pot", block);
        data.setInt("cook", 42);
        data.set("meal", "mutton_stew");
        registry.saveData();

        // «Перезагрузка сервера»: новый реестр, те же файлы.
        FunctionalBlockRegistry restarted = new FunctionalBlockRegistry(plugin);
        restarted.loadData(dataDir);
        FunctionalBlock.Builder b = new FunctionalBlock.Builder("custom_pot", restarted)
                .gui("pot");
        restarted.registerHandler("custom_pot", b.build());

        assertTrue(restarted.isWorking("custom_pot", block), "флаг работы восстановлен");
        FunctionalBlockData restored = restarted.data("custom_pot", block);
        assertEquals(42, restored.getInt("cook", 0));
        assertEquals("mutton_stew", restored.getString("meal", ""));
    }

    @Test
    void stopWorkingRemovesFlag() {
        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        registry.saveData();
        registry.setWorking("custom_pot", block, false);
        assertFalse(registry.isWorking("custom_pot", block));
        registry.saveData();

        FunctionalBlockRegistry restarted = new FunctionalBlockRegistry(plugin);
        restarted.loadData(dataDir);
        restarted.registerHandler("custom_pot",
                new FunctionalBlock.Builder("custom_pot", restarted).gui("pot").build());
        assertFalse(restarted.isWorking("custom_pot", block));
    }

    @Test
    void setWorkingRejectsUnknownBlockId() {
        registry.loadData(dataDir);
        assertFalse(registry.setWorking("no_such_block", block, true));
        assertFalse(registry.isWorking("no_such_block", block));
    }

    @Test
    void removeBlockDataCleansEverything() {
        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        registry.data("custom_pot", block).setInt("cook", 10);
        registry.saveData();

        registry.removeBlockData(block);

        assertFalse(registry.isWorking("custom_pot", block));
        assertNull(registry.data("custom_pot", block).getString("cook", null),
                "данные блока удалены");
        // Файл перезаписан без этого блока: после «перезагрузки» ничего нет.
        FunctionalBlockRegistry restarted = new FunctionalBlockRegistry(plugin);
        restarted.loadData(dataDir);
        restarted.registerHandler("custom_pot",
                new FunctionalBlock.Builder("custom_pot", restarted).gui("pot").build());
        assertFalse(restarted.isWorking("custom_pot", block));
    }

    @Test
    void tickCallsOnBlockTickWithoutViewers() {
        AtomicReference<Location> seen = new AtomicReference<>();
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<Integer> cookAtTick = new AtomicReference<>();
        FunctionalBlock.Builder b =
                new FunctionalBlock.Builder("custom_pot", registry).gui("pot")
                        .onBlockTick((loc, data) -> {
                            seen.set(loc);
                            cookAtTick.set(data.getInt("cook", -1));
                            ticks.incrementAndGet();
                        });
        registry.registerHandler("custom_pot", b.build());

        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        registry.data("custom_pot", block).setInt("cook", 10);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);

        BlockHookDispatcher dispatcher = new BlockHookDispatcher(plugin, null, null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            dispatcher.tickFunctionalBlocks();
            dispatcher.tickFunctionalBlocks(); // повторный тик
        }

        assertEquals(2, ticks.get(), "onBlockTick вызывается каждый тикерный интервал");
        assertEquals(block, seen.get());
        assertEquals(10, cookAtTick.get(), "данные блока доступны в колбэке");
    }

    @Test
    void tickSkipsUnloadedChunks() {
        AtomicInteger ticks = new AtomicInteger();
        FunctionalBlock.Builder b =
                new FunctionalBlock.Builder("custom_pot", registry).gui("pot")
                        .onBlockTick((loc, data) -> ticks.incrementAndGet());
        registry.registerHandler("custom_pot", b.build());

        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        when(world.isChunkLoaded(0, 0)).thenReturn(false);

        BlockHookDispatcher dispatcher = new BlockHookDispatcher(plugin, null, null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            dispatcher.tickFunctionalBlocks();
        }
        assertEquals(0, ticks.get(), "unloaded чанки не тикаются");
    }

    @Test
    void tickSurvivesHandlerException() {
        FunctionalBlock.Builder first =
                new FunctionalBlock.Builder("custom_pot", registry).gui("pot")
                        .onBlockTick((loc, data) -> {
                            throw new IllegalStateException("boom");
                        });
        registry.registerHandler("custom_pot", first.build());

        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);

        BlockHookDispatcher dispatcher = new BlockHookDispatcher(plugin, null, null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            // Если бы исключение прокинулось — тест бы упал:
            // значит, диспетчер его проглотил (логирует и идёт дальше).
            dispatcher.tickFunctionalBlocks();
        }
    }

    @Test
    void workingEntriesSnapshotIsStable() {
        registry.loadData(dataDir);
        registry.setWorking("custom_pot", block, true);
        Map<String, java.util.Set<String>> entries = registry.workingEntries();
        assertTrue(entries.containsKey("custom_pot"));
        assertTrue(entries.get("custom_pot").contains("world:1,2,3"));
    }

    @Test
    void dataRequiresRegisteredBlockId() {
        assertNull(registry.data("unknown_block", block));
        Map<String, String> backing = new ConcurrentHashMap<>();
        FunctionalBlockData data = new FunctionalBlockData(backing, null);
        assertTrue(data.isEmpty());
        data.setInt("x", 1);
        assertEquals(1, data.getInt("x", 0));
    }
}
