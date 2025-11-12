package io.apicurio.registry.tls;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;

/**
 * Tests mutual TLS (mTLS) configuration in the Java SDK clients (both v2 and v3).
 * This test verifies that clients can successfully connect to a registry that REQUIRES
 * client certificates, and that connections without client certificates are rejected.
 */
@QuarkusTest
@TestProfile(MtlsTestProfile.class)
public class MtlsSdkClientTest extends AbstractResourceTestBase {

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

    // ========================================
    // Successful mTLS Connection Tests
    // ========================================

    /**
     * Test v3 SDK client with JKS keystore - should successfully connect with mTLS
     */
    @Test
    public void testV3ClientWithJksKeystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest")
                        .keystoreJks(keystoreUrl.getPath(), "registrytest"));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with JKS keystore - should successfully connect with mTLS
     */
    @Test
    public void testV2ClientWithJksKeystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest")
                        .keystoreJks(keystoreUrl.getPath(), "registrytest"));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PKCS12 keystore - should successfully connect with mTLS
     */
    @Test
    public void testV3ClientWithPkcs12Keystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.p12");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.p12");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStorePkcs12(truststoreUrl.getPath(), "registrytest")
                        .keystorePkcs12(keystoreUrl.getPath(), "registrytest"));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PKCS12 keystore - should successfully connect with mTLS
     */
    @Test
    public void testV2ClientWithPkcs12Keystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.p12");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.p12");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStorePkcs12(truststoreUrl.getPath(), "registrytest")
                        .keystorePkcs12(keystoreUrl.getPath(), "registrytest"));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM keystore - should successfully connect with mTLS
     */
    @Test
    public void testV3ClientWithPemKeystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl, "Truststore PEM file not found");
        Assertions.assertNotNull(clientCertUrl, "Client certificate PEM file not found");
        Assertions.assertNotNull(clientKeyUrl, "Client key PEM file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStorePem(truststoreUrl.getPath())
                        .keystorePem(clientCertUrl.getPath(), clientKeyUrl.getPath()));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM keystore - should successfully connect with mTLS
     */
    @Test
    public void testV2ClientWithPemKeystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl, "Truststore PEM file not found");
        Assertions.assertNotNull(clientCertUrl, "Client certificate PEM file not found");
        Assertions.assertNotNull(clientKeyUrl, "Client key PEM file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStorePem(truststoreUrl.getPath())
                        .keystorePem(clientCertUrl.getPath(), clientKeyUrl.getPath()));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM keystore content - should successfully connect with mTLS
     */
    @Test
    public void testV3ClientWithPemKeystoreContent() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl, "Truststore PEM file not found");
        Assertions.assertNotNull(clientCertUrl, "Client certificate PEM file not found");
        Assertions.assertNotNull(clientKeyUrl, "Client key PEM file not found");

        String pemTrustContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(truststoreUrl.toURI())));
        String pemCertContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(clientCertUrl.toURI())));
        String pemKeyContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(clientKeyUrl.toURI())));

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStorePemContent(pemTrustContent)
                        .keystorePemContent(pemCertContent, pemKeyContent));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM keystore content - should successfully connect with mTLS
     */
    @Test
    public void testV2ClientWithPemKeystoreContent() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl, "Truststore PEM file not found");
        Assertions.assertNotNull(clientCertUrl, "Client certificate PEM file not found");
        Assertions.assertNotNull(clientKeyUrl, "Client key PEM file not found");

        String pemTrustContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(truststoreUrl.toURI())));
        String pemCertContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(clientCertUrl.toURI())));
        String pemKeyContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(clientKeyUrl.toURI())));

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStorePemContent(pemTrustContent)
                        .keystorePemContent(pemCertContent, pemKeyContent));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    // ========================================
    // Negative Tests - Should Fail Without Client Certificate
    // ========================================

    /**
     * Test v3 SDK client without client certificate - should fail when server requires mTLS
     * This verifies that the server actually requires client certificates
     */
    @Test
    public void testV3ClientWithoutClientCertificate() {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        // Create client with only truststore (no client certificate)
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

        // Should fail because server requires client certificate
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            client.system().info().get();
        });

        // Verify it's an SSL-related error about client authentication
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String rootMessage = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        boolean isMtlsError = message.contains("ssl") || message.contains("certificate") ||
                message.contains("handshake") || message.contains("peer") ||
                rootMessage.contains("ssl") || rootMessage.contains("certificate") ||
                rootMessage.contains("handshake") || rootMessage.contains("peer");

        Assertions.assertTrue(isMtlsError,
                "Expected SSL handshake failure (no client cert), got: " + exception.getClass().getName() + ": " + message);
    }

    /**
     * Test v2 SDK client without client certificate - should fail when server requires mTLS
     * This verifies that the server actually requires client certificates
     */
    @Test
    public void testV2ClientWithoutClientCertificate() {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        // Create client with only truststore (no client certificate)
        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl, vertx)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

        // Should fail because server requires client certificate
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            client.system().info().get();
        });

        // Verify it's an SSL-related error about client authentication
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String rootMessage = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        boolean isMtlsError = message.contains("ssl") || message.contains("certificate") ||
                message.contains("handshake") || message.contains("peer") ||
                rootMessage.contains("ssl") || rootMessage.contains("certificate") ||
                rootMessage.contains("handshake") || rootMessage.contains("peer");

        Assertions.assertTrue(isMtlsError,
                "Expected SSL handshake failure (no client cert), got: " + exception.getClass().getName() + ": " + message);
    }
}
