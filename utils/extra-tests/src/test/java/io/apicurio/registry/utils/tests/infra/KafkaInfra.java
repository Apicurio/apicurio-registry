package io.apicurio.registry.utils.tests.infra;

import io.strimzi.test.container.StrimziKafkaCluster;
import io.strimzi.test.container.StrimziKafkaCluster.StrimziKafkaClusterBuilder;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.utils.CollectionsUtil.toProperties;
import static io.apicurio.registry.utils.ConcurrentUtil.blockOn;
import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;
import static io.apicurio.registry.utils.kafka.KafkaUtil.toJavaFuture;
import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG;

/**
 * Manage a Kafka cluster.
 */
public class KafkaInfra implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaInfra.class);

    private static KafkaInfra instance;

    public static KafkaInfra getInstance() {
        if (instance == null) {
            instance = new KafkaInfra();
        }
        return instance;
    }

    private StrimziKafkaCluster kafkaCluster;

    private Admin client;

    private KafkaInfra() {
    }

    public void start() {
        if (!isRunning()) {
            kafkaCluster = new StrimziKafkaClusterBuilder()
                    .withNumberOfBrokers(1) // required
                    .withSharedNetwork()
                    .build();
            kafkaCluster.start();
        }
    }

    private boolean isRunning() {
        return kafkaCluster != null;
    }

    public String getNetworkBootstrapServers() {
        return kafkaCluster.getNetworkBootstrapServers();
    }

    public String getHostBootstrapServers() {
        return kafkaCluster.getBootstrapServers();
    }

    public Admin getAdminClient() {
        if (client == null) {
            Map<String, String> config = Map.of(
                    BOOTSTRAP_SERVERS_CONFIG, getHostBootstrapServers(),
                    CONNECTIONS_MAX_IDLE_MS_CONFIG, "10000",
                    REQUEST_TIMEOUT_MS_CONFIG, "5000"
            );
            client = Admin.create(toProperties(config));
        }
        return client;
    }

    public void createTopic(String topic) {
        createTopic(topic, null, null, null);
    }

    /**
     * @param topic             required
     * @param partitions        optional
     * @param replicationFactor optional
     * @param configs           optional
     */
    public void createTopic(String topic, Integer partitions, Short replicationFactor, Map<String, String> configs) {
        var newTopic = new NewTopic(topic, ofNullable(partitions), ofNullable(replicationFactor));
        if (configs != null) {
            newTopic.configs(configs);
        }
        blockOn(toJavaFuture(getAdminClient().createTopics(singleton(newTopic)).all()));
    }

    public Set<String> listTopics() {
        return blockOnResult(toJavaFuture(getAdminClient().listTopics().names()));
    }

    public void deleteTopic(String topic) {
        blockOn(toJavaFuture(getAdminClient().deleteTopics(singleton(topic)).all()));
    }

    public void stop() {
        if (isRunning()) {
            if (client != null) {
                client.close();
                client = null;
            }
            kafkaCluster.stop();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
