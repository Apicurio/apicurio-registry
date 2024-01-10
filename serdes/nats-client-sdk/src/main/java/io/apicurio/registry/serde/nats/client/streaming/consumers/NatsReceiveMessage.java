package io.apicurio.registry.serde.nats.client.streaming.consumers;

import io.nats.client.Message;

public interface NatsReceiveMessage {
    Message getMessageContext();
    Object getPayload();
    void ack();
}
