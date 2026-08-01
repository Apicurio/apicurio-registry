package io.apicurio.registry.serde;

@FunctionalInterface
public interface DeserializerFactory<T, U> {
    AbstractDeserializer<T, U> create();
}
