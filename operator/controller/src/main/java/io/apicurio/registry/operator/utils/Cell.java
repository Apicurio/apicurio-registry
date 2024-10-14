package io.apicurio.registry.operator.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Non-atomic variable wrapper that can be final and can be used to share data with lambda functions.
 *
 * @param <T> type of the internal value
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Cell<T> {

    public static <T> Cell<T> cell(T value) {
        return new Cell<>(value);
    }

    T value;

    private Cell(T value) {
        this.value = value;
    }
}
