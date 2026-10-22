package io.apicurio.registry.serde.avro.nats.client.streaming.producers;

import io.apicurio.registry.serde.avro.nats.client.exceptions.ApicurioNatsException;

public interface NatsProducer<T> extends AutoCloseable {

    void publish(T message) throws ApicurioNatsException;
}
