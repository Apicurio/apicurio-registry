package io.apicurio.deployment.manual;

import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.apicurio.deployment.TestConfiguration.isClusterTests;
import static org.apache.kafka.clients.CommonClientConfigs.*;
import static org.apache.kafka.common.config.TopicConfig.*;

public class ProxyKafkaRunner implements KafkaRunner {

    private KafkaRunner delegate;

    private AdminClient kafkaClient;


    public ProxyKafkaRunner() {
        if (isClusterTests()) {
            delegate = new ClusterKafkaRunner();
        } else {
            delegate = new DockerKafkaRunner();
        }
    }


    @Override
    public void startAndWait() {
        delegate.startAndWait();
    }


    @Override
    public String getBootstrapServers() {
        return delegate.getBootstrapServers();
    }


    @Override
    public String getTestClientBootstrapServers() {
        return delegate.getTestClientBootstrapServers();
    }


    /**
     * Do not close the client, it is done when the runner is stopped.
     */
    public AdminClient kafkaClient() {
        if (kafkaClient == null) {
            var properties = new Properties();
            properties.put(BOOTSTRAP_SERVERS_CONFIG, getTestClientBootstrapServers());
            properties.put(CONNECTIONS_MAX_IDLE_MS_CONFIG, 2 * 30 * 1000);
            properties.put(REQUEST_TIMEOUT_MS_CONFIG, 30 * 1000); // Needs to be longer due to Kubernetes port forwarding
            kafkaClient = AdminClient.create(properties);
        }
        return kafkaClient;
    }


    @Override
    public void stopAndWait() {
        if (kafkaClient != null) {
            kafkaClient.close();
        }
        delegate.stopAndWait();
    }


    // === Utils


    @SneakyThrows
    public static void createCompactingTopic(ProxyKafkaRunner kafka, String name, int partitions) {
        var topic = new NewTopic(name, partitions, (short) 1);
        topic.configs(Map.of(
                MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.000001",
                CLEANUP_POLICY_CONFIG, CLEANUP_POLICY_COMPACT,
                SEGMENT_MS_CONFIG, "100",
                DELETE_RETENTION_MS_CONFIG, "100"
        ));
        kafka.kafkaClient().createTopics(List.of(topic)).all().get();
    }
}
