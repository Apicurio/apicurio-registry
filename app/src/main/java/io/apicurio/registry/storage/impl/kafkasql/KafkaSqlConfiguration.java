package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlPartitioner;
import io.apicurio.registry.utils.RegistryProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.TopicConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.apicurio.registry.utils.CollectionsUtil.toMap;

@ApplicationScoped
public class KafkaSqlConfiguration {

    /**
     * Configure number of partitions for Kafka topics created by Apicurio Registry.
     * If not provided, a value specified by the Kafka cluster configuration 'num.partitions' is used (except for the events topic, see below).
     */
    public static final String TOPIC_PARTITIONS_CONFIG = "partitions";

    /**
     * Configure the replication factor (number of replicas) for Kafka topics created by Apicurio Registry.
     * If not provided, a sensible value is computed.
     */
    public static final String TOPIC_REPLICATION_FACTOR_CONFIG = "replication.factor";

    // === Common configurations ===

    @ConfigProperty(name = "apicurio.kafkasql.bootstrap.servers")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage bootstrap servers")
    @Getter
    String bootstrapServers;

    @ConfigProperty(name = "apicurio.kafkasql.topic.auto-create", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage topic auto create")
    @Getter
    Boolean topicAutoCreate;

    @ConfigProperty(name = "apicurio.kafkasql.topic-configuration-verification-override-enabled", defaultValue = "false")
    @Info(category = CATEGORY_STORAGE, description = """
            When using KafkaSQL storage, Apicurio Registry verifies that the topic configuration will not cause accidental data loss or corruption. \
            Setting this property to true will partially disable this verification for *all* Apicurio Registry topics. \
            Specifically, 'retention.ms=-1' is not enforced to support automatic cleanup when Apicurio Registry snapshotting feature is used. \
            In this case, snapshots have to be made more frequently than messages are deleted. \
            IMPORTANT: We might change which topics and which verification checks are affected by this configuration property in the future.""", registryAvailableSince = "3.1.3")
    @Getter
    boolean topicConfigurationVerificationOverrideEnabled;

    @ConfigProperty(name = "apicurio.kafkasql.coordinator.response-timeout", defaultValue = "30000")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage coordinator response timeout in milliseconds")
    Integer responseTimeout;

    public Duration getResponseTimeout() {
        return Duration.ofMillis(responseTimeout);
    }

    // === Journal topic configurations ===

    @ConfigProperty(name = "apicurio.kafkasql.topic", defaultValue = "kafkasql-journal")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage topic name")
    @Getter
    String topic;

    @Inject
    @RegistryProperties(value = "apicurio.kafkasql.topic")
    @Info(category = CATEGORY_STORAGE, description = """
            Kafka sql storage topic properties. \
            There are two optional Registry-specific configuration properties: 'partitions' and 'replication.factor'.""")
    Properties topicProperties;

    public Map<String, String> getTopicProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(topicProperties);

        // Journal topic supports multiple partitions, let's use a cluster-default value if not specified.
        props.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
        props.putIfAbsent(TopicConfig.RETENTION_MS_CONFIG, "-1");
        props.putIfAbsent(TopicConfig.RETENTION_BYTES_CONFIG, "-1");

        tryToConfigureSecurity(props);

        return props;
    }

    // === Snapshots topic and related configurations ===

    @ConfigProperty(name = "apicurio.kafkasql.snapshots.topic", defaultValue = "kafkasql-snapshots")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql snapshots topic name", registryAvailableSince = "3.0.0")
    @Getter
    String snapshotsTopic;

    @Inject
    @RegistryProperties(value = "apicurio.kafkasql.snapshots.topic")
    @Info(category = CATEGORY_STORAGE, description = """
            Kafka sql snapshots topic properties. \
            There are two optional Registry-specific configuration properties: 'partitions' and 'replication.factor'. \
            IMPORTANT: As a temporary compatibility measure, configuration properties for this topic are also inherited from 'apicurio.kafkasql.topic' \
            unless explicitly overridden by this property. This will be removed in a next minor version.\
            """, registryAvailableSince = "3.1.3")
    Properties snapshotTopicProperties;

    public Map<String, String> getSnapshotTopicProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(snapshotTopicProperties);

        // Snapshots topic supports multiple partitions since snapshots are ordered by timestamp after reading to find out the latest snapshot.
        // Let's use a cluster-default value if not specified.
        props.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
        props.putIfAbsent(TopicConfig.RETENTION_MS_CONFIG, "-1");
        props.putIfAbsent(TopicConfig.RETENTION_BYTES_CONFIG, "-1");

