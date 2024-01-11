package io.apicurio.registry.serde.nats.client.streaming.consumers;

import java.time.Duration;
import java.util.Collection;

public interface NatsConsumer<T> extends AutoCloseable {


    String getSubject();


    NatsConsumerRecord<T> receive() throws Exception;


    NatsConsumerRecord<T> receive(Duration timeout) throws Exception;


    Collection<NatsConsumerRecord<T>> receive(int batchSize, Duration timeout) throws Exception;
}
