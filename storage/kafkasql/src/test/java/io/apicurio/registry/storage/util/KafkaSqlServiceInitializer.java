package io.apicurio.registry.storage.util;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.apicurio.registry.util.ClusterInitializer;
import io.apicurio.registry.util.ServiceInitializer;
import io.quarkus.test.common.QuarkusTestResource;

@QuarkusTestResource(KafkaTestContainerManager.class)
public class KafkaSqlServiceInitializer implements ServiceInitializer, ClusterInitializer {
    private static final Logger log = LoggerFactory.getLogger(KafkaSqlServiceInitializer.class);

    private KafkaTestContainerManager manager;

    /**
     * @see io.apicurio.registry.util.ServiceInitializer#beforeAll(java.lang.Object)
     */
    @Override
    public void beforeAll(@Observes @Initialized(ApplicationScoped.class) Object event) throws Exception {
        String bootstrapServers = System.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        System.setProperty("registry.kafkasql.bootstrap.servers", bootstrapServers);
        
        log.info("Bootstrap servers for KSQL test: {}", bootstrapServers);
        
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put("connections.max.idle.ms", 10000);
        properties.put("request.timeout.ms", 5000);
        try (AdminClient client = AdminClient.create(properties)) {
            ListTopicsResult topics = client.listTopics();
            Set<String> names = topics.names().get();
            log.info("Kafka is running - {} ...", names);
        }
    }
    
    /**
     * @see io.apicurio.registry.util.ServiceInitializer#afterAll(java.lang.Object)
     */
    @Override
    public void afterAll(Object event) {
    }

    @Override
    public Map<String, String> startCluster() {
        log.info("Starting Kafka cluster for KSQL test.");
        if (manager != null) {
            throw new RuntimeException("Tried starting Kafka twice!");
        }
        manager = new KafkaTestContainerManager();
        return manager.start();
    }

    @Override
    public void stopCluster() {
        if (manager != null) {
            log.info("Stopping Kafka cluster.");
            manager.stop();
            manager = null;
        }
    }
}
