package io.apicurio.tests.serdes.apicurio.debezium.mysql;

import io.apicurio.tests.serdes.apicurio.debezium.DebeziumLocalConvertersUtil;
import io.apicurio.tests.serdes.apicurio.debezium.postgresql.KubernetesDebeziumContainerWrapper;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.Startables;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Container resource for Debezium MySQL integration tests that uses locally built
 * Apicurio converters instead of downloading them from Maven Central.
 */
public class DebeziumMySQLLocalConvertersResource extends DebeziumMySQLContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumMySQLLocalConvertersResource.class);

    @Override
    public Map<String, String> start() {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true detected for MySQL LOCAL CONVERTERS test");
            log.info("Debezium infrastructure with local converters should already be deployed");

            mysqlContainer = new KubernetesMySQLContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.MYSQL_DEBEZIUM_SERVICE_EXTERNAL);
            debeziumContainer = new KubernetesDebeziumContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_LOCAL_SERVICE_EXTERNAL);

            log.info("Debezium MySQL service wrappers created for cluster mode using LOCAL converters");
            return Collections.emptyMap();
        }

        log.info("cluster.tests=false, using Testcontainers with local converters for MySQL");

        mysqlContainer = createMySQLContainer();
        debeziumContainer = createDebeziumContainerWithLocalConverters();
        kafkaContainer = createKafkaContainer();

        Startables.deepStart(Stream.of(kafkaContainer, mysqlContainer, debeziumContainer)).join();
        System.setProperty("bootstrap.servers", kafkaContainer.getBootstrapServers());

        return Collections.emptyMap();
    }

    /**
     * Creates a Debezium container configured to use locally built Apicurio converters.
     */
    protected DebeziumContainer createDebeziumContainerWithLocalConverters() {
        DebeziumContainer container = createDebeziumContainer();

        // Mount local converters using shared utility
        DebeziumLocalConvertersUtil.mountLocalConverters(container);

        return container;
    }
}
