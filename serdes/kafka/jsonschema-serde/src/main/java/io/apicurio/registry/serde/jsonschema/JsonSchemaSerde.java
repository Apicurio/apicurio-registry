package io.apicurio.registry.serde.jsonschema;

/**
 * Wraps the JsonSchemaKafkaSerializer and JsonSchemaKafkaDeserializer.
 */
public class JsonSchemaSerde<T> extends AbstractSerde<T> {
    public JsonSchemaSerde() {
        super(new JsonSchemaKafkaSerializer<>(), new JsonSchemaKafkaDeserializer<>());
    }
}
