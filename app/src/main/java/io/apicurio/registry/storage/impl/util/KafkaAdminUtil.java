package io.apicurio.registry.storage.impl.util;

import io.apicurio.registry.exception.RuntimeAssertionFailedException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlConfiguration;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlFactory.KafkaAdminClient;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlFactory.KafkaSqlVerificationJournalConsumer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.v2compat.BootstrapKey;
import io.apicurio.registry.storage.impl.kafkasql.v2compat.UpgraderKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlConfiguration.TOPIC_PARTITIONS_CONFIG;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlConfiguration.TOPIC_REPLICATION_FACTOR_CONFIG;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter.BOOTSTRAP_MESSAGE_TYPE;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter.MESSAGE_TYPE_HEADER;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter.REQUEST_ID_HEADER;
import static io.apicurio.registry.utils.CollectionsUtil.copy;
import static io.apicurio.registry.utils.ConcurrentUtil.blockOn;
import static io.apicurio.registry.utils.kafka.KafkaUtil.toJavaFuture;
import static java.lang.Math.max;
import static java.lang.String.valueOf;
import static java.util.Collections.singleton;

@ApplicationScoped
public class KafkaAdminUtil {

    @Inject
    Logger log;

    @Inject
    KafkaAdminClient adminClient;

    @Inject
    KafkaSqlVerificationJournalConsumer verificationConsumer;

    @Inject
    KafkaSqlConfiguration configuration;

    /**
     * NOTE: This is a bit slower than the previous implementation that created multiple topics concurrently.
     * But I think better readability is worth it, since we create topics very rarely.
     * <p>
     * NOTE: Close the admin client after use.
     */
    public CompletableFuture<Boolean> createTopicIfDoesNotExistAsync(String topic, Map<String, String> properties) {
        final var props = copy(properties);
        // Get the list of existing topics
        return toJavaFuture(adminClient.get().listTopics().names())
                .thenCompose(topics -> {
                    if (topics.contains(topic)) {
                        return CompletableFuture.completedFuture(false);
                    } else {
                        // Get the cluster nodes to determine replication factor
                        return toJavaFuture(adminClient.get().describeCluster().nodes())
                                .thenApply(nodes -> {

                                    var partitions = Optional.ofNullable(props.get(TOPIC_PARTITIONS_CONFIG))
                                            .map(Integer::valueOf);
                                    // This property is not supported by Kafka and will throw an exception if set.
                                    partitions.ifPresent(_ignored -> props.remove(TOPIC_PARTITIONS_CONFIG));

                                    var replicationFactor = Optional.ofNullable(props.get(TOPIC_REPLICATION_FACTOR_CONFIG))
                                            .map(Short::valueOf)
                                            .or(() -> Optional.of((short) Math.min(3, nodes.size())));

                                    props.putIfAbsent(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, valueOf(max(replicationFactor.get() - 1, 1)));

                                    return new NewTopic(topic, partitions, replicationFactor).configs(props);

                                })
                                .thenCompose(newTopic -> toJavaFuture(adminClient.get().createTopics(List.of(newTopic)).all()))
                                .thenApply(_ignored -> true);
                    }
                });
    }

    /**
     * Prevents potential data loss in case the topics are misconfigured.
     * <p>
     * 1. We check that the topic messages are never deleted or compacted. KafkaSQL should be compaction-safe,
     * but in a way that compaction has no effect, so we might as well require it to be turned off.
     * <p>
     * We might relax this requirement if snapshotting is enabled, but the snapshots would have to be done automatically,
     * and there is never a guarantee that a snapshot has been made before the messages are deleted based on retention (e.g. Registry could be turned off).
     * <p>
     * An alternative approach would be to delete messages after a snapshot is made, but that should be left to an admin.
     * <p>
     * 2. We check that the topic does not contain v2 messages, which could happen during an incorrect migration.
     * <p>
     * NOTE: We should run this even for topics we create as an extra check.
     *
     * @param topic
     */
    public void verifyTopicConfiguration(String topic) {
        var key = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        // includeSynonyms should ensure we get effective values (including default configs).
        var options = new DescribeConfigsOptions().includeSynonyms(true);
        blockOn(toJavaFuture(adminClient.get().describeConfigs(singleton(key), options).all())
                .thenAccept(d -> {
                    var config = d.get(key);
                    assertConfiguration(topic, config, TopicConfig.CLEANUP_POLICY_CONFIG, "delete"::equals, """
                            Topic must not have '%s=%s'. While Apicurio Registry will work with topic compaction, it will not have any effect. \
                            Use '%s=delete', '%s=-1', and '%s=-1' to disable automatic deletion of messages, and perform any cleanup manually after a backup has been made\
                            """.formatted(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT,
                            TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.RETENTION_MS_CONFIG, TopicConfig.RETENTION_BYTES_CONFIG));

                    if (!topic.equals(configuration.getEventsTopic())) { // Events topic is allowed to use retention. If configured, old events should be eventually deleted.
                        if (!configuration.isTopicConfigurationVerificationOverrideEnabled()) {
                            assertConfiguration(topic, config, TopicConfig.RETENTION_MS_CONFIG, "-1"::equals,
                                    "Topic must have '%s=-1' and '%s=-1' to prevent accidental loss of data"
                                            .formatted(TopicConfig.RETENTION_MS_CONFIG, TopicConfig.RETENTION_BYTES_CONFIG));
                        }

                        assertConfiguration(topic, config, TopicConfig.RETENTION_BYTES_CONFIG, "-1"::equals,
                                "Topic must have '%s=-1' and '%s=-1' to prevent accidental loss of data"
                                        .formatted(TopicConfig.RETENTION_BYTES_CONFIG, TopicConfig.RETENTION_MS_CONFIG));
                    }
                }));
    }

