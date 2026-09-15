package dev.moonaticks.customGuiReworked.api.functional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Персистентные данные функционального блока (прогресс, состояние,
 * произвольные числа/строки) — аналог «states.yml» в скриптах, где
 * логика блока живёт независимо от GUI.
 *
 * <p>Хранится на стороне блока, а не GUI: работает и тогда, когда
 * интерфейс закрыт, и переживает перезагрузку сервера. Файл —
 * {@code data/functional/<blockId>.yml} в папке плагина.
 *
 * <p>Все значения сериализуются строками; типы восстанавливаются
 * при чтении. Все методы вызываются на основном потоке.
 *
 * <pre>{@code
 * FunctionalBlockData data = CustomGuiAPI.blockData("custom_furnace", block);
 * int cook = data.getInt("cook", 0);
 * data.setInt("cook", cook + 1);
 * }</pre>
 */
public final class FunctionalBlockData {

    /** Внутренний ключ флага «блок работает» (см. {@link FunctionalBlockRegistry#setWorking}). */
    public static final String WORKING_KEY = "_working";

    private final Map<String, String> values;
    private final Runnable dirtyHook;

    FunctionalBlockData(Map<String, String> values, Runnable dirtyHook) {
        this.values = values;
        this.dirtyHook = dirtyHook;
    }

    /** Значение как int, либо {@code def}, если ключа нет/не число. */
    public int getInt(String key, int def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Значение как double, либо {@code def}, если ключа нет/не число. */
    public double getDouble(String key, double def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Значение как boolean («true»/«1»), либо {@code def}. */
    public boolean getBoolean(String key, boolean def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        v = v.trim().toLowerCase(java.util.Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }

    /** Значение как String, либо {@code def}. */
    public String getString(String key, String def) {
        return values.getOrDefault(key, def);
    }

    /** Устанавливает int-значение (помечает файл на сохранение). */
    public void setInt(String key, int value) {
        set(key, Integer.toString(value));
    }

    /** Устанавливает double-значение. */
    public void setDouble(String key, double value) {
        set(key, Double.toString(value));
    }

    /** Устанавливает boolean-значение. */
    public void setBoolean(String key, boolean value) {
        set(key, value ? "true" : "false");
    }

    /** Устанавливает строковое значение. */
    public void set(String key, String value) {
        if (value == null) {
            remove(key);
            return;
        }
        values.put(key, value);
        markDirty();
    }

    /** true, если ключ установлен. */
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    /** Удаляет ключ (пустые данные очищаются целиком из файла). */
    public void remove(String key) {
        if (values.remove(key) != null) {
            markDirty();
        }
    }

    /** Удаляет все данные блока. */
    public void clear() {
        if (!values.isEmpty()) {
            values.clear();
            markDirty();
        }
    }

    /** true, если данных нет. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Все ключи (копия). */
    public Set<String> keys() {
        return new LinkedHashSet<>(values.keySet());
    }

    private void markDirty() {
        if (dirtyHook != null) {
            dirtyHook.run();
        }
    }

    /** Числовое/строковое представление (для отладки). */
    @Override
    public String toString() {
        return "FunctionalBlockData" + Collections.unmodifiableMap(values);
    }
}
