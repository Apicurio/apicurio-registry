package io.apicurio.registry.serde.avro.nats.client.streaming.consumers;

import io.apicurio.registry.serde.avro.AvroDeserializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NatsConsumerImpl<DATA> implements NatsConsumer<DATA> {

    private final AvroDeserializer<DATA> deserializer;

    private final Connection connection;

    private final String subject;

    private final PullSubscribeOptions subscribeOptions;

    private JetStreamSubscription subscription;

    private static final Logger logger = LoggerFactory.getLogger(NatsConsumerImpl.class);

    public NatsConsumerImpl(Connection connection, String subject, PullSubscribeOptions subscribeOptions,
            Map<String, Object> config) {
        this.connection = connection;
        this.subject = subject;
        this.subscribeOptions = subscribeOptions;

        AvroSerdeConfig serializerConfig = new AvroSerdeConfig(config);
        deserializer = new AvroDeserializer<>();

        deserializer.configure(serializerConfig, false);
    }

    private JetStreamSubscription getLazySubscription() throws IOException, JetStreamApiException {
        if (subscription == null) {
            subscription = connection.jetStream().subscribe(subject, subscribeOptions);
        }
        return subscription;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public NatsConsumerRecord<DATA> receive() throws JetStreamApiException, IOException {
        return receive(Duration.ofSeconds(3));
    }

    @Override
    public NatsConsumerRecord<DATA> receive(Duration timeout) throws JetStreamApiException, IOException {
        Collection<NatsConsumerRecord<DATA>> messages = receive(1, timeout);
        Optional<NatsConsumerRecord<DATA>> record = messages.stream().findFirst();
        return record.orElse(null);
    }

    @Override
    public List<NatsConsumerRecord<DATA>> receive(int batchSize, Duration timeout)
            throws JetStreamApiException, IOException {
        List<Message> messages = getLazySubscription().fetch(batchSize, timeout);

        if (messages == null || messages.isEmpty()) {
            logger.info("Receive timeout ({} ms)", timeout.toMillis());
            // TODO consider throwing an exception?
            return Collections.emptyList();
        }

        List<NatsConsumerRecord<DATA>> records = new ArrayList<>();
        for (Message message : messages) {
            DATA payload = deserializer.deserializeData(subject, message.getData());
            records.add(new NatsConsumerRecordImpl<>(message, payload));
        }
        return records;
    }

    @Override
    public void close() throws Exception {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }
}
