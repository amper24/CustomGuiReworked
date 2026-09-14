package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.api.StorageType;

/**
 * Бэкенд хранения данных (персистентный слой).
 *
 * <p>Методы могут вызываться с любого потока; реализации обязаны
 * быть тред-безопасными. Записи выполняются атомарно
 * (временный файл + move).
 */
public interface StorageBackend {

    /** Тип хранилища, который обслуживает бэкенд. */
    StorageType type();

    /**
     * Читает данные ключа.
     *
     * @return массив payloadов (может быть короче, чем размер GUI —
     *         недостающие слоты считаются пустыми)
     */
    String[] read(StorageKey key);

    /**
     * Записывает данные ключа. Пустые данные удаляют запись с диска.
     */
    void write(StorageKey key, String[] slots);

    /** Удаляет данные ключа. */
    void remove(StorageKey key);

    /** Синхронный flush внутренних буферов (вызывается при выключении). */
    void flushAll();
}
