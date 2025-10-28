package io.apicurio.registry.tls;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;

/**
 * Tests the TLS/SSL configuration options in the Java SDK clients (both v2 and v3).
 * This test verifies that clients can successfully connect to an HTTPS-enabled registry
 * using different trust store configurations.
 */
@QuarkusTest
@TestProfile(TlsTestProfile.class)
public class TlsSdkClientTest extends AbstractResourceTestBase {

    @TestHTTPResource(value = "/apis", tls = true)
    URL httpsUrl;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Don't bother with this test
    }

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        vertx = Vertx.vertx();

        // Override base URL to use HTTPS
        registryApiBaseUrl = httpsUrl.toExternalForm();
        registryV2ApiUrl = registryApiBaseUrl + "/registry/v2";
        registryV3ApiUrl = registryApiBaseUrl + "/registry/v3";
    }

    /**
     * Test v3 SDK client with JKS trust store - should successfully connect
     */
    @Test
    public void testV3ClientWithJksTrustStore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with JKS trust store - should successfully connect
     */
    @Test
    public void testV2ClientWithJksTrustStore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM certificate - should successfully connect
     */
    @Test
    public void testV3ClientWithPemCertificate() throws Exception {
        URL certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStorePem(certUrl.getPath()));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM certificate - should successfully connect
     */
    @Test
    public void testV2ClientWithPemCertificate() throws Exception {
        URL certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStorePem(certUrl.getPath()));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM certificate content (as string) - should successfully connect
     */
    @Test
    public void testV3ClientWithPemContent() throws Exception {
        // Read the PEM certificate file and pass it as content string
        URL certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        String pemContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(certUrl.toURI())));

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStorePemContent(pemContent));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM certificate content (as string) - should successfully connect
     */
    @Test
    public void testV2ClientWithPemContent() throws Exception {
        // Read the PEM certificate file and pass it as content string
        URL certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        String pemContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(certUrl.toURI())));

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStorePemContent(pemContent));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with multiple PEM certificates in a single content string - should successfully connect
     */
    @Test
    public void testV3ClientWithMultiplePemCertificatesInContent() throws Exception {
        // Read the PEM certificate file
        URL certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        String singleCert = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(certUrl.toURI())));

        // Concatenate the same certificate twice to simulate multiple certificates
        // In a real scenario, these would be different certificates from different CAs
        String multipleCerts = singleCert + "\n" + singleCert;

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStorePemContent(multipleCerts));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with trustAll enabled - should successfully connect
     */
    @Test
    public void testV3ClientWithTrustAll() throws Exception {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustAll(true));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with trustAll enabled - should successfully connect
     */
    @Test
    public void testV2ClientWithTrustAll() throws Exception {
        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustAll(true));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client without SSL configuration - should fail with SSL error
     */
    @Test
    public void testV3ClientWithoutSslConfig() {
        // Create a client with no SSL configuration - should fail
        RequestAdapter adapter = new VertXRequestAdapter(vertx);
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            client.system().info().get();
        });

        // Verify it's an SSL-related error
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable cause = exception.getCause();
        String causeMessage = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        boolean isSslError = message.contains("ssl") || message.contains("certificate") ||
                message.contains("handshake") || causeMessage.contains("ssl") ||
                causeMessage.contains("certificate") || causeMessage.contains("handshake");

        Assertions.assertTrue(isSslError,
                "Expected SSL-related error, got: " + exception.getClass().getName() + ": " + message);
    }

    /**
     * Test v2 SDK client without SSL configuration - should fail with SSL error
     */
    @Test
    public void testV2ClientWithoutSslConfig() {
        // Create a client with no SSL configuration - should fail
        RequestAdapter adapter = new VertXRequestAdapter(vertx);
        adapter.setBaseUrl(registryV2ApiUrl);
        io.apicurio.registry.rest.client.v2.RegistryClient client =
                new io.apicurio.registry.rest.client.v2.RegistryClient(adapter);

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            client.system().info().get();
        });

        // Verify it's an SSL-related error
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable cause = exception.getCause();
        String causeMessage = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        boolean isSslError = message.contains("ssl") || message.contains("certificate") ||
                message.contains("handshake") || causeMessage.contains("ssl") ||
                causeMessage.contains("certificate") || causeMessage.contains("handshake");

        Assertions.assertTrue(isSslError,
                "Expected SSL-related error, got: " + exception.getClass().getName() + ": " + message);
    }

    /**
     * Test v3 SDK client with verifyHost disabled - should successfully connect even with hostname mismatch
     */
    @Test
    public void testV3ClientWithVerifyHostDisabled() throws Exception {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustAll(true)
                        .verifyHost(false));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
    }
}