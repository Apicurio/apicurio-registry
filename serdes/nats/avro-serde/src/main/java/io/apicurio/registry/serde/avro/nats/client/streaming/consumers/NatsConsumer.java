package io.apicurio.registry.serde.avro.nats.client.streaming.consumers;

import io.nats.client.JetStreamApiException;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;

public interface NatsConsumer<T> extends AutoCloseable {

    String getSubject();

    NatsConsumerRecord<T> receive() throws JetStreamApiException, IOException;

    NatsConsumerRecord<T> receive(Duration timeout) throws JetStreamApiException, IOException;

    Collection<NatsConsumerRecord<T>> receive(int batchSize, Duration timeout)
            throws JetStreamApiException, IOException;
}