        tryToConfigureSecurity(props);
        getTopicProperties().forEach(props::putIfAbsent);
        return props;
    }

    @ConfigProperty(name = "apicurio.kafkasql.snapshot.every.seconds", defaultValue = "86400s")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql journal topic snapshot every", registryAvailableSince = "3.0.0")
    @Getter
    String snapshotEvery;

    @ConfigProperty(name = "apicurio.storage.snapshot.location", defaultValue = "./")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql snapshots store location", registryAvailableSince = "3.0.0")
    @Getter
    String snapshotStoreLocation;

    // === Events topic and related configurations ===

    @ConfigProperty(name = "apicurio.events.kafka.topic", defaultValue = "registry-events")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage events topic", registryAvailableSince = "3.0.1")
    @Getter
    String eventsTopic;

    @Inject
    @RegistryProperties(value = "apicurio.events.kafka.topic")
    @Info(category = CATEGORY_STORAGE, description = """
            Kafka sql events topic properties. \
            There is an optional Registry-specific configuration property: 'replication.factor'. \
            IMPORTANT: As a temporary compatibility measure, configuration properties for this topic are also inherited from 'apicurio.kafkasql.topic' \
            unless explicitly overridden by this property. This will be removed in a next minor version.\
            """, registryAvailableSince = "3.1.3")
    Properties eventsTopicProperties;

    public Map<String, String> getEventsTopicProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(eventsTopicProperties);

        // TODO: Check if the events topic can support multiple partitions. Currently all events are explicitly sent to partition 0.
        props.put(TOPIC_PARTITIONS_CONFIG, "1");
        props.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
        props.putIfAbsent(TopicConfig.RETENTION_MS_CONFIG, "-1");
        props.putIfAbsent(TopicConfig.RETENTION_BYTES_CONFIG, "-1");

        tryToConfigureSecurity(props);
        getTopicProperties().forEach(props::putIfAbsent);
        return props;
    }

    // === Consumer configurations ===

    @ConfigProperty(name = "apicurio.kafkasql.consumer.poll.timeout", defaultValue = "5000")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage consumer poll timeout in milliseconds")
    Integer pollTimeout;

    public Duration getPollTimeout() {
        return Duration.ofMillis(pollTimeout);
    }

    @ConfigProperty(name = "apicurio.kafkasql.consumer.group-prefix", defaultValue = "apicurio-")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage prefix for consumer group name")
    @Getter
    String groupPrefix;

    @Inject
    @RegistryProperties(value = {"apicurio.kafka.common", "apicurio.kafkasql.consumer"}, empties = {"ssl.endpoint.identification.algorithm="})
    Properties consumerProperties;

    public Map<String, String> getConsumerProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(consumerProperties);

        props.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.putIfAbsent(ConsumerConfig.CLIENT_ID_CONFIG, "apicurio-consumer-" + UUID.randomUUID());
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, getGroupPrefix() + UUID.randomUUID());

        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        tryToConfigureSecurity(props);

        return props;
    }

    // === Producer configurations ===

    @Inject
    @RegistryProperties(value = {"apicurio.kafka.common", "apicurio.kafkasql.producer"}, empties = {"ssl.endpoint.identification.algorithm="})
    Properties producerProperties;

    public Map<String, String> getProducerProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(producerProperties);

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "apicurio-producer-" + UUID.randomUUID());

        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, "10");
        props.putIfAbsent(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaSqlPartitioner.class.getCanonicalName());

        tryToConfigureSecurity(props);

        return props;
    }

    // === Admin client configurations ===

    @Inject
    @RegistryProperties(value = {"apicurio.kafka.common", "apicurio.kafkasql.admin"}, empties = {"ssl.endpoint.identification.algorithm="})
    Properties adminProperties;

    public Map<String, String> getAdminProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(adminProperties);

        props.putIfAbsent(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.putIfAbsent(AdminClientConfig.CLIENT_ID_CONFIG, "apicurio-admin-" + UUID.randomUUID());

        tryToConfigureSecurity(props);

        return props;
    }

    // === Security configurations ===

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.enabled", defaultValue = "false")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage sasl enabled")
    boolean saslEnabled;

    @ConfigProperty(name = "apicurio.kafkasql.security.protocol", defaultValue = "")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage security protocol")
    Optional<String> protocol;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.mechanism", defaultValue = "")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage sasl mechanism")
    String saslMechanism;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.client-id", defaultValue = "")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage sasl client identifier")
    String clientId;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.client-secret", defaultValue = "")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage sasl client secret")
    String clientSecret;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.token.endpoint", defaultValue = "")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage sasl token endpoint")
    String tokenEndpoint;

    @ConfigProperty(name = "apicurio.kafkasql.security.sasl.login.callback.handler.class", defaultValue = "")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage sasl login callback handler")
    String loginCallbackHandler;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.truststore.location")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl truststore location")
    Optional<String> trustStoreLocation;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.truststore.type")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl truststore type")
    Optional<String> trustStoreType;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.truststore.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl truststore password")
    Optional<String> trustStorePassword;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.location")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore location")
    Optional<String> keyStoreLocation;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.type")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore type")
    Optional<String> keyStoreType;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore password")
    Optional<String> keyStorePassword;

    @ConfigProperty(name = "apicurio.kafkasql.ssl.key.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl key password")
    Optional<String> keyPassword;

    private void tryToConfigureSecurity(Map<String, String> props) {
        protocol.ifPresent(s -> props.putIfAbsent("security.protocol", s));

        // Try to configure sasl for authentication
        if (saslEnabled) {
            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG,
                    String.format(
                            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required "
                                    + "  oauth.client.id=\"%s\" " + "  oauth.client.secret=\"%s\" "
                                    + "  oauth.token.endpoint.uri=\"%s\" ;",
                            clientId, clientSecret, tokenEndpoint));
            props.putIfAbsent(SaslConfigs.SASL_MECHANISM, saslMechanism);
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, loginCallbackHandler);
        }
        // Try to configure the trustStore, if specified
        if (trustStoreLocation.isPresent() && trustStorePassword.isPresent() && trustStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, trustStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStorePassword.get());
        }
        // Finally, try to configure the keystore, if specified
        if (keyStoreLocation.isPresent() && keyStorePassword.isPresent() && keyStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keyStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStorePassword.get());
            keyPassword.ifPresent(s -> props.putIfAbsent(SslConfigs.SSL_KEY_PASSWORD_CONFIG, s));
        }
    }
}
