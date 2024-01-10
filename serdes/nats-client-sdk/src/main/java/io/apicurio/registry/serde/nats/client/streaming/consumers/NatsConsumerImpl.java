package io.apicurio.registry.serde.nats.client.streaming.consumers;

import io.apicurio.registry.serde.nats.client.ConfigurationProvider;
import io.apicurio.registry.serde.nats.client.exceptions.NatsClientException;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.utils.Lists;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsConsumerImpl implements NatsConsumer {

    private Deserializer deserializer;

    private Connection connection;

    private String subject;

    private JetStreamSubscription subscription;

    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    public NatsConsumerImpl(String subject, PullSubscribeOptions pullSubscribeOptions) throws IOException, InterruptedException, JetStreamApiException {
        this.subject = subject;
        deserializer = new AvroKafkaDeserializer();
        deserializer.configure(getConfig(), false);
        connection = ConnectionFactory.getConnection();
        JetStream jetStream = connection.jetStream();
        subscription = jetStream.subscribe(subject, pullSubscribeOptions);
    }
    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public void unsubscribe() throws InterruptedException {
        if(connection != null ) {
            connection.close();
        }
    }

    @Override
    public NatsReceiveMessage receive() throws NatsClientException, InterruptedException {
        return receive(3000);
    }

    @Override
    public NatsReceiveMessage receive(long timeoutInMillis) throws NatsClientException, InterruptedException {
        Collection<NatsReceiveMessage> messages = receive(1, timeoutInMillis);
        return messages == null ? null : messages.stream().findFirst().get();
    }

    @Override
    public Collection<NatsReceiveMessage> receive(int batchsize, long timeoutInMillis)
            throws NatsClientException, InterruptedException {
        List<Message> messages = subscription.fetch(batchsize, Duration.ofMillis(timeoutInMillis));
        Collection<NatsReceiveMessage> natsReceiveMessages = Lists.newArrayList();
        if (messages == null || messages.isEmpty()) {
            logger.info("Pull request timeout ({}} millis), no message found", timeoutInMillis);
            return null;
        }
        for (Message message : messages) {
            Object object = deserializer.deserialize(subject, message.getData());
            natsReceiveMessages.add(new NatsReceiveMessageImpl(message , object));
        }
        return natsReceiveMessages;
    }

    private Map<String, String> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + subject);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, ConfigurationProvider.getString(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY));
        config.put(SerdeConfig.REGISTRY_URL, ConfigurationProvider.getString(SerdeConfig.REGISTRY_URL));
        config.put(SerdeConfig.USE_ID, ConfigurationProvider.getString(SerdeConfig.USE_ID));
        config.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, ConfigurationProvider.getString(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER));
        return config;
    }
}
