package io.apicurio.registry.noprofile.proxy;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOn;

/**
 * Tests the proxy configuration options in the Java SDK clients.
 */
@QuarkusTest
public class ProxySdkClientTest extends AbstractResourceTestBase {

    private static final int PROXY_PORT = 38080;
    private SimpleTestProxy proxy;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Don't bother with this test
    }

    @BeforeEach
    public void setupProxy() throws Exception {
        URL url = new URL(registryV3ApiUrl);
        String host = url.getHost();
        int port = url.getPort();
        if (port == -1) {
            // If no port specified, it means we're using the test default port
            // which is typically 8081 for QuarkusTest
            port = 8081;
        }
        proxy = new SimpleTestProxy(PROXY_PORT, host, port);
        blockOn(proxy.start());
        proxy.resetRequestCount();
    }

    @AfterEach
    public void teardownProxy() {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    public void testV3ClientWithProxy() throws Exception {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .proxy("localhost", PROXY_PORT));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertTrue(proxy.getRequestCount() > 0);
    }

    @Test
    public void testV3ClientWithoutProxy() throws Exception {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertEquals(0, proxy.getRequestCount());
    }

    @Test
    public void testProxyConfigurationValidation() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientOptions.create(registryV3ApiUrl).proxy("localhost", -1);
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientOptions.create(registryV3ApiUrl).proxy(null, 8080);
        });
    }
}
