package io.apicurio.tests.proxy;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;

/**
 * Integration tests for proxy configuration with Registry SDK clients.
 * Tests verify that clients can successfully connect through an HTTP proxy.
 */
@QuarkusIntegrationTest
public class ProxyRegistryClientIT extends ApicurioRegistryBaseIT {

    private static final int PROXY_PORT = 30002;
    private TrackingProxy proxy;

    @BeforeEach
    public void setupProxy() throws Exception {
        URI registryUri = URI.create(getRegistryV3ApiUrl());
        String host = registryUri.getHost();
        int port = registryUri.getPort();
        if (port == -1) {
            // Default port based on scheme
            port = "https".equals(registryUri.getScheme()) ? 443 : 80;
        }

        logger.info("Setting up proxy to {}:{}", host, port);
        proxy = new TrackingProxy(host, port);
        blockOnResult(proxy.start());
        proxy.resetRequestCount();
    }

    @AfterEach
    public void teardownProxy() {
        if (proxy != null) {
            proxy.stop();
        }
    }

    /**
     * Test that SDK client can connect through proxy and perform basic operations
     */
    @Test
    public void testRegistryClientWithProxy() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create client with proxy configuration pointing to localhost proxy
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(getRegistryV3ApiUrl())
                        .proxy("localhost", PROXY_PORT));

        // Verify system info can be retrieved through proxy
        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        logger.info("Retrieved system info through proxy: {}", systemInfo.getName());

        // Verify requests went through the proxy
        int requestCountAfterSystemInfo = proxy.getRequestCount();
        Assertions.assertTrue(requestCountAfterSystemInfo > 0,
                "Expected requests to go through proxy for system info");

        // Create an artifact through the proxy
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().getContent().setContent("{\"type\": \"object\"}");

        client.groups().byGroupId(groupId).artifacts().post(createArtifact, config -> {
            config.queryParameters.ifExists = IfArtifactExists.FAIL;
        });

        // Retrieve the artifact metadata through the proxy
        ArtifactMetaData metaData = client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId).get();

        Assertions.assertNotNull(metaData);
        Assertions.assertEquals(artifactId, metaData.getArtifactId());
        Assertions.assertEquals(groupId, metaData.getGroupId());
        logger.info("Successfully created and retrieved artifact through proxy");

        // Verify more requests went through the proxy
        int finalRequestCount = proxy.getRequestCount();
        Assertions.assertTrue(finalRequestCount > requestCountAfterSystemInfo,
                "Expected more requests through proxy after artifact operations");

        logger.info("Total requests through proxy: {}", finalRequestCount);
    }

    /**
     * Test that client works without proxy when not configured
     */
    @Test
    public void testRegistryClientWithoutProxy() throws Exception {
        // Create client WITHOUT proxy configuration
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(getRegistryV3ApiUrl()));

        // Perform operations
        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);

        // Verify NO requests went through the proxy (direct connection)
        Assertions.assertEquals(0, proxy.getRequestCount(),
                "Expected no requests through proxy when not configured");
    }

    /**
     * Test proxy configuration with invalid settings
     */
    @Test
    public void testInvalidProxyConfiguration() {
        // Test invalid port
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientOptions.create(getRegistryV3ApiUrl())
                    .proxy("localhost", -1);
        }, "Should throw exception for invalid port");

        // Test null host
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientOptions.create(getRegistryV3ApiUrl())
                    .proxy(null, 8080);
        }, "Should throw exception for null host");

        // Test empty host
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientOptions.create(getRegistryV3ApiUrl())
                    .proxy("", 8080);
        }, "Should throw exception for empty host");
    }
}
