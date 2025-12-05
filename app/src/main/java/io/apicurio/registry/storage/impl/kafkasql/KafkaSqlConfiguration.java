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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.apicurio.registry.utils.CollectionsUtil.toMap;

@ApplicationScoped
public class KafkaSqlConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlConfiguration.class);

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
    @RegistryProperties(prefixes = "apicurio.kafkasql.topic", excluded = {"auto-create", "configuration-verification-override-enabled"})
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

        return props;
    }

    // === Snapshots topic and related configurations ===

    @ConfigProperty(name = "apicurio.kafkasql.snapshots.topic", defaultValue = "kafkasql-snapshots")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql snapshots topic name", registryAvailableSince = "3.0.0")
    @Getter
    String snapshotsTopic;

    @Inject
    @RegistryProperties(prefixes = "apicurio.kafkasql.snapshots.topic")
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
    @RegistryProperties(prefixes = "apicurio.events.kafka.topic")
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

        // Events topic is configured with a single partition to guarantee total ordering of all events.
        // This ensures consumers see events in the exact order they occurred. If per-aggregate ordering
        // is sufficient (rather than global ordering), multiple partitions could be supported by removing
        // the explicit partition assignment in KafkaSqlEventsProcessor and using the aggregateId as the
        // partition key. This would enable better scalability while maintaining ordering per aggregate.
        props.put(TOPIC_PARTITIONS_CONFIG, "1");
        props.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
        props.putIfAbsent(TopicConfig.RETENTION_MS_CONFIG, "-1");
        props.putIfAbsent(TopicConfig.RETENTION_BYTES_CONFIG, "-1");

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
    @RegistryProperties(prefixes = {"apicurio.kafka.common", "apicurio.kafkasql.consumer"}, defaults = {"ssl.endpoint.identification.algorithm="})
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

        tryToConfigureClientSecurity(props);

        return props;
    }

    // === Producer configurations ===

    @Inject
    @RegistryProperties(prefixes = {"apicurio.kafka.common", "apicurio.kafkasql.producer"}, defaults = {"ssl.endpoint.identification.algorithm="})
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

        tryToConfigureClientSecurity(props);

        return props;
    }

    // === Admin client configurations ===

    @Inject
    @RegistryProperties(prefixes = {"apicurio.kafka.common", "apicurio.kafkasql.admin"}, defaults = {"ssl.endpoint.identification.algorithm="})
    Properties adminProperties;

    public Map<String, String> getAdminProperties() {
        // Properties are prone to bugs - they allow adding non-string values, but getProperty returns only strings (and null if the value is not a String).
        // So we convert them as soon as possible.
        var props = toMap(adminProperties);

        props.putIfAbsent(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.putIfAbsent(AdminClientConfig.CLIENT_ID_CONFIG, "apicurio-admin-" + UUID.randomUUID());

        tryToConfigureClientSecurity(props);

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

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.truststore.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl truststore password")
    Optional<String> trustStorePassword;

    /**
     * @deprecated Use apicurio.kafkasql.security.ssl.truststore.password instead. This property will be removed in a future version.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    @ConfigProperty(name = "apicurio.kafkasql.ssl.truststore.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl truststore password (deprecated, use apicurio.kafkasql.security.ssl.truststore.password)")
    Optional<String> trustStorePasswordDeprecated;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.keystore.location")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore location")
    Optional<String> keyStoreLocation;

    /**
     * @deprecated Use apicurio.kafkasql.security.ssl.keystore.location instead. This property will be removed in a future version.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.location")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore location (deprecated, use apicurio.kafkasql.security.ssl.keystore.location)")
    Optional<String> keyStoreLocationDeprecated;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.keystore.type")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore type")
    Optional<String> keyStoreType;

    /**
     * @deprecated Use apicurio.kafkasql.security.ssl.keystore.type instead. This property will be removed in a future version.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.type")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore type (deprecated, use apicurio.kafkasql.security.ssl.keystore.type)")
    Optional<String> keyStoreTypeDeprecated;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.keystore.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore password")
    Optional<String> keyStorePassword;

    /**
     * @deprecated Use apicurio.kafkasql.security.ssl.keystore.password instead. This property will be removed in a future version.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    @ConfigProperty(name = "apicurio.kafkasql.ssl.keystore.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl keystore password (deprecated, use apicurio.kafkasql.security.ssl.keystore.password)")
    Optional<String> keyStorePasswordDeprecated;

    @ConfigProperty(name = "apicurio.kafkasql.security.ssl.key.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl key password")
    Optional<String> keyPassword;

    /**
     * @deprecated Use apicurio.kafkasql.security.ssl.key.password instead. This property will be removed in a future version.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    @ConfigProperty(name = "apicurio.kafkasql.ssl.key.password")
    @Info(category = CATEGORY_STORAGE, description = "Kafka sql storage ssl key password (deprecated, use apicurio.kafkasql.security.ssl.key.password)")
    Optional<String> keyPasswordDeprecated;

    private void tryToConfigureClientSecurity(Map<String, String> props) {
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
        // Use new property names, falling back to deprecated ones if new ones are not set
        Optional<String> effectiveTrustStorePassword = trustStorePassword.or(() -> {
            if (trustStorePasswordDeprecated.isPresent()) {
                log.warn("Configuration property 'apicurio.kafkasql.ssl.truststore.password' is deprecated and will be removed in a future version. "
                        + "Please migrate to 'apicurio.kafkasql.security.ssl.truststore.password'");
            }
            return trustStorePasswordDeprecated;
        });

        if (trustStoreLocation.isPresent() && effectiveTrustStorePassword.isPresent() && trustStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, trustStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, effectiveTrustStorePassword.get());
        }

        // Finally, try to configure the keystore, if specified
        // Use new property names, falling back to deprecated ones if new ones are not set
        Optional<String> effectiveKeyStoreLocation = keyStoreLocation.or(() -> {
            if (keyStoreLocationDeprecated.isPresent()) {
                log.warn("Configuration property 'apicurio.kafkasql.ssl.keystore.location' is deprecated and will be removed in a future version. "
                        + "Please migrate to 'apicurio.kafkasql.security.ssl.keystore.location'");
            }
            return keyStoreLocationDeprecated;
        });

        Optional<String> effectiveKeyStoreType = keyStoreType.or(() -> {
            if (keyStoreTypeDeprecated.isPresent()) {
                log.warn("Configuration property 'apicurio.kafkasql.ssl.keystore.type' is deprecated and will be removed in a future version. "
                        + "Please migrate to 'apicurio.kafkasql.security.ssl.keystore.type'");
            }
            return keyStoreTypeDeprecated;
        });

        Optional<String> effectiveKeyStorePassword = keyStorePassword.or(() -> {
            if (keyStorePasswordDeprecated.isPresent()) {
                log.warn("Configuration property 'apicurio.kafkasql.ssl.keystore.password' is deprecated and will be removed in a future version. "
                        + "Please migrate to 'apicurio.kafkasql.security.ssl.keystore.password'");
            }
            return keyStorePasswordDeprecated;
        });

        Optional<String> effectiveKeyPassword = keyPassword.or(() -> {
            if (keyPasswordDeprecated.isPresent()) {
                log.warn("Configuration property 'apicurio.kafkasql.ssl.key.password' is deprecated and will be removed in a future version. "
                        + "Please migrate to 'apicurio.kafkasql.security.ssl.key.password'");
            }
            return keyPasswordDeprecated;
        });

        if (effectiveKeyStoreLocation.isPresent() && effectiveKeyStorePassword.isPresent() && effectiveKeyStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, effectiveKeyStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, effectiveKeyStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, effectiveKeyStorePassword.get());
            effectiveKeyPassword.ifPresent(s -> props.putIfAbsent(SslConfigs.SSL_KEY_PASSWORD_CONFIG, s));
        }
    }
}
