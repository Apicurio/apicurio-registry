package io.apicurio.registry.tls;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the TLS/SSL configuration options using the JDK HTTP adapter.
 * Extends TlsSdkClientTest and overrides tests to use the JDK adapter instead of Vert.x.
 */
@QuarkusTest
@TestProfile(TlsTestProfile.class)
public class TlsSdkClientJdkAdapterTest extends TlsSdkClientTest {

    /**
     * Test v3 SDK client with JKS trust store using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithJksTrustStore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with JKS trust store using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithJksTrustStore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertEquals("High performance, runtime registry for schemas and API designs.",
                systemInfo.getDescription());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM certificate using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithPemCertificate() throws Exception {
        var certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePem(certUrl.getPath()));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM certificate using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithPemCertificate() throws Exception {
        var certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePem(certUrl.getPath()));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM certificate content using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithPemContent() throws Exception {
        var certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        String pemContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(certUrl.toURI())));

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePemContent(pemContent));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM certificate content using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithPemContent() throws Exception {
        var certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        String pemContent = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(certUrl.toURI())));

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePemContent(pemContent));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with multiple PEM certificates using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithMultiplePemCertificatesInContent() throws Exception {
        var certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        String singleCert = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(certUrl.toURI())));

        String multipleCerts = singleCert + "\n" + singleCert;

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePemContent(multipleCerts));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with trustAll using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithTrustAll() throws Exception {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustAll(true));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with trustAll using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithTrustAll() throws Exception {
        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustAll(true));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with verifyHost disabled using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithVerifyHostDisabled() throws Exception {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustAll(true)
                        .verifyHost(false));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
    }

    /**
     * Test v3 SDK client without SSL config using JDK adapter - should fail
     */
    @Test
    @Override
    public void testV3ClientWithoutSslConfig() {
        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK));

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            client.system().info().get();
        });

        // Verify it's an SSL-related error
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable cause = exception.getCause();
        String causeMessage = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        boolean isSslError = message.contains("ssl") || message.contains("certificate") ||
                message.contains("handshake") || message.contains("pkix") ||
                causeMessage.contains("ssl") || causeMessage.contains("certificate") ||
                causeMessage.contains("handshake") || causeMessage.contains("pkix");

        Assertions.assertTrue(isSslError,
                "Expected SSL-related error, got: " + exception.getClass().getName() + ": " + message);
    }

    /**
     * Test v2 SDK client without SSL config using JDK adapter - should fail
     */
    @Test
    @Override
    public void testV2ClientWithoutSslConfig() {
        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK));

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            client.system().info().get();
        });

        // Verify it's an SSL-related error
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable cause = exception.getCause();
        String causeMessage = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        boolean isSslError = message.contains("ssl") || message.contains("certificate") ||
                message.contains("handshake") || message.contains("pkix") ||
                causeMessage.contains("ssl") || causeMessage.contains("certificate") ||
                causeMessage.contains("handshake") || causeMessage.contains("pkix");

        Assertions.assertTrue(isSslError,
                "Expected SSL-related error, got: " + exception.getClass().getName() + ": " + message);
    }
}
