package io.apicurio.registry.serde.jsonschema;

import io.apicurio.registry.serde.AbstractSerde;

/**
 * Wraps the JsonSchemaKafkaSerializer and JsonSchemaKafkaDeserializer.
 */
public class JsonSchemaSerde<T> extends AbstractSerde<T> {
    public JsonSchemaSerde() {
        super(new JsonSchemaKafkaSerializer<>(), new JsonSchemaKafkaDeserializer<>());
    }
}
