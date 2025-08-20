package io.apicurio.deployment.manual;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.apicurio.deployment.TestConfiguration.isClusterTests;
import static io.restassured.RestAssured.given;

public class ProxyRegistryRunner implements RegistryRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRegistryRunner.class);

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
        Awaitility.await().atMost(Duration.ofSeconds(3 * 60)).until(this::isReady);
    }

    // === Health Checks

    /**
     * Verifies that Registry API is healthy and responsive
     */
    public void verifyRegistryHealth() {
        LOGGER.info("Verifying Registry API health...");
        var timeout = io.apicurio.deployment.TestConfiguration.getRegistryTimeout();
        
        Awaitility.await("Registry API to be healthy")
            .atMost(timeout)
            .pollInterval(io.apicurio.deployment.TestConfiguration.getPollInterval())
            .until(() -> {
                try {
                    var response = given()
                        .baseUri(getClientURL())
                        .get("/health")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
                    
                    LOGGER.info("Registry health check passed: status={}", response.getStatusCode());
                    return true;
                } catch (Exception e) {
                    LOGGER.warn("Registry health check failed: {}", e.getMessage());
                    return false;
                }
            });
        LOGGER.info("✓ Registry API health verified");
    }

    /**
     * Verifies that Registry API can perform basic operations
     */
    public void verifyRegistryApiOperations() {
        LOGGER.info("Verifying Registry API operations...");
        var timeout = io.apicurio.deployment.TestConfiguration.getRegistryTimeout();
        
        Awaitility.await("Registry API operations to work")
            .atMost(timeout)
            .pollInterval(io.apicurio.deployment.TestConfiguration.getPollInterval())
            .until(() -> {
                try (var client = RegistryClientFactory.create(getClientURL())) {
                    // Try basic search operation to verify API is working
                    var results = client.searchArtifacts(null, null, null, null, null, null, null, null, null);
                    LOGGER.info("Registry search returned {} artifacts", results.getCount());
                    return true;
                } catch (Exception e) {
                    LOGGER.warn("Registry API operations check failed: {}", e.getMessage());
                    return false;
                }
            });
        LOGGER.info("✓ Registry API operations verified");
    }

    /**
     * Verifies Registry readiness endpoint specifically
     */
    public void verifyRegistryReadiness() {
        LOGGER.info("Verifying Registry readiness...");
        var timeout = io.apicurio.deployment.TestConfiguration.getRegistryTimeout();
        
        Awaitility.await("Registry to be ready")
            .atMost(timeout)
            .pollInterval(io.apicurio.deployment.TestConfiguration.getPollInterval())
            .until(() -> {
                try {
                    given()
                        .baseUri(getClientURL())
                        .get("/health/ready")
                        .then()
                        .statusCode(200);
                    
                    LOGGER.info("Registry readiness check passed");
                    return true;
                } catch (Exception e) {
                    LOGGER.warn("Registry readiness check failed: {}", e.getMessage());
                    return false;
                }
            });
        LOGGER.info("✓ Registry readiness verified");
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
