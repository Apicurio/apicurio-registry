package io.apicurio.registry.storage.util;

import io.apicurio.registry.util.ServiceInitializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;

/**
 * @author Ales Justin
 */
public class KafkaServiceInitializer implements ServiceInitializer {
    private static final Logger log = LoggerFactory.getLogger(KafkaServiceInitializer.class);

    @Override
    public void beforeAll(@Observes @Initialized(ApplicationScoped.class) Object event) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:9092");
        properties.put("connections.max.idle.ms", 10000);
        properties.put("request.timeout.ms", 5000);
        try (AdminClient client = KafkaAdminClient.create(properties)) {
            ListTopicsResult topics = client.listTopics();
            Set<String> names = topics.names().get();
            if (names.isEmpty()) {
                // case: if no topic found.
            }
            log.info("Kafka is running ...");
        }
    }
}
