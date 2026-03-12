package io.apicurio.registry.tls;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests mutual TLS (mTLS) configuration using the JDK HTTP adapter.
 * Extends MtlsSdkClientTest and overrides tests to use the JDK adapter instead of Vert.x.
 */
@QuarkusTest
@TestProfile(MtlsTestProfile.class)
@Typed(MtlsSdkClientJdkAdapterTest.class)
public class MtlsSdkClientJdkAdapterTest extends MtlsSdkClientTest {

    /**
     * Test v3 SDK client with JKS keystore using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithJksKeystore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        var keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
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
     * Test v2 SDK client with JKS keystore using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithJksKeystore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        var keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
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
     * Test v3 SDK client with PKCS12 keystore using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithPkcs12Keystore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.p12");
        var keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.p12");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
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
     * Test v2 SDK client with PKCS12 keystore using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithPkcs12Keystore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.p12");
        var keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.p12");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
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
     * Test v3 SDK client with PEM keystore using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithPemKeystore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        var clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        var clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl, "Truststore PEM file not found");
        Assertions.assertNotNull(clientCertUrl, "Client certificate PEM file not found");
        Assertions.assertNotNull(clientKeyUrl, "Client key PEM file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePem(truststoreUrl.getPath())
                        .keystorePem(clientCertUrl.getPath(), clientKeyUrl.getPath()));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM keystore using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithPemKeystore() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        var clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        var clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl, "Truststore PEM file not found");
        Assertions.assertNotNull(clientCertUrl, "Client certificate PEM file not found");
        Assertions.assertNotNull(clientKeyUrl, "Client key PEM file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePem(truststoreUrl.getPath())
                        .keystorePem(clientCertUrl.getPath(), clientKeyUrl.getPath()));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client with PEM keystore content using JDK adapter
     */
    @Test
    @Override
    public void testV3ClientWithPemKeystoreContent() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        var clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        var clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
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
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePemContent(pemTrustContent)
                        .keystorePemContent(pemCertContent, pemKeyContent));

        SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v2 SDK client with PEM keystore content using JDK adapter
     */
    @Test
    @Override
    public void testV2ClientWithPemKeystoreContent() throws Exception {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        var clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        var clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
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
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStorePemContent(pemTrustContent)
                        .keystorePemContent(pemCertContent, pemKeyContent));

        io.apicurio.registry.rest.client.v2.models.SystemInfo systemInfo = client.system().info().get();
        Assertions.assertNotNull(systemInfo);
        Assertions.assertNotNull(systemInfo.getName());
        Assertions.assertNotNull(systemInfo.getVersion());
    }

    /**
     * Test v3 SDK client without client certificate using JDK adapter - should fail
     */
    @Test
    @Override
    public void testV3ClientWithoutClientCertificate() {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        RegistryClient client = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

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
     * Test v2 SDK client without client certificate using JDK adapter - should fail
     */
    @Test
    @Override
    public void testV2ClientWithoutClientCertificate() {
        var truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        io.apicurio.registry.rest.client.v2.RegistryClient client = RegistryV2ClientFactory.create(
                RegistryClientOptions.create(registryV2ApiUrl)
                        .httpAdapter(HttpAdapterType.JDK)
                        .trustStoreJks(truststoreUrl.getPath(), "registrytest"));

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
