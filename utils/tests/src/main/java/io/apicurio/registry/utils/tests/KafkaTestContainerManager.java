package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.strimzi.test.container.StrimziKafkaCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

public class KafkaTestContainerManager implements QuarkusTestResourceLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(KafkaTestContainerManager.class);

    private StrimziKafkaCluster kafka;

    @Override
    public int order() {
        return 10000;
    }

    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            log.info("Starting the Kafka Test Container");
            kafka = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                    .withNumberOfBrokers(1)
                    .withAdditionalKafkaConfiguration(Map.of(
                            "transaction.state.log.replication.factor", "1",
                            "transaction.state.log.min.isr", "1"))
                    .withSharedNetwork()
                    .withLogCollection()
                    .build();
            kafka.start();

            String externalBootstrapServers = kafka.getBootstrapServers();

            System.setProperty("bootstrap.servers.external", externalBootstrapServers);

            return Map.of("bootstrap.servers", externalBootstrapServers,
                    "apicurio.kafkasql.bootstrap.servers", externalBootstrapServers);
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public void stop() {
        if (kafka != null) {
            log.info("Stopping the Kafka Test Container");
            kafka.stop();
        }
    }
}
