package io.apicurio.deployment.manual;

import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.apicurio.deployment.TestConfiguration.isClusterTests;
import static io.restassured.RestAssured.given;

public class ProxyRegistryRunner implements RegistryRunner {


    public static ProxyRegistryRunner createClusterOrDocker() {
        if (isClusterTests()) {
            return new ProxyRegistryRunner(new ClusterRegistryRunner());
        } else {
            return new ProxyRegistryRunner(new DockerRegistryRunner());
        }
    }


    public static ProxyRegistryRunner createClusterOrJAR() {
        if (isClusterTests()) {
            return new ProxyRegistryRunner(new ClusterRegistryRunner());
        } else if (JarRegistryRunner.isSupported()) {
            return new ProxyRegistryRunner(new JarRegistryRunner());
        } else {
            throw new IllegalStateException("Could not select Registry runner.");
        }
    }


    private RegistryRunner delegate;


    private ProxyRegistryRunner(RegistryRunner delegate) {
        this.delegate = delegate;
    }


    public void start(String bootstrapServers) {
        start(1, Instant.now(), null, bootstrapServers, List.of(), (line, runner) -> {
        });
    }


    @Override
    public void start(int nodeId, Instant startingLine, String image, String bootstrapServers, List<String> args, BiConsumer<String, RegistryRunner> reporter) {
        if (startingLine == null) {
            startingLine = Instant.now();
        }
        if (args == null) {
            args = List.of();
        }
        if (reporter == null) {
            reporter = (line, node) -> {
            };
        }
        delegate.start(nodeId, startingLine, image, bootstrapServers, args, reporter);
    }


    @Override
    public int getNodeId() {
        return delegate.getNodeId();
    }


    @Override
    public String getClientURL() {
        return delegate.getClientURL();
    }

    @Override
    public Map<String, Object> getReport() {
        return delegate.getReport();
    }


    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean isStopped() {
        return delegate.isStopped();
    }


    public boolean isReady() {
        try {
            given().when()
                    .baseUri(getClientURL())
                    .get("/health/ready")
                    .then()
                    .statusCode(200);
            return true;
        } catch (AssertionError | Exception ex) {
            return false;
        }
    }


    public void waitUntilReady() {
        Awaitility.await().atMost(Duration.ofSeconds(60)).until(this::isReady);
    }


    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void stopAndWait() {
        delegate.stopAndWait();
    }
}
