package io.apicurio.registry.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * One-item non-thread-safe container used to pass values out of lambda functions.
 * If you need a thread-safe alternative, use an {@link java.util.concurrent.atomic.AtomicReference}.
 */
@EqualsAndHashCode
@ToString
public class Cell<T> {

    private T value;

    @JsonCreator
    public static <T> Cell<T> cell(T value) {
        return new Cell<>(value);
    }

    public static <T> Cell<T> cell() {
        return new Cell<>(null);
    }

    private Cell(T value) {
        this.value = value;
    }

    @JsonValue
    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public boolean isSet() {
        return value != null;
    }

    public Optional<T> toOptional() {
        return ofNullable(value);
    }
}
