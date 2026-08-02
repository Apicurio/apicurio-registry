package io.apicurio.registry.serde;

/**
 * Factory for creating {@link AbstractDeserializer} instances.
 *
 * @param <T> the deserializer payload type
 * @param <U> the deserializer configuration type
 */
@FunctionalInterface
public interface DeserializerFactory<T, U> {

    /**
     * Creates a new deserializer instance.
     *
     * @return a new {@link AbstractDeserializer}
     */
    AbstractDeserializer<T, U> create();
}
