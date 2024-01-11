package io.apicurio.registry.serde.nats.client.streaming.producers;


import io.apicurio.registry.serde.nats.client.exceptions.ApicurioNatsException;

public interface NatsProducer<T> extends AutoCloseable {


    void send(T message) throws ApicurioNatsException;
}
