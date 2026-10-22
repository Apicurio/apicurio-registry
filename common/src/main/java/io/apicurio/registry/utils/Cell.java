package io.apicurio.registry.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

/**
 * One-item non-thread-safe container that can be used to pass a value into or out of a lambda function.
 * <p>
 * If you need a thread-safe alternative, use an {@link java.util.concurrent.atomic.AtomicReference}.
 */
@EqualsAndHashCode
@ToString
public class Cell<T> {

    private T value;
    private Supplier<T> loader;

    @JsonCreator
    public static <T> Cell<T> cell(T value) {
        return new Cell<>(value, null);
    }

    public static <T> Cell<T> cellOrDefault(T value, T defaultValue) {
        return new Cell<>(value, () -> defaultValue);
    }

    public static <T> Cell<T> cellWithLoader(Supplier<T> loader) {
        return new Cell<>(null, loader);
    }

    public static <T> Cell<T> cell() {
        return new Cell<>(null, null);
    }

    private Cell(T value, Supplier<T> loader) {
        this.value = value;
        this.loader = loader;
    }

    @JsonValue
    public T get() {
        if (value == null && loader != null) {
            value = loader.get();
            loader = null; // Load only once.
        }
        return value;
    }

    public Optional<T> getOptional() {
        return ofNullable(get());
    }

    public void set(T value) {
        this.value = value;
    }

    public boolean isSet() {
        return get() != null;
    }
}
