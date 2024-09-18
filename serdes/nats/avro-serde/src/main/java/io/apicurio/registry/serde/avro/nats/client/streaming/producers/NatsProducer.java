package io.apicurio.registry.serde.avro.nats.client.streaming.producers;

import io.apicurio.registry.serde.avro.nats.client.exceptions.ApicurioNatsException;

public interface NatsProducer<T> extends AutoCloseable {

    void send(T message) throws ApicurioNatsException;
}