    private static void assertConfiguration(String topic, Config config, String property, Predicate<String> condition, String errorMessage) {
        var value = config.get(property).value();
        if (!condition.test(value)) {
            var source = switch (config.get(property).source()) {
                case DYNAMIC_TOPIC_CONFIG -> "dynamic topic config that is configured for a specific topic";
                case DYNAMIC_BROKER_LOGGER_CONFIG ->
                        "dynamic broker logger config that is configured for a specific broker";
                case DYNAMIC_BROKER_CONFIG -> "dynamic broker config that is configured for a specific broker";
                case DYNAMIC_DEFAULT_BROKER_CONFIG ->
                        "dynamic broker config that is configured as default for all brokers in the cluster";
                case DYNAMIC_CLIENT_METRICS_CONFIG ->
                        "dynamic client metrics subscription config that is configured for all clients";
                case STATIC_BROKER_CONFIG ->
                        "static broker config provided as broker properties at start up (e.g. server.properties file)";
                case DEFAULT_CONFIG -> "built-in default configuration";
                case UNKNOWN ->
                        "an unknown source, e.g. in the ConfigEntry used for alter requests where source is not set";
            };
            throw new RuntimeAssertionFailedException("""
                    To prevent accidental loss of data, Apicurio Registry verifies that Kafka topics are configured correctly before starting. \
                    The following issue was found with topic '%s': %s. Effective configuration value is '%s=%s' and comes from %s.
                    """.formatted(topic, errorMessage, property, value, source));
        }
    }

