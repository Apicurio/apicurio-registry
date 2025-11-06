package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.MountableFile;

import java.io.File;

/**
 * Container resource for Debezium integration tests that uses locally built
 * Apicurio converters
 * instead of downloading them from Maven Central. This ensures tests run
 * against the current
 * SNAPSHOT build of the converters library.
 */
public class DebeziumLocalConvertersResource extends DebeziumContainerResource {

    private static final Logger log = LoggerFactory.getLogger(DebeziumLocalConvertersResource.class);

    /**
     * Creates a Debezium container configured to use locally built Apicurio
     * converters.
     * The converters are expected to be unpacked by the maven-dependency-plugin
     * into
     * target/debezium-converters directory during the build.
     */
    @Override
    protected DebeziumContainer createDebeziumContainer() {
        // Determine the path to the local converters
        String projectDir = System.getProperty("user.dir");
        String convertersPath = projectDir + "/target/debezium-converters";
        File convertersDir = new File(convertersPath);

        log.info("Looking for local Apicurio converters at: {}", convertersPath);

        DebeziumContainer container = new DebeziumContainer("quay.io/debezium/connect")
                .withKafka(kafkaContainer)
                .dependsOn(kafkaContainer);

        // Configure network mode based on environment
        if (shouldUseHostNetwork()) {
            log.info("Using host network mode for Debezium container (Linux/CI environment)");
            container.withNetworkMode("host");
        }
        else {
            log.info("Using bridge network mode for Debezium container (Mac/Windows environment)");
            container.withNetwork(network);
        }

        // Mount local converters if available
        if (convertersDir.exists() && convertersDir.isDirectory()) {
            File[] files = convertersDir.listFiles();
            if (files != null && files.length > 0) {
                log.info("Found {} files in local converters directory", files.length);

                // Copy the entire directory structure to the Kafka Connect plugins path
                container.withCopyFileToContainer(
                        MountableFile.forHostPath(convertersPath),
                        "/kafka/connect/apicurio-converter/");

                log.info("Using local Apicurio converters from: {}", convertersPath);
                log.info("Local converters will be mounted to /kafka/connect/apicurio-converter/ in container");
            }
            else {
                String errorMsg = "Local converters directory exists but is empty: " + convertersPath;
                log.error(errorMsg);
                throw new IllegalStateException(
                        errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
            }
        }
        else {
            String errorMsg = "Local converters not found at: " + convertersPath;
            log.error(errorMsg);
            throw new IllegalStateException(
                    errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
        }

        return container;
    }
}
