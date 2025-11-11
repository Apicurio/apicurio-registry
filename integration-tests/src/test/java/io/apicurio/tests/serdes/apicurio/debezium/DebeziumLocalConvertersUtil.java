package io.apicurio.tests.serdes.apicurio.debezium;

import io.debezium.testing.testcontainers.DebeziumContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.MountableFile;

import java.io.File;

/**
 * Utility class for mounting locally built Apicurio converters into Debezium containers.
 * Provides shared logic for validating and mounting converter JARs from the build output.
 */
public class DebeziumLocalConvertersUtil {

    private static final Logger log = LoggerFactory.getLogger(DebeziumLocalConvertersUtil.class);
    private static final String CONVERTERS_PATH_SUFFIX = "/target/debezium-converters";
    private static final String CONTAINER_MOUNT_PATH = "/kafka/connect/apicurio-converter/";

    /**
     * Mounts locally built Apicurio converters into the Debezium container.
     * The converters are expected to be unpacked by maven-dependency-plugin into
     * target/debezium-converters directory during the build.
     *
     * @param container the Debezium container to mount converters into
     * @throws IllegalStateException if converters directory doesn't exist, is empty, or has validation errors
     */
    public static void mountLocalConverters(DebeziumContainer container) {
        String projectDir = System.getProperty("user.dir");
        String convertersPath = projectDir + CONVERTERS_PATH_SUFFIX;
        File convertersDir = new File(convertersPath);

        log.info("Looking for local Apicurio converters at: {}", convertersPath);

        // Validate converters directory exists
        if (!convertersDir.exists() || !convertersDir.isDirectory()) {
            String errorMsg = "Local converters not found at: " + convertersPath;
            log.error(errorMsg);
            throw new IllegalStateException(
                    errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
        }

        // Validate converters directory is not empty
        File[] files = convertersDir.listFiles();
        if (files == null || files.length == 0) {
            String errorMsg = "Local converters directory exists but is empty: " + convertersPath;
            log.error(errorMsg);
            throw new IllegalStateException(
                    errorMsg + ". Please run 'mvn clean install -DskipTests' to build the converters.");
        }

        log.info("Found {} files in local converters directory", files.length);

        // Mount the converters directory into the container
        container.withCopyFileToContainer(
                MountableFile.forHostPath(convertersPath),
                CONTAINER_MOUNT_PATH);

        log.info("Local converters mounted from {} to {} in Debezium container",
                convertersPath, CONTAINER_MOUNT_PATH);
    }
}
