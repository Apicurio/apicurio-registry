package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlPartitioner;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.utils.RegistryProperties;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@ApplicationScoped
public class KafkaSqlFactory {

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.bootstrap.servers")
    @Info(category = "storage", description = "Kafka sql storage bootstrap servers")
    String bootstrapServers;

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.topic", defaultValue = "kafkasql-journal")
    @Info(category = "storage", description = "Kafka sql storage topic name")
    String topic;

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.snapshots.topic", defaultValue = "kafkasql-snapshots")
    @Info(category = "storage", description = "Kafka sql storage topic name")
    String snapshotsTopic;

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.snapshot.every.seconds", defaultValue = "86400s")
    @Info(category = "storage", description = "Kafka sql journal topic snapshot every")
    String snapshotEvery;

    @Inject
    @ConfigProperty(name = "apicurio.storage.snapshot.location", defaultValue = "86400s")
    @Info(category = "storage", description = "Kafka sql snapshots store location")
    String snapshotStoreLocation;

    @Inject
    @RegistryProperties(value = "apicurio.kafkasql.topic")
    @Info(category = "storage", description = "Kafka sql storage topic properties")
    Properties topicProperties;

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.topic.auto-create", defaultValue = "true")
    @Info(category = "storage", description = "Kafka sql storage topic auto create")
    Boolean topicAutoCreate;

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.consumer.poll.timeout", defaultValue = "1000")
    @Info(category = "storage", description = "Kafka sql storage consumer poll timeout")
    Integer pollTimeout;

    @Inject
    @ConfigProperty(name = "apicurio.kafkasql.coordinator.response-timeout", defaultValue = "30000")
    @Info(category = "storage", description = "Kafka sql storage coordinator response timeout")
    Integer responseTimeout;

    @Inject
    @RegistryProperties(
            value = { "apicurio.kafka.common", "apicurio.kafkasql.producer" },
            empties = { "ssl.endpoint.identification.algorithm=" }
    )
    Properties producerProperties;

    @Inject
    @RegistryProperties(
            value = { "apicurio.kafka.common", "apicurio.kafkasql.consumer" },
            empties = { "ssl.endpoint.identification.algorithm=" }
    )
    Properties consumerProperties;

    @Inject
    @RegistryProperties(
            value = { "apicurio.kafka.common", "apicurio.kafkasql.admin" },
            empties = { "ssl.endpoint.identification.algorithm=" }
    )
    Properties adminProperties;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.enabled", defaultValue = "false")
    @Info(category = "storage", description = "Kafka sql storage sasl enabled")
    boolean saslEnabled;

    @ConfigProperty(name = "apicurio.kafkasql.security.protocol", defaultValue = "")
    @Info(category = "storage", description = "Kafka sql storage security protocol")
    Optional<String> protocol;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.mechanism", defaultValue = "")
    @Info(category = "storage", description = "Kafka sql storage sasl mechanism")
    String saslMechanism;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.client-id", defaultValue = "")
    @Info(category = "storage", description = "Kafka sql storage sasl client identifier")
    String clientId;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.client-secret", defaultValue = "")
    @Info(category = "storage", description = "Kafka sql storage sasl client secret")
    String clientSecret;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.token.endpoint", defaultValue = "")
    @Info(category = "storage", description = "Kafka sql storage sasl token endpoint")
    String tokenEndpoint;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.login.callback.handler.class", defaultValue = "")
    @Info(category = "storage", description = "Kafka sql storage sasl login callback handler")
    String loginCallbackHandler;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.truststore.location")
    @Info(category = "storage", description = "Kafka sql storage ssl truststore location")
    Optional<String> trustStoreLocation;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.truststore.type")
    @Info(category = "storage", description = "Kafka sql storage ssl truststore type")
    Optional<String> trustStoreType;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.truststore.password")
    @Info(category = "storage", description = "Kafka sql storage ssl truststore password")
    Optional<String> trustStorePassword;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.location")
    @Info(category = "storage", description = "Kafka sql storage ssl keystore location")
    Optional<String> keyStoreLocation;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.type")
    @Info(category = "storage", description = "Kafka sql storage ssl keystore type")
    Optional<String> keyStoreType;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.password")
    @Info(category = "storage", description = "Kafka sql storage ssl keystore password")
    Optional<String> keyStorePassword;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.key.password")
    @Info(category = "storage", description = "Kafka sql storage ssl key password")
    Optional<String> keyPassword;


