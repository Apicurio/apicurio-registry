package io.apicurio.registry.serde.nats.client;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.nats.client.config.ConfigurationProvider;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumer;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumerImpl;
import io.apicurio.registry.serde.nats.client.streaming.producers.NatsProducer;
import io.apicurio.registry.serde.nats.client.streaming.producers.NatsProducerImpl;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PullSubscribeOptions;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class NatsClientManager {

    private Connection connection;

    private Map<String, NatsProducer> cachedProducers = new ConcurrentHashMap<>();
    private Map<String, NatsConsumer<?>> cachedConsumers = new ConcurrentHashMap<>();


    public NatsClientManager(Connection connection) {
        this.connection = connection;
    }


    public NatsProducer<?> getProducer(String subject) {
        return cachedProducers.computeIfAbsent(subject, _ignored -> {
            try {
                return new NatsProducerImpl<>(connection, subject, getProducerConfig(subject));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }


    public NatsConsumer<?> getConsumer(String subject, PullSubscribeOptions subscribeOptions) {
        return cachedConsumers.computeIfAbsent(subject, _ignored -> {
            try {
                return new NatsConsumerImpl<>(connection, subject, subscribeOptions, getConsumerConfig(subject));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }


    public static Connection getDefaultConnection() throws IOException {
        Options.Builder builder = new Options.Builder();
        Properties natsProperties = ConfigurationProvider.getProperties();
        if (natsProperties != null) {
            builder = builder.properties(natsProperties);
        }
        try {
            Options options = builder.maxReconnects(0).build();
            return Nats.connect(options);
        } catch (Exception e) {
            throw new IOException("Cannot connect to NATS server.", e);
        }
    }


    public static Properties getConsumerConfig(String subject) {
        var config = new Properties();
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "Consumer-" + subject);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, ConfigurationProvider.getString(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY));
        config.put(SerdeConfig.REGISTRY_URL, ConfigurationProvider.getString(SerdeConfig.REGISTRY_URL));
        config.put(SerdeConfig.USE_ID, ConfigurationProvider.getString(SerdeConfig.USE_ID));
        config.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, ConfigurationProvider.getString(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER));
        return config;
    }


    public static Properties getProducerConfig(String subject) { //Making it public, for clients to add values
        var config = new Properties();
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + subject);
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, ConfigurationProvider.getString(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY));
        config.put(SerdeConfig.REGISTRY_URL, ConfigurationProvider.getString(SerdeConfig.REGISTRY_URL));
        config.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, ConfigurationProvider.getString(SerdeConfig.AUTO_REGISTER_ARTIFACT));
        config.put(SerdeConfig.USE_ID, ConfigurationProvider.getString(SerdeConfig.USE_ID));
        return config;
    }
}
