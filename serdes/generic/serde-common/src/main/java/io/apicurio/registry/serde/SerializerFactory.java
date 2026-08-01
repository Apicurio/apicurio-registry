package io.apicurio.registry.serde;

@FunctionalInterface
public interface SerializerFactory<T, U> {
    AbstractSerializer<T, U> create();
}
