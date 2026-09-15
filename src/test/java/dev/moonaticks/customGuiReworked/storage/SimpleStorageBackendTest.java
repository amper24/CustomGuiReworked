package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.api.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip файлового бэкенда. {@code plugin = null} допустим:
 * на успешном пути логгер не используется. Payloadы обязаны быть
 * подписаны тегом кодека (n1/b1/b2), иначе {@code LegacyPayloads.migrate}
 * попытается их конвертировать.
 */
class SimpleStorageBackendTest {

    @TempDir
    Path tempDir;

    private SimpleStorageBackend backend;
    private File folder;

    @BeforeEach
    void setUp() {
        folder = tempDir.resolve("players").toFile();
        backend = new SimpleStorageBackend(null, StorageType.PERSONAL, folder, true);
    }

    @AfterEach
    void tearDown() {
        backend.flushAll();
    }

    private StorageKey key(String owner, String table) {
        return new StorageKey(StorageType.PERSONAL, owner, table);
    }

    @Test
    void readMissingReturnsEmpty() throws IOException {
        assertEquals(0, backend.read(key("steve", "shop.yml")).length);
    }

    @Test
    void writeThenReadRoundTrip() throws IOException {
        StorageKey key = key("steve", "shop.yml");
        String[] slots = {"n1:{}a", "", "b2:AAAA"};
        backend.write(key, slots);

        String[] read = backend.read(key);
        assertArrayEquals(slots, read);
        // ownerInName — файл содержит владельца
        File expected = new File(folder, "steve_shop.yml");
        assertTrue(expected.exists());
    }

    @Test
    void globalBackendOmitsOwnerInFileName() throws IOException {
        File globals = tempDir.resolve("globals").toFile();
        SimpleStorageBackend global = new SimpleStorageBackend(null, StorageType.GLOBAL, globals, false);
        global.write(StorageKey.global("market.yml"), new String[]{"n1:x"});
        assertTrue(new File(globals, "market.yml").exists());
    }

    @Test
    void emptyWriteDeletesFile() throws IOException {
        StorageKey key = key("steve", "shop.yml");
        backend.write(key, new String[]{"n1:x", "n1:y"});
        File file = new File(folder, "steve_shop.yml");
        assertTrue(file.exists());

        backend.write(key, new String[]{"", ""});
        assertFalse(file.exists(), "пустой массив должен удалять файл");

        backend.write(key, new String[]{"n1:x"});
        assertTrue(file.exists());
        backend.write(key, null);
        assertFalse(file.exists());
    }

    @Test
    void removeDeletesFile() throws IOException {
        StorageKey key = key("steve", "shop.yml");
        backend.write(key, new String[]{"n1:x"});
        File file = new File(folder, "steve_shop.yml");
        assertTrue(file.exists());

        backend.remove(key);
        assertFalse(file.exists());
        // повторный remove не бросает
        backend.remove(key);
    }

    @Test
    void unsafeOwnerNameIsSanitizedInFileName() throws IOException {
        StorageKey key = key("../evil", "shop.yml");
        backend.write(key, new String[]{"n1:x"});
        // ни один файл не должен оказаться вне папки бэкенда
        assertTrue(folder.listFiles().length > 0);
        for (File f : folder.listFiles()) {
            assertTrue(f.getName().contains("shop.yml"));
        }
        assertFalse(tempDir.resolve("evil_shop.yml").toFile().exists()
                , "parent-переход не должен создавать файлы вне папки");
    }

    @Test
    void corruptJsonYieldsEmptyNotException() throws IOException {
        File file = new File(folder, "steve_shop.yml");
        folder.mkdirs();
        Files.writeString(file.toPath(), "this is not json", StandardCharsets.UTF_8);
        String[] read = backend.read(key("steve", "shop.yml"));
        assertEquals(0, read.length);
    }

    @Test
    void staleTmpFromCrashIsSweptOnConstruction() throws IOException {
        folder.mkdirs();
        Files.writeString(folder.toPath().resolve("steve_shop.yml.tmp-uuid-1"), "partial");
        Files.writeString(folder.toPath().resolve("other.tmp-abc"), "partial");
        new SimpleStorageBackend(null, StorageType.PERSONAL, folder, true);
        File[] leftovers = folder.listFiles((d, name) -> name.contains(".tmp-"));
        assertEquals(0, leftovers == null ? 0 : leftovers.length);
    }

    @Test
    void writesAreAtomicNoLeftoverTmpFiles() throws IOException {
        StorageKey key = key("steve", "shop.yml");
        for (int i = 0; i < 10; i++) {
            backend.write(key, new String[]{"n1:item" + i});
        }
        File[] leftovers = folder.listFiles((d, name) -> name.endsWith(".tmp") || name.contains(".tmp-"));
        assertEquals(0, leftovers == null ? 0 : leftovers.length);
        assertEquals("n1:item9", backend.read(key)[0]);
    }
}
