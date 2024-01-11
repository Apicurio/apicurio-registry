package io.apicurio.registry.serde.nats.client.streaming.consumers;

import io.apicurio.registry.serde.NatsDeserializer;
import io.apicurio.registry.serde.config.nats.NatsConsumerConfig;
import io.apicurio.registry.serde.generic.Utils;
import io.nats.client.Connection;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class NatsConsumerImpl<DATA> implements NatsConsumer<DATA> {

    private NatsDeserializer<DATA> deserializer;

    private Connection connection;

    private String subject;

    private PullSubscribeOptions subscribeOptions;

    private JetStreamSubscription subscription;

    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());


    public NatsConsumerImpl(Connection connection, String subject, PullSubscribeOptions subscribeOptions, Properties config) {
        this.connection = connection;
        this.subject = subject;
        this.subscribeOptions = subscribeOptions;

        deserializer = Utils.newConfiguredInstance(config.get(NatsConsumerConfig.DESERIALIZER_CLASS_CONFIG), NatsDeserializer.class, org.apache.kafka.common.utils.Utils.propsToMap(config), null);
    }


    private JetStreamSubscription getLazySubscription() throws Exception {
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
    public NatsConsumerRecord<DATA> receive() throws Exception {
        return receive(Duration.ofSeconds(3));
    }


    @Override
    public NatsConsumerRecord<DATA> receive(Duration timeout) throws Exception {
        List<NatsConsumerRecord<DATA>> messages = receive(1, timeout);
        return messages == null ? null : messages.get(0); // TODO
    }


    @Override
    public List<NatsConsumerRecord<DATA>> receive(int batchSize, Duration timeout) throws Exception {

        List<Message> messages = getLazySubscription().fetch(batchSize, timeout);

        if (messages == null || messages.isEmpty()) {
            logger.info("Receive timeout ({} ms)", timeout.toMillis());
            return null; // TODO
        }

        List<NatsConsumerRecord<DATA>> records = new ArrayList<>();
        for (Message message : messages) {
            DATA payload = deserializer.deserialize(subject, message.getData());
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
