package io.apicurio.registry.storage.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class KafkaTestContainerManager implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTestContainerManager.class);

    KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        kafka = new KafkaContainer();
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafka.addExposedPorts(9092);
        kafka.withCreateContainerCmdModifier(cmd -> {
            cmd
                    .withHostName("localhost")
                    .withPortBindings(new PortBinding(Ports.Binding.bindPort(9092), new ExposedPort(9092)));
        });
        kafka.start();
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:9092");
        properties.put("connections.max.idle.ms", 10000);
        properties.put("request.timeout.ms", 5000);
        try (AdminClient client = KafkaAdminClient.create(properties)) {
            CreateTopicsResult result = client.createTopics(Arrays.asList(
                    new NewTopic("storage-topic", 1, (short) 1),
                    new NewTopic("global-id-topic", 1, (short) 1)
                    ));
            try {
                result.all().get(15, TimeUnit.SECONDS);
            } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
                throw new IllegalStateException(e);
            }
            LOGGER.info("Topics created");
        }
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        kafka.stop();
    }

}
