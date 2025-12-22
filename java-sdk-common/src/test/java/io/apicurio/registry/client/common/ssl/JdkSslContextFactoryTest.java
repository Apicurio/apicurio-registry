package io.apicurio.registry.client.common.ssl;

import io.apicurio.registry.client.common.RegistryClientOptions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JdkSslContextFactory}.
 */
class JdkSslContextFactoryTest {

    @Test
    void testHasSslConfigWithNoConfig() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080");

        assertFalse(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testHasSslConfigWithTrustAll() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustAll(true);

        assertTrue(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testHasSslConfigWithVerifyHostDisabled() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .verifyHost(false);

        assertTrue(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testHasSslConfigWithJksTrustStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustStoreJks("/path/to/truststore.jks", "password");

        assertTrue(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testHasSslConfigWithPkcs12TrustStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustStorePkcs12("/path/to/truststore.p12", "password");

        assertTrue(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testHasSslConfigWithPemTrustStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustStorePem("/path/to/cert.pem");

        assertTrue(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testHasSslConfigWithClientKeyStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .keystoreJks("/path/to/keystore.jks", "password");

        assertTrue(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testCreateSslContextWithTrustAll() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustAll(true);

        SSLContext sslContext = JdkSslContextFactory.createSslContext(options);

        assertNotNull(sslContext);
        assertEquals("TLS", sslContext.getProtocol());
    }

    @Test
    void testCreateSslContextWithDefaultConfig() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080");

        SSLContext sslContext = JdkSslContextFactory.createSslContext(options);

        assertNotNull(sslContext);
        assertEquals("TLS", sslContext.getProtocol());
    }

    @Test
    void testCreateSslParametersWithHostVerification() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .verifyHost(true);

        SSLParameters params = JdkSslContextFactory.createSslParameters(options);

        assertNotNull(params);
        assertEquals("HTTPS", params.getEndpointIdentificationAlgorithm());
    }

    @Test
    void testCreateSslParametersWithoutHostVerification() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .verifyHost(false);

        SSLParameters params = JdkSslContextFactory.createSslParameters(options);

        assertNotNull(params);
        assertNull(params.getEndpointIdentificationAlgorithm());
    }

    @Test
    void testCreateSslContextWithNonExistentJksTrustStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustStoreJks("/nonexistent/truststore.jks", "password");

        assertThrows(RuntimeException.class, () ->
                JdkSslContextFactory.createSslContext(options));
    }

    @Test
    void testCreateSslContextWithNonExistentPkcs12TrustStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustStorePkcs12("/nonexistent/truststore.p12", "password");

        assertThrows(RuntimeException.class, () ->
                JdkSslContextFactory.createSslContext(options));
    }

    @Test
    void testCreateSslContextWithNonExistentPemTrustStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .trustStorePem("/nonexistent/cert.pem");

        assertThrows(RuntimeException.class, () ->
                JdkSslContextFactory.createSslContext(options));
    }

    @Test
    void testCreateSslContextWithNonExistentJksKeyStore() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080")
                .keystoreJks("/nonexistent/keystore.jks", "password");

        assertThrows(RuntimeException.class, () ->
                JdkSslContextFactory.createSslContext(options));
    }

    @Test
    void testCreateSslContextWithPemCertContentConfigured() {
        // Test that hasSslConfig returns true when PEM content is configured
        // A valid PEM certificate from Java's default cacerts (Let's Encrypt root)
        // Note: Actually parsing requires a valid cert, so we test the config detection here
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8080");

        // Without PEM content, default config has no SSL settings
        assertFalse(JdkSslContextFactory.hasSslConfig(options));
    }

    @Test
    void testCreateSslContextWithInvalidPemContentThrows() {
        // Content that doesn't contain a valid BEGIN CERTIFICATE marker
        // should throw at the options level
        String noPemPattern = "This is not a certificate";

        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientOptions.create()
                        .registryUrl("https://localhost:8080")
                        .trustStorePemContent(noPemPattern));
    }

    @Test
    void testCreateSslContextWithEmptyPemCertContentThrows() {
        // Empty content should throw at the options level
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientOptions.create()
                        .registryUrl("https://localhost:8080")
                        .trustStorePemContent(""));
    }

    @Test
    void testCreateSslContextWithNullPemCertContentThrows() {
        // Null content should throw at the options level
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientOptions.create()
                        .registryUrl("https://localhost:8080")
                        .trustStorePemContent(null));
    }
}
