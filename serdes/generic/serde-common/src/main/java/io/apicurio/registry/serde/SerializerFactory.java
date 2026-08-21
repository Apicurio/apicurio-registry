package io.apicurio.registry.serde;

/**
 * Factory for creating {@link AbstractSerializer} instances.
 *
 * @param <T> the serializer payload type
 * @param <U> the serializer configuration type
 */
@FunctionalInterface
public interface SerializerFactory<T, U> {

    /**
     * Creates a new serializer instance.
     *
     * @return a new {@link AbstractSerializer}
     */
    AbstractSerializer<T, U> create();
}