    public void verifyJournalTopicContents() {

        var consumer = verificationConsumer.get();

        var topic = configuration.getTopic();
        consumer.subscribe(singleton(topic));

        // Wait for partition assignment:
        var assigned = consumer.assignment();
        while (assigned.isEmpty()) {
            consumer.poll(configuration.getPollTimeout());
            assigned = consumer.assignment();
            log.debug("Consumer was assigned {} partitions.", assigned.size());
        }

        final var MAX_MESSAGE_COUNT = 500;
        // For each assigned partition, seek to offset so that the number of messages is <= `MAX_MESSAGE_COUNT`:
        var endOffsets = consumer.endOffsets(assigned);
        for (var partition : assigned) {
            long endOffset = endOffsets.get(partition);
            long startOffset = max(0, endOffset - (MAX_MESSAGE_COUNT / assigned.size()));
            consumer.seek(partition, startOffset);
            log.debug("Consumer seeking partition {} to offset {} (end offset: {})", partition, startOffset, endOffset);
        }

        try (var v3KeyDeserializer = new KafkaSqlKeyDeserializer()) {
            try (var v2KeyDeserializer = new io.apicurio.registry.storage.impl.kafkasql.v2compat.KafkaSqlKeyDeserializer()) {

                long count = 0;
                boolean foundV2 = false;
                boolean foundV3 = false;
                boolean foundV2WithHeaders = false;
                boolean foundV3WithHeaders = false;
                boolean foundUnknownWithHeaders = false;

                while (true) { // We process a limited number of messages, so this will terminate.
                    var records = verificationConsumer.get().poll(configuration.getPollTimeout());
                    if (records.isEmpty()) {
                        break;
                    }
                    for (var record : records) {
                        count++;
                        // Check the headers:
                        //  - v2 uses "req"
                        //  - v3 uses both "req" and "mt"
                        // TODO: This might be enough, but only using headers feels less robust.
                        var headers = record.headers();
                        if (headers.headers(REQUEST_ID_HEADER).iterator().hasNext()) {
                            if (headers.headers(MESSAGE_TYPE_HEADER).iterator().hasNext()) {
                                foundV3WithHeaders = true;
                            } else {
                                foundV2WithHeaders = true;
                            }
                        } else {
                            foundUnknownWithHeaders = true;
                        }
                        // Check for v3 bootstrap message
                        try {
                            var key = v3KeyDeserializer.deserialize(record.topic(), record.key().get());
                            if (BOOTSTRAP_MESSAGE_TYPE.equals(key.getMessageType())) {
                                if (!foundV3) {
                                    foundV3 = true;
                                    log.debug("Found v3 bootstrap record in the {} journal topic.", record.topic());
                                }
                                continue;
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                        // Check for v2 bootstrap or upgrader message
                        try {
                            var key = v2KeyDeserializer.deserialize(record.topic(), record.key().get());
                            if (key instanceof BootstrapKey k) {
                                if (k.getBootstrapId() != null) {
                                    if (!foundV2) {
                                        foundV2 = true;
                                        log.warn("Found v2 bootstrap record in the {} journal topic.", record.topic());
                                    }
                                }
                            } else if (key instanceof UpgraderKey k) {
                                if ("compact".equals(k.getEitherUUID())) {
                                    if (!foundV2) {
                                        foundV2 = true;
                                        log.warn("Found v2 upgrader record in the {} journal topic.", record.topic());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }

                log.debug("""
                        Scanned {} messages from the end of the KafkaSQL journal topic '{}' to verify its contents. \
                        Found v2 messages: {} (with headers: {}), v3 messages: {} (with headers: {}), \
                        unknown messages with headers: {}.\
                        """, count, topic, foundV2, foundV2WithHeaders, foundV3, foundV3WithHeaders, foundUnknownWithHeaders);

                if (foundUnknownWithHeaders) {
                    // Do not fail the startup in this case, since Registry should handle unknown messages.
                    log.warn("""
                            The KafkaSQL journal topic '{}' contains unknown records. \
                            The topic must only be used by Apicurio Registry. \
                            This might indicate that the topic has been used by other applications. \
                            Backup now as a precaution. \
                            You can ignore this message on subsequent restarts if there are no further issues, \
                            but we suggest you create a new topic for Apicurio Registry and then restore from the backup.\
                            """, topic);
                }
                if (foundV2 && foundV3) {
                    // Do not fail the startup in this case, because the harm has already been done,
                    // and user must be able to create a backup (export).
                    log.error("""
                            The KafkaSQL journal topic '{}' contains records from both Apicurio Registry v2 and v3. \
                            The topic must not be reused by both major versions of Apicurio Registry. \
                            This might indicate that the topic has been corrupted. \
                            Backup now as a precaution, and review the migration guide documentation. \
                            We suggest you must create a new topic for Apicurio Registry v3 and then restore from the backup.\
                            """, topic);
                    return;
                }
                if ((foundV2 || foundV2WithHeaders) && (foundV3 || foundV3WithHeaders)) {
                    // Same as above, but we're less confident.
                    log.warn("""
                            The KafkaSQL journal topic '{}' might contain records from both Apicurio Registry v2 and v3. \
                            The topic must not be reused by both major versions of Apicurio Registry. \
                            This might indicate that the topic has been corrupted. \
                            Backup now as a precaution, and review the migration guide documentation. \
                            You can ignore this message on subsequent restarts if there are no further issues, \
                            but we suggest you create a new topic for Apicurio Registry and then restore from the backup.\
                            """, topic);
                    return;
                }
                if (foundV2 || foundV2WithHeaders) {
                    throw new RuntimeAssertionFailedException("""
                            The KafkaSQL journal topic '%s' contains records from Apicurio Registry v2. \
                            The topic must not be reused by both major versions of Apicurio Registry. \
                            To prevent data loss, Apicurio Registry v3 refuses to start when v2 messages are detected in the journal topic. \
                            Backup now and review the migration guide documentation.\
                            """.formatted(topic));
                }
                if (count == 0) {
                    log.debug("""
                            The KafkaSQL journal topic '{}' is empty and can be used to store Apicurio Registry v3 data.\
                            """, topic);
                } else {
                    log.debug("""
                            The KafkaSQL journal topic '{}' appears to contain only Apicurio Registry v3 records. \
                            No v2 records were found.\
                            """, topic);
                }
            }
        } finally {
            verificationConsumer.close();
        }
    }
}
