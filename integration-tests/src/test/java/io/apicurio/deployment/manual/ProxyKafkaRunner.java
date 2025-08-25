package io.apicurio.deployment.manual;

import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.apicurio.deployment.TestConfiguration.isClusterTests;
import static org.apache.kafka.clients.CommonClientConfigs.*;
import static org.apache.kafka.common.config.TopicConfig.*;

public class ProxyKafkaRunner implements KafkaRunner {

    private static final Logger log = LoggerFactory.getLogger(ProxyKafkaRunner.class);

    private KafkaRunner delegate;

    private AdminClient kafkaClient;


    public ProxyKafkaRunner() {
        log.info("Initializing ProxyKafkaRunner");
        boolean clusterMode = isClusterTests();
        log.info("Cluster test mode: {}", clusterMode);
        
        if (clusterMode) {
            log.info("Creating ClusterKafkaRunner delegate");
            delegate = new ClusterKafkaRunner();
        } else {
            log.info("Creating DockerKafkaRunner delegate");
            delegate = new DockerKafkaRunner();
        }
        
        log.info("ProxyKafkaRunner initialized with delegate: {}", delegate.getClass().getSimpleName());
    }


    @Override
    public void startAndWait() {
        log.info("Starting Kafka using delegate: {}", delegate.getClass().getSimpleName());
        try {
            long startTime = System.currentTimeMillis();
            delegate.startAndWait();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Kafka started successfully in {} ms", duration);
            
            // Log bootstrap servers immediately after startup
            try {
                String bootstrapServers = getBootstrapServers();
                String testClientBootstrapServers = getTestClientBootstrapServers();
                log.info("Kafka bootstrap servers: {}", bootstrapServers);
                log.info("Test client bootstrap servers: {}", testClientBootstrapServers);
            } catch (Exception e) {
                log.error("Failed to retrieve bootstrap servers after startup", e);
            }
        } catch (Exception e) {
            log.error("Failed to start Kafka with delegate: {}", delegate.getClass().getSimpleName(), e);
            throw e;
        }
    }


    @Override
    public String getBootstrapServers() {
        try {
            String servers = delegate.getBootstrapServers();
            log.debug("Retrieved bootstrap servers: {}", servers);
            return servers;
        } catch (Exception e) {
            log.error("Failed to get bootstrap servers from delegate", e);
            throw e;
        }
    }


    @Override
    public String getTestClientBootstrapServers() {
        try {
            String servers = delegate.getTestClientBootstrapServers();
            log.debug("Retrieved test client bootstrap servers: {}", servers);
            return servers;
        } catch (Exception e) {
            log.error("Failed to get test client bootstrap servers from delegate", e);
            throw e;
        }
    }


    /**
     * Do not close the client, it is done when the runner is stopped.
     */
    public AdminClient kafkaClient() {
        if (kafkaClient == null) {
            log.info("Creating Kafka AdminClient");
            try {
                String testClientBootstrapServers = getTestClientBootstrapServers();
                log.info("Using bootstrap servers for AdminClient: {}", testClientBootstrapServers);
                
                var properties = new Properties();
                properties.put(BOOTSTRAP_SERVERS_CONFIG, testClientBootstrapServers);
                properties.put(CONNECTIONS_MAX_IDLE_MS_CONFIG, 2 * 30 * 1000);
                properties.put(REQUEST_TIMEOUT_MS_CONFIG, 30 * 1000); // Needs to be longer due to Kubernetes port forwarding
                
                log.debug("AdminClient properties: {}", properties);
                kafkaClient = AdminClient.create(properties);
                log.info("Kafka AdminClient created successfully");
            } catch (Exception e) {
                log.error("Failed to create Kafka AdminClient", e);
                throw e;
            }
        } else {
            log.debug("Returning existing Kafka AdminClient");
        }
        return kafkaClient;
    }


    @Override
    public void stopAndWait() {
        log.info("Stopping Kafka using delegate: {}", delegate.getClass().getSimpleName());
        try {
            if (kafkaClient != null) {
                log.info("Closing Kafka AdminClient");
                kafkaClient.close();
                kafkaClient = null;
                log.info("Kafka AdminClient closed successfully");
            }
            
            long startTime = System.currentTimeMillis();
            delegate.stopAndWait();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Kafka stopped successfully in {} ms", duration);
        } catch (Exception e) {
            log.error("Failed to stop Kafka with delegate: {}", delegate.getClass().getSimpleName(), e);
            throw e;
        }
    }


    // === Utils


    @SneakyThrows
    public static void createCompactingTopic(ProxyKafkaRunner kafka, String name, int partitions) {
        LoggerFactory.getLogger(ProxyKafkaRunner.class).info("Creating compacting topic '{}' with {} partitions", name, partitions);
        try {
            var topic = new NewTopic(name, partitions, (short) 1);
            var configs = Map.of(
                    MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.000001",
                    CLEANUP_POLICY_CONFIG, CLEANUP_POLICY_COMPACT,
                    SEGMENT_MS_CONFIG, "100",
                    DELETE_RETENTION_MS_CONFIG, "100"
            );
            topic.configs(configs);
            LoggerFactory.getLogger(ProxyKafkaRunner.class).debug("Topic configs for '{}': {}", name, configs);
            
            kafka.kafkaClient().createTopics(List.of(topic)).all().get();
            LoggerFactory.getLogger(ProxyKafkaRunner.class).info("Successfully created compacting topic '{}'", name);
        } catch (Exception e) {
            LoggerFactory.getLogger(ProxyKafkaRunner.class).error("Failed to create compacting topic '{}'", name, e);
            throw e;
        }
    }
}
