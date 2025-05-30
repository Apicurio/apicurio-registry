package io.apicurio.registry.utils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

/**
 * One-item non-thread-safe cache-like container that can be used to read and update a "remote" value.
 * <p>
 * Remote value means that there exist two functions, known up front:
 * - reader - used to read the value from the remote system, and
 * - updater - used to update the value in the remote system.
 * <p>
 * Example use case: Working with a Kubernetes resource.
 */
public class AutoCell<T> {

    private T cachedValue;

    private final Supplier<T> reader;

    private final Function<T, T> updater;

    /**
     * Create a new auto cell with the specified reader and updater functions.
     */
    public static <T> AutoCell<T> acell(Supplier<T> reader, Function<T, T> updater) {
        return new AutoCell<>(reader, updater);
    }

    /**
     * Create a new auto cell with the specified reader function.
     * Resource will not need to be updated in the remote system.
     */
    public static <T> AutoCell<T> acell(Supplier<T> reader) {
        return new AutoCell<>(reader, x -> x);
    }

    /**
     * Create a new auto cell with the specified initial value supplier function, and an updater function.
     * Resource will not need to be read more that once from the remote system.
     */
    public static <T> AutoCell<T> acelli(Supplier<T> initializer, Function<T, T> updater) {
        var initialValue = initializer.get();
        return new AutoCell<>(() -> initialValue, updater);
    }

    /**
     * Create a new auto cell with the specified initial value, and an updater function.
     * Resource will not need to be read more that once from the remote system.
     */
    public static <T> AutoCell<T> acelliv(T initialValue, Function<T, T> updater) {
        return new AutoCell<>(() -> initialValue, updater);
    }

    private AutoCell(Supplier<T> reader, Function<T, T> updater) {
        this.reader = reader;
        this.updater = updater;
    }

    public T get() {
        cachedValue = reader.get();
        return cachedValue;
    }

    public Optional<T> getOptional() {
        return ofNullable(get());
    }

    public T getCached() {
        return cachedValue;
    }

    public Optional<T> getCachedOptional() {
        return ofNullable(getCached());
    }

    public void update(Consumer<T> action) {
        action.accept(get());
        cachedValue = updater.apply(cachedValue);
    }

    public void updateCached(Consumer<T> action) {
        action.accept(cachedValue);
        cachedValue = updater.apply(cachedValue);
    }
}
