package io.apicurio.registry.serde.avro.nats.client.streaming.consumers;

import io.nats.client.JetStreamApiException;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;

public interface NatsConsumer<T> extends AutoCloseable {

    String getSubject();

    NatsConsumerRecord<T> fetch() throws JetStreamApiException, IOException;

    NatsConsumerRecord<T> fetch(Duration timeout) throws JetStreamApiException, IOException;

    Collection<NatsConsumerRecord<T>> fetch(int batchSize, Duration timeout)
            throws JetStreamApiException, IOException;
}
