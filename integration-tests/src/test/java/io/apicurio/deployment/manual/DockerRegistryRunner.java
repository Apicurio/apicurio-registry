package io.apicurio.deployment.manual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.apicurio.deployment.TestConfiguration.Constants.REGISTRY_KAFKASQL_IMAGE;

public class DockerRegistryRunner implements RegistryRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerRegistryRunner.class);

    private GenericContainer registry;
    private int nodeId;


    @Override
    public void start(int nodeId, Instant startingLine, String image, String bootstrapServers, List<String> args, BiConsumer<String, RegistryRunner> reporter) {
        this.nodeId = nodeId;
        if (startingLine != null) {
            log.warn("startingLine is not supported yet"); // TODO
        }
        if (reporter != null) {
            log.warn("reporter is not supported yet"); // TODO
        }
        if (args != null && !args.isEmpty()) {
            log.warn("args is not supported yet"); // TODO
        }
        if (image == null) {
            image = System.getProperty(REGISTRY_KAFKASQL_IMAGE);
        }

        registry = new GenericContainer(image);
        registry.withEnv(
                        Map.of(
                                "KAFKA_BOOTSTRAP_SERVERS", bootstrapServers,
                                "QUARKUS_HTTP_PORT", String.valueOf(8780 + nodeId)
                        )
                )
                .withNetworkMode("host")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("registry " + nodeId)))
                .start();
    }


    @Override
    public Map<String, Object> getReport() {
        throw new UnsupportedOperationException("reporter"); // TODO
    }


    @Override
    public int getNodeId() {
        return nodeId;
    }


    @Override
    public String getClientURL() {
        if (!isStarted()) {
            throw new IllegalStateException("Not started.");
        }
        return String.format("http://localhost:%s", 8780 + nodeId);
    }


    @Override
    public boolean isStarted() {
        return registry.isRunning();
    }


    @Override
    public boolean isStopped() {
        return !isStarted();
    }


    @Override
    public void stop() {
        if (registry != null) {
            registry.stop();
        }
    }


    @Override
    public void stopAndWait() {
        stop();
    }
}
