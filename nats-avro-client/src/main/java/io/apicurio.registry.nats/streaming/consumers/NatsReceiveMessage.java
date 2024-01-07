package io.apicurio.registry.nats.streaming.consumers;

import io.nats.client.Message;

public interface NatsReceiveMessage {
    Message getMessageContext();
    Object getPayload();
    void ack();
}
