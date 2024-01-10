package io.apicurio.registry.serde.nats.client.streaming.producers;


import io.apicurio.registry.serde.nats.client.exceptions.NatsClientException;
import java.io.IOException;
import java.util.Collection;

public interface NatsProducer {

    <T> void sendMessage(T message) throws NatsClientException;

    <T> void sendMessages(Collection<T> messages) throws NatsClientException;

    void closeProducer() throws IOException, InterruptedException;

    static NatsProducer defaultOf(String subject) throws NatsClientException {
        return NatsProducerImpl.getInstance(subject);
    }

    static NatsProducer builderOf(String subject) throws NatsClientException {
        return null;
    }

}
