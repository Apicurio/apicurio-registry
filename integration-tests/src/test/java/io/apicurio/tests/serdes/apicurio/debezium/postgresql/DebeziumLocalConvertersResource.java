package io.apicurio.tests.serdes.apicurio.debezium.postgresql;

import io.apicurio.tests.serdes.apicurio.debezium.BaseDebeziumContainerResource;
import io.apicurio.tests.serdes.apicurio.debezium.DebeziumLocalConvertersUtil;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.Startables;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Container resource for Debezium integration tests that uses locally built
 * Apicurio converters
 * instead of downloading them from Maven Central. This ensures tests run
 * against the current
 * SNAPSHOT build of the converters library.
 */
public class DebeziumLocalConvertersResource extends DebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumLocalConvertersResource.class);

    @Override
    public Map<String, String> start() {
        // Check if running in Kubernetes cluster mode
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            log.info("cluster.tests=true detected for LOCAL CONVERTERS test");
            log.info("Debezium infrastructure with local converters should already be deployed");

            // Create wrappers using the LOCAL converters service
            postgresContainer = new KubernetesPostgreSQLContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.POSTGRESQL_DEBEZIUM_SERVICE_EXTERNAL);
            BaseDebeziumContainerResource.debeziumContainer = new KubernetesDebeziumContainerWrapper(
                    io.apicurio.deployment.KubernetesTestResources.DEBEZIUM_CONNECT_LOCAL_SERVICE_EXTERNAL);

            log.info("Debezium service wrappers created for cluster mode using LOCAL converters");
            return Collections.emptyMap();
        }

        // Local mode: Use Testcontainers with local converters
        log.info("cluster.tests=false, using Testcontainers with local converters");

        postgresContainer = createPostgreSQLContainer();
        BaseDebeziumContainerResource.debeziumContainer = createDebeziumContainer(); // This will mount local converters
        BaseDebeziumContainerResource.kafkaContainer = BaseDebeziumContainerResource.createKafkaContainer();

        // Start the postgresql database, kafka, and debezium
        Startables.deepStart(Stream.of(BaseDebeziumContainerResource.kafkaContainer, postgresContainer, BaseDebeziumContainerResource.debeziumContainer)).join();
        System.setProperty("bootstrap.servers", BaseDebeziumContainerResource.kafkaContainer.getBootstrapServers());

        return Collections.emptyMap();
    }

    /**
     * Creates a Debezium container configured to use locally built Apicurio converters.
     * The converters are expected to be unpacked by the maven-dependency-plugin into
     * target/debezium-converters directory during the build.
     */
    @Override
    protected DebeziumContainer createDebeziumContainer() {
        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withKafka(BaseDebeziumContainerResource.kafkaContainer)
                .dependsOn(BaseDebeziumContainerResource.kafkaContainer);

        // Configure network mode based on environment
        if (BaseDebeziumContainerResource.shouldUseHostNetwork()) {
            log.info("Using host network mode for Debezium container (Linux/CI environment)");
            container.withNetworkMode("host");
        } else {
            log.info("Using bridge network mode for Debezium container (Mac/Windows environment)");
            container.withNetwork(BaseDebeziumContainerResource.network);
        }

        // Mount local converters using shared utility
        DebeziumLocalConvertersUtil.mountLocalConverters(container);

        return container;
    }
}
