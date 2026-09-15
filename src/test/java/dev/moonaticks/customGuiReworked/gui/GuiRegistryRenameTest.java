package dev.moonaticks.customGuiReworked.gui;

import dev.moonaticks.customGuiReworked.api.Gui;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Реестр: переименование уже зарегистрированного экземпляра через API
 * ({@link Gui#rename(String)} + повторный register/save) не должно оставлять
 * вторую запись в карте имён, «призрачные» block-привязки и старые файлы.
 */
class GuiRegistryRenameTest {

    @TempDir
    Path tempDir;

    private GuiRegistry registry() {
        // plugin=null: RUNTIME-путь не пишет в лог и не трогает файлы.
        return new GuiRegistry(null, tempDir.toFile());
    }

    @Test
    @DisplayName("register после rename не оставляет старый ключ в карте")
    void registerAfterRename() {
        GuiRegistry registry = registry();
        Gui gui = new Gui("shop");
        registry.register(gui, false);
        assertNotNull(registry.get("shop"));

        gui.rename("market");
        registry.register(gui, false);

        assertNull(registry.get("shop"), "старое имя должно исчезнуть из реестра");
        assertSame(gui, registry.get("market"));
        assertEquals(1, registry.all().size(), "экземпляр в карте ровно один");
    }

    @Test
    @DisplayName("block-индекс переезжает на новое имя")
    void blockIndexRebindsAfterRename() {
        GuiRegistry registry = registry();
        Gui gui = new Gui("shop");
        gui.addBlockId("myblocks:shop");
        registry.register(gui, false);
        assertSame(gui, registry.getByBlockId("myblocks:shop"));

        gui.rename("market");
        registry.register(gui, false);

        assertSame(gui, registry.getByBlockId("myblocks:shop"));
        assertEquals(1, registry.all().size());
    }

    @Test
    @DisplayName("save после rename (RUNTIME) не оставляет старый ключ")
    void saveAfterRenameRuntime() {
        GuiRegistry registry = registry();
        Gui gui = new Gui("shop");
        registry.register(gui, false);

        gui.rename("market");
        registry.save(gui);

        assertNull(registry.get("shop"));
        assertSame(gui, registry.get("market"));
    }

    @Test
    @DisplayName("файл под старым именем удаляется при переименовании persist-GUI")
    void persistedRenameDeletesStaleFile() {
        File customDir = new File(tempDir.toFile(), "custom");
        GuiRegistry registry = registry();
        Gui gui = new Gui("shop");
        registry.register(gui, true);

        File oldFile = new File(customDir, "shop.yml");
        File newFile = new File(customDir, "market.yml");
        assertEquals(true, oldFile.exists(), "первичная запись должна создать shop.yml");

        gui.rename("market");
        registry.register(gui, true);

        assertEquals(false, oldFile.exists(), "shop.yml должен быть удалён после переименования");
        assertEquals(true, newFile.exists(), "market.yml должен быть создан");
        assertSame(gui, registry.get("market"));
        assertEquals(1, registry.all().size());
    }

    @Test
    @DisplayName("понижение CUSTOM до RUNTIME удаляет файл из custom/")
    void downgradeToRuntimeDeletesFile() {
        File customDir = new File(tempDir.toFile(), "custom");
        GuiRegistry registry = registry();
        Gui gui = new Gui("shop");
        registry.register(gui, true);
        File file = new File(customDir, "shop.yml");
        assertEquals(true, file.exists());

        registry.register(gui, false);

        assertEquals(false, file.exists(), "файл custom/shop.yml должен быть удалён");
        assertSame(gui, registry.get("shop"));
    }

    @Test
    @DisplayName("переименование persist-GUI с тем же source чистит старый файл в своей папке")
    void tableRenameViaSaveDeletesOldFile() {
        File tableDir = new File(tempDir.toFile(), "tables");
        GuiRegistry registry = registry();
        Gui gui = new Gui("shop");
        registry.register(gui, false);
        // имитируем редакторный GUI (файл в tables/)
        gui.source(Gui.Source.TABLE);
        registry.save(gui);
        File oldFile = new File(tableDir, "shop.yml");
        assertEquals(true, oldFile.exists());

        gui.rename("market");
        registry.save(gui);

        assertEquals(false, oldFile.exists());
        assertEquals(true, new File(tableDir, "market.yml").exists());
        assertNull(registry.get("shop"));
        assertSame(gui, registry.get("market"));
    }

    @Test
    @DisplayName("повторный register с новым именем вытесняет чужой GUI с таким именем")
    void renameOntoOccupiedName() {
        GuiRegistry registry = registry();
        Gui first = new Gui("shop");
        registry.register(first, false);
        Gui other = new Gui("market");
        registry.register(other, false);

        first.rename("market");
        Gui result = registry.register(first, false);

        assertSame(first, result);
        assertSame(first, registry.get("market"));
        assertNull(registry.get("shop"));
        assertEquals(1, registry.all().size());
    }
}
