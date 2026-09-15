package dev.moonaticks.customGuiReworked.storage;

/**
 * Хранимое в памяти представление данных одного {@link StorageKey}.
 *
 * <p>Позволяет повторные открытия GUI без чтения с диска
 * и коалесить многократные изменения в одну запись.
 * Поле {@code version} защищает асинхронную запись от гонок:
 * если данные изменились во время записи, она повторяется.
 */
final class StorageView {

    final StorageKey key;
    volatile String[] slots;
    volatile boolean dirty;
    volatile long version;
    volatile long lastUsed;

    StorageView(StorageKey key, String[] slots) {
        this.key = key;
        this.slots = slots == null ? new String[0] : slots;
        this.lastUsed = System.currentTimeMillis();
    }

    void touch() {
        this.lastUsed = System.currentTimeMillis();
    }

    void markDirty() {
        this.version++;
        this.dirty = true;
    }
}
