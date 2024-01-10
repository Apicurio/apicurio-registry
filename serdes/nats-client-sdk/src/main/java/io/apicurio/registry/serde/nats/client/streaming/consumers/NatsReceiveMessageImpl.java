package io.apicurio.registry.serde.nats.client.streaming.consumers;

import io.nats.client.Message;

public class NatsReceiveMessageImpl implements NatsReceiveMessage{

    private Message messageContext;

    private Object payload;

    public NatsReceiveMessageImpl(Message internalMessage, Object payload){
        this.messageContext = internalMessage;
        this.payload = payload;
    }
    @Override
    public Message getMessageContext() {
        return messageContext;
    }

    @Override
    public Object getPayload() {
        return payload;
    }

    @Override
    public void ack() {
        messageContext.ack();
    }
}
