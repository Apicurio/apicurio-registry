package io.apicurio.registry.serde.avro.nats.client.streaming.consumers;

import io.nats.client.Message;

public interface NatsConsumerRecord<T> {

    Message getNatsMessage();

    T getPayload();

    void ack();

    void nak();

    void term();

    void inProgress();
}
