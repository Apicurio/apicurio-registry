package io.apicurio.registry.utils;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Proxyable alternative to java.util.Optional.
 * Optional cannot be used with CDI because it is final.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class Maybe<T> {
    private static final Maybe<?> EMPTY = new Maybe<>();
    private final T value;

    @SuppressWarnings("unchecked")
    public static <T> Maybe<T> empty() {
        return (Maybe<T>) EMPTY;
    }

    public static <T> Maybe<T> of(T value) {
        return new Maybe<>(value);
    }

    public static <T> Maybe<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    /**
     * This constructor is public to avoid an error in Quarkus and should not be called directly.
     */
    public Maybe() {
        this.value = null;
    }

    /**
     * This constructor is public to avoid an error in Quarkus and should not be called directly.
     */
    public Maybe(T value) {
        this.value = Objects.requireNonNull(value);
    }

    public T get() {
        if (this.value == null) {
            throw new NoSuchElementException("No value present");
        } else {
            return this.value;
        }
    }

    public boolean isPresent() {
        return this.value != null;
    }

    public boolean isEmpty() {
        return this.value == null;
    }

    public <U> Maybe<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return !this.isPresent() ? empty() : ofNullable(mapper.apply(this.value));
    }

    public Stream<T> stream() {
        return !this.isPresent() ? Stream.empty() : Stream.of(this.value);
    }

    public T orElse(T other) {
        return this.value != null ? this.value : other;
    }

    public Optional<T> toOptional() {
        return Optional.ofNullable(this.value);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Maybe)) {
            return false;
        } else {
            Maybe<?> other = (Maybe<?>) obj;
            return Objects.equals(this.value, other.value);
        }
    }

    public int hashCode() {
        return Objects.hashCode(this.value);
    }

    public String toString() {
        return this.value != null ? String.format("Maybe[%s]", this.value) : "Maybe.empty";
    }
}