    @ApplicationScoped
    @Produces
    public KafkaSqlConfiguration createConfiguration() {

        return new KafkaSqlConfiguration() {
            @Override
            public String bootstrapServers() {
                return bootstrapServers;
            }

            @Override
            public String topic() {
                return topic;
            }

            @Override
            public String snapshotsTopic() {
                return snapshotsTopic;
            }

            @Override
            public String snapshotEvery() {
                return snapshotEvery;
            }

            @Override
            public String snapshotLocation() {
                return snapshotStoreLocation;
            }

            @Override
            public Properties topicProperties() {
                return topicProperties;
            }

            @Override
            public boolean isTopicAutoCreate() {
                return topicAutoCreate;
            }

            @Override
            public Integer pollTimeout() {
                return pollTimeout;
            }

            @Override
            public Integer responseTimeout() {
                return responseTimeout;
            }

            @Override
            public Properties producerProperties() {
                return producerProperties;
            }

            @Override
            public Properties consumerProperties() {
                return consumerProperties;
            }

            @Override
            public Properties adminProperties() {
                tryToConfigureSecurity(adminProperties);
                return adminProperties;
            }
        };
    }

    /**
     * Creates the Kafka journal producer.
     */
    @ApplicationScoped
    @Produces
    @Named("KafkaSqlJournalProducer")
    public ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaJournalProducer() {
        Properties props = (Properties) producerProperties.clone();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + UUID.randomUUID().toString());
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.putIfAbsent(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaSqlPartitioner.class);

        tryToConfigureSecurity(props);

        // Create the Kafka producer
        KafkaSqlKeySerializer keySerializer = new KafkaSqlKeySerializer();
        KafkaSqlValueSerializer valueSerializer = new KafkaSqlValueSerializer();
        return new AsyncProducer<>(props, keySerializer, valueSerializer);
    }

    /**
     * Creates the Kafka journal consumer.
     */
    @ApplicationScoped
    @Produces
    @Named("KafkaSqlJournalConsumer")
    public KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaJournalConsumer() {
        Properties props = (Properties) consumerProperties.clone();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        tryToConfigureSecurity(props);

        // Create the Kafka Consumer
        KafkaSqlKeyDeserializer keyDeserializer = new KafkaSqlKeyDeserializer();
        KafkaSqlValueDeserializer valueDeserializer = new KafkaSqlValueDeserializer();
        return new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
    }

    /**
     * Creates the Kafka data producer.
     */
    @ApplicationScoped
    @Produces
    @Named("KafkaSqlSnapshotsProducer")
    public ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaSnapshotsProducer() {
        Properties props = (Properties) producerProperties.clone();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + UUID.randomUUID().toString());
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.putIfAbsent(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaSqlPartitioner.class);

        tryToConfigureSecurity(props);

        // Create the Kafka producer
        KafkaSqlKeySerializer keySerializer = new KafkaSqlKeySerializer();
        KafkaSqlValueSerializer valueSerializer = new KafkaSqlValueSerializer();
        return new AsyncProducer<>(props, keySerializer, valueSerializer);
    }

    /**
     * Creates the Kafka journal consumer.
     */
    @ApplicationScoped
    @Produces
    @Named("KafkaSqlSnapshotsConsumer")
    public KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> createKafkaSnapshotsConsumer() {
        Properties props = (Properties) consumerProperties.clone();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        tryToConfigureSecurity(props);

        // Create the Kafka Consumer
        KafkaSqlKeyDeserializer keyDeserializer = new KafkaSqlKeyDeserializer();
        KafkaSqlValueDeserializer valueDeserializer = new KafkaSqlValueDeserializer();
        return new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
    }

    private void tryToConfigureSecurity(Properties props) {
        protocol.ifPresent(s -> props.putIfAbsent("security.protocol", s));

        //Try to configure sasl for authentication
        if (saslEnabled) {
            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG, String.format("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                    "  oauth.client.id=\"%s\" " +
                    "  oauth.client.secret=\"%s\" " +
                    "  oauth.token.endpoint.uri=\"%s\" ;", clientId, clientSecret, tokenEndpoint));
            props.putIfAbsent(SaslConfigs.SASL_MECHANISM, saslMechanism);
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, loginCallbackHandler);
        }
        //Try to configure the trustStore, if specified
        if (trustStoreLocation.isPresent() && trustStorePassword.isPresent() && trustStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, trustStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStorePassword.get());
        }
        //Finally, try to configure the keystore, if specified
        if (keyStoreLocation.isPresent() && keyStorePassword.isPresent() && keyStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keyStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStorePassword.get());
            keyPassword.ifPresent(s -> props.putIfAbsent(SslConfigs.SSL_KEY_PASSWORD_CONFIG, s));
        }
    }
}
