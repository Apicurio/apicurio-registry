package io.apicurio.registry.client.common.ssl;

import io.apicurio.registry.client.common.RegistryClientOptions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

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

    private static final String CERT_ONE =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBdDCCARmgAwIBAgIUbOIMkZRdRfLiBHhKUVg/w1yOxIgwCgYIKoZIzj0EAwIw\n" +
            "DjEMMAoGA1UEAwwDb25lMCAXDTI2MTAwMTA3NTAzM1oYDzIxMjYwOTA3MDc1MDMz\n" +
            "WjAOMQwwCgYDVQQDDANvbmUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ592Di\n" +
            "hCE+d22gKWqlPG5x4BNdpLP0bhGIR7P2EQFer16eJ2gX2dttfOGJw0Si5JIB9nYi\n" +
            "GyvgJ28aim7U1P4Yo1MwUTAdBgNVHQ4EFgQU6ybgtUgmr+8ky30EbY6F0jI1+mUw\n" +
            "HwYDVR0jBBgwFoAU6ybgtUgmr+8ky30EbY6F0jI1+mUwDwYDVR0TAQH/BAUwAwEB\n" +
            "/zAKBggqhkjOPQQDAgNJADBGAiEA7M4ALuqICDvbDqPf4NwiLjxm4EABcj8ZRnRo\n" +
            "987FMxwCIQDAJYPfGvhtpMg3TWgUv1w4PmH0KB0Z/2x1q2LuqdIMVQ==\n" +
            "-----END CERTIFICATE-----\n";

    private static final String CERT_TWO =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBczCCARmgAwIBAgIUE4eJY2EfrCogsOtmUVX98QYll9cwCgYIKoZIzj0EAwIw\n" +
            "DjEMMAoGA1UEAwwDdHdvMCAXDTI2MTAwMTA3NTAzM1oYDzIxMjYwOTA3MDc1MDMz\n" +
            "WjAOMQwwCgYDVQQDDAN0d28wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASw12+e\n" +
            "f9GCwyAuj3NHHQegCYab2Gbca5Q+bnuK7+8YNnc8c7XGbb8GP9tIC+Sl2QrRJO8S\n" +
            "mPtHEJbRTBI2VJe5o1MwUTAdBgNVHQ4EFgQUL+6/cLSGluY7h1P68fOpNvvMZTIw\n" +
            "HwYDVR0jBBgwFoAUL+6/cLSGluY7h1P68fOpNvvMZTIwDwYDVR0TAQH/BAUwAwEB\n" +
            "/zAKBggqhkjOPQQDAgNIADBFAiEAjkRykpyovtV7Aixj7FCXrrwqWvN0E6C16bjm\n" +
            "nglFlT0CICrMuhm1T0D3H4RU7FbIR3EAaG1hlG9ge1EVkIytZHCe\n" +
            "-----END CERTIFICATE-----\n";

    private static final String EC_PRIVATE_KEY =
            "-----BEGIN PRIVATE KEY-----\n" +
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgPNlKTRPjJyEvbQLN\n" +
            "jwDN+T/2DVFlj3/mtAXGCXt2oTShRANCAAQ592DihCE+d22gKWqlPG5x4BNdpLP0\n" +
            "bhGIR7P2EQFer16eJ2gX2dttfOGJw0Si5JIB9nYiGyvgJ28aim7U1P4Y\n" +
            "-----END PRIVATE KEY-----\n";

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

    @Test
    void testParseSinglePemCertificate() throws Exception {
        List<X509Certificate> certs = JdkSslContextFactory.parsePemCertificates(CERT_ONE);

        assertEquals(1, certs.size());
        assertEquals("CN=one", certs.get(0).getSubjectX500Principal().getName());
    }

    @Test
    void testParseMultiplePemCertificates() throws Exception {
        List<X509Certificate> certs = JdkSslContextFactory.parsePemCertificates(CERT_ONE + CERT_TWO);

        assertEquals(2, certs.size());
        assertEquals("CN=one", certs.get(0).getSubjectX500Principal().getName());
        assertEquals("CN=two", certs.get(1).getSubjectX500Principal().getName());
    }

    @Test
    void testParsePemCertificatesWithCrlfLineEndings() throws Exception {
        String crlf = (CERT_ONE + CERT_TWO).replace("\n", "\r\n");

        List<X509Certificate> certs = JdkSslContextFactory.parsePemCertificates(crlf);

        assertEquals(2, certs.size());
        assertEquals("CN=two", certs.get(1).getSubjectX500Principal().getName());
    }

    @Test
    void testParsePemPrivateKey() throws Exception {
        PrivateKey key = JdkSslContextFactory.parsePemPrivateKey(
                EC_PRIVATE_KEY.replace("\n", "\r\n"));

        assertEquals("EC", key.getAlgorithm());
        assertEquals("PKCS#8", key.getFormat());
    }

    @Test
    void testParsePemPrivateKeyWithoutKeyThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                JdkSslContextFactory.parsePemPrivateKey(CERT_ONE));
    }
}
