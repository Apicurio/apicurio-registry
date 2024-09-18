package io.apicurio.registry.serde.avro.nats.client.streaming.consumers;

import io.nats.client.Message;

public class NatsConsumerRecordImpl<T> implements NatsConsumerRecord<T> {

    private final Message natsMessage;

    private final T payload;

    public NatsConsumerRecordImpl(Message natsMessage, T payload) {
        this.natsMessage = natsMessage;
        this.payload = payload;
    }

    @Override
    public Message getNatsMessage() {
        return natsMessage;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public void ack() {
        natsMessage.ack();
    }
}
