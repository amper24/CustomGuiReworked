package dev.moonaticks.customGuiReworked.storage;

import dev.moonaticks.customGuiReworked.api.StorageType;

import java.io.IOException;

/**
 * Бэкенд хранения данных (персистентный слой).
 *
 * <p>Методы могут вызываться с любого потока (за чтение/запись отвечает
 * выделенный поток ввода-вывода в {@link StorageService}); реализации
 * обязаны быть тред-безопасными. Записи выполняются атомарно
 * (уникальный временный файл + ATOMIC_MOVE), чтобы параллельные записи
 * не повредили друг друга.
 *
 * <p>При ошибке ввода-вывода реализация обязана бросить {@link IOException}
 * (а не глотать её): тогда слой сервиса пометит данные как «грязные»
 * и повторит запись позже — данные не теряются молча.
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
    String[] read(StorageKey key) throws IOException;

    /**
     * Записывает данные ключа. Пустые данные удаляют запись с диска.
     *
     * @throws IOException при ошибке записи (вызов обязан повторить попытку позже)
     */
    void write(StorageKey key, String[] slots) throws IOException;

    /** Удаляет данные ключа. */
    void remove(StorageKey key) throws IOException;

    /** Синхронный flush внутренних буферов (вызывается при выключении). */
    void flushAll() throws IOException;
}
