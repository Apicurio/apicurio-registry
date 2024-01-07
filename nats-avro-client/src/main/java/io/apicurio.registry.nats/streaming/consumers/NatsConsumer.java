package io.apicurio.registry.nats.streaming.consumers;

import io.apicurio.registry.nats.exceptions.NatsClientException;
import java.util.Collection;

public interface NatsConsumer {

    String getSubject();

    void unsubscribe() throws InterruptedException;

    NatsReceiveMessage receive() throws NatsClientException, InterruptedException;

    NatsReceiveMessage receive(long timeoutInMillis) throws NatsClientException, InterruptedException;

    Collection<NatsReceiveMessage> receive(int batchsize, long timeoutInMillis) throws NatsClientException, InterruptedException;

}
