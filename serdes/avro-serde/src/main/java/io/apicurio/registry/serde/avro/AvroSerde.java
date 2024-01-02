package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.AbstractSerde;

/**
 * Wraps the AvroKafkaSerializer and AvroKafkaDeserializer.
 */
public class AvroSerde<T> extends AbstractSerde<T> {
    public AvroSerde() {
        super(new AvroKafkaSerializer<>(), new AvroKafkaDeserializer<>());
    }
}
