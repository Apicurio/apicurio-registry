package io.apicurio.registry.maven;

import io.apicurio.registry.client.common.RegistryClientOptions;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TLS configuration functionality in AbstractRegistryMojo.
 */
public class TlsConfigurationTest {

    private TestableRegistryMojo mojo;

    @BeforeEach
    void setup() {
        mojo = new TestableRegistryMojo();
        mojo.setRegistryUrl("http://localhost:8080/apis/registry/v3");
    }

    // ========================================================================
    // Tests for detectStoreType
    // ========================================================================

    @Test
    void testDetectStoreType_JksExtension() {
        assertEquals("JKS", mojo.detectStoreType("/path/to/truststore.jks", null));
        assertEquals("JKS", mojo.detectStoreType("/path/to/truststore.JKS", null));
    }

    @Test
    void testDetectStoreType_Pkcs12Extension() {
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.p12", null));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.P12", null));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.pfx", null));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.PFX", null));
    }

    @Test
    void testDetectStoreType_PemExtension() {
        assertEquals("PEM", mojo.detectStoreType("/path/to/ca-cert.pem", null));
        assertEquals("PEM", mojo.detectStoreType("/path/to/ca-cert.PEM", null));
        assertEquals("PEM", mojo.detectStoreType("/path/to/ca-cert.crt", null));
        assertEquals("PEM", mojo.detectStoreType("/path/to/ca-cert.CRT", null));
        assertEquals("PEM", mojo.detectStoreType("/path/to/ca-cert.cer", null));
        assertEquals("PEM", mojo.detectStoreType("/path/to/ca-cert.CER", null));
    }

    @Test
    void testDetectStoreType_ExplicitTypeOverridesExtension() {
        assertEquals("JKS", mojo.detectStoreType("/path/to/truststore.p12", "JKS"));
        assertEquals("PEM", mojo.detectStoreType("/path/to/truststore.jks", "PEM"));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/cert.pem", "PKCS12"));
    }

    @Test
    void testDetectStoreType_UnknownExtensionDefaultsToPkcs12() {
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.unknown", null));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore", null));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.keystore", null));
    }

    @Test
    void testDetectStoreType_EmptyExplicitTypeUsesExtension() {
        assertEquals("JKS", mojo.detectStoreType("/path/to/truststore.jks", ""));
        assertEquals("PKCS12", mojo.detectStoreType("/path/to/truststore.p12", ""));
    }

    // ========================================================================
    // Tests for TLS setter methods
    // ========================================================================

    @Test
    void testSetTrustStorePath() {
        mojo.setTrustStorePath("/path/to/truststore.p12");
        assertEquals("/path/to/truststore.p12", mojo.trustStorePath);
    }

    @Test
    void testSetTrustStorePassword() {
        mojo.setTrustStorePassword("secret");
        assertEquals("secret", mojo.trustStorePassword);
    }

    @Test
    void testSetTrustStoreType() {
        mojo.setTrustStoreType("JKS");
        assertEquals("JKS", mojo.trustStoreType);
    }

    @Test
    void testSetKeyStorePath() {
        mojo.setKeyStorePath("/path/to/keystore.p12");
        assertEquals("/path/to/keystore.p12", mojo.keyStorePath);
    }

    @Test
    void testSetKeyStorePassword() {
        mojo.setKeyStorePassword("secret");
        assertEquals("secret", mojo.keyStorePassword);
    }

    @Test
    void testSetKeyStoreType() {
        mojo.setKeyStoreType("PKCS12");
        assertEquals("PKCS12", mojo.keyStoreType);
    }

    @Test
    void testSetKeyStorePemKeyPath() {
        mojo.setKeyStorePemKeyPath("/path/to/client-key.pem");
        assertEquals("/path/to/client-key.pem", mojo.keyStorePemKeyPath);
    }

    @Test
    void testSetTrustAll() {
        assertFalse(mojo.trustAll);
        mojo.setTrustAll(true);
        assertTrue(mojo.trustAll);
    }

    @Test
    void testSetVerifyHostname() {
        // Note: Java default for boolean is false; Maven @Parameter defaultValue only applies during Maven execution
        mojo.setVerifyHostname(true);
        assertTrue(mojo.verifyHostname);
        mojo.setVerifyHostname(false);
        assertFalse(mojo.verifyHostname);
    }

    // ========================================================================
    // Tests for configureTrustStore
    // ========================================================================

    @Test
    void testConfigureTrustStore_NullPath_DoesNothing() {
        mojo.setTrustStorePath(null);
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        // Should not throw and should not modify options
        assertDoesNotThrow(() -> mojo.configureTrustStore(options));
        assertEquals(RegistryClientOptions.TrustStoreType.NONE, options.getTrustStoreType());
    }

    @Test
    void testConfigureTrustStore_EmptyPath_DoesNothing() {
        mojo.setTrustStorePath("");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        assertDoesNotThrow(() -> mojo.configureTrustStore(options));
        assertEquals(RegistryClientOptions.TrustStoreType.NONE, options.getTrustStoreType());
    }

    @Test
    void testConfigureTrustStore_JksFormat() {
        mojo.setTrustStorePath("/path/to/truststore.jks");
        mojo.setTrustStorePassword("password");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureTrustStore(options);

        assertEquals(RegistryClientOptions.TrustStoreType.JKS, options.getTrustStoreType());
        assertEquals("/path/to/truststore.jks", options.getTrustStorePath());
        assertEquals("password", options.getTrustStorePassword());
    }

    @Test
    void testConfigureTrustStore_Pkcs12Format() {
        mojo.setTrustStorePath("/path/to/truststore.p12");
        mojo.setTrustStorePassword("password");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureTrustStore(options);

        assertEquals(RegistryClientOptions.TrustStoreType.PKCS12, options.getTrustStoreType());
        assertEquals("/path/to/truststore.p12", options.getTrustStorePath());
        assertEquals("password", options.getTrustStorePassword());
    }

    @Test
    void testConfigureTrustStore_PemFormat() {
        mojo.setTrustStorePath("/path/to/ca-cert.pem");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureTrustStore(options);

        assertEquals(RegistryClientOptions.TrustStoreType.PEM, options.getTrustStoreType());
        assertNotNull(options.getPemCertPaths());
        assertEquals(1, options.getPemCertPaths().length);
        assertEquals("/path/to/ca-cert.pem", options.getPemCertPaths()[0]);
    }

    @Test
    void testConfigureTrustStore_ExplicitType() {
        mojo.setTrustStorePath("/path/to/truststore.unknown");
        mojo.setTrustStoreType("JKS");
        mojo.setTrustStorePassword("password");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureTrustStore(options);

        assertEquals(RegistryClientOptions.TrustStoreType.JKS, options.getTrustStoreType());
    }

    // ========================================================================
    // Tests for configureKeyStore
    // ========================================================================

    @Test
    void testConfigureKeyStore_NullPath_DoesNothing() {
        mojo.setKeyStorePath(null);
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        assertDoesNotThrow(() -> mojo.configureKeyStore(options));
        assertEquals(RegistryClientOptions.KeyStoreType.NONE, options.getKeyStoreType());
    }

    @Test
    void testConfigureKeyStore_EmptyPath_DoesNothing() {
        mojo.setKeyStorePath("");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        assertDoesNotThrow(() -> mojo.configureKeyStore(options));
        assertEquals(RegistryClientOptions.KeyStoreType.NONE, options.getKeyStoreType());
    }

    @Test
    void testConfigureKeyStore_JksFormat() {
        mojo.setKeyStorePath("/path/to/keystore.jks");
        mojo.setKeyStorePassword("password");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureKeyStore(options);

        assertEquals(RegistryClientOptions.KeyStoreType.JKS, options.getKeyStoreType());
        assertEquals("/path/to/keystore.jks", options.getKeyStorePath());
        assertEquals("password", options.getKeyStorePassword());
    }

    @Test
    void testConfigureKeyStore_Pkcs12Format() {
        mojo.setKeyStorePath("/path/to/keystore.p12");
        mojo.setKeyStorePassword("password");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureKeyStore(options);

        assertEquals(RegistryClientOptions.KeyStoreType.PKCS12, options.getKeyStoreType());
        assertEquals("/path/to/keystore.p12", options.getKeyStorePath());
        assertEquals("password", options.getKeyStorePassword());
    }

    @Test
    void testConfigureKeyStore_PemFormat_WithKeyPath() {
        mojo.setKeyStorePath("/path/to/client-cert.pem");
        mojo.setKeyStorePemKeyPath("/path/to/client-key.pem");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        mojo.configureKeyStore(options);

        assertEquals(RegistryClientOptions.KeyStoreType.PEM, options.getKeyStoreType());
        assertEquals("/path/to/client-cert.pem", options.getPemClientCertPath());
        assertEquals("/path/to/client-key.pem", options.getPemClientKeyPath());
    }

    @Test
    void testConfigureKeyStore_PemFormat_WithoutKeyPath_ThrowsException() {
        mojo.setKeyStorePath("/path/to/client-cert.pem");
        mojo.setKeyStorePemKeyPath(null);
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mojo.configureKeyStore(options)
        );

        assertTrue(exception.getMessage().contains("keyStorePemKeyPath is required"));
    }

    @Test
    void testConfigureKeyStore_PemFormat_EmptyKeyPath_ThrowsException() {
        mojo.setKeyStorePath("/path/to/client-cert.pem");
        mojo.setKeyStorePemKeyPath("");
        RegistryClientOptions options = RegistryClientOptions.create("http://localhost:8080");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mojo.configureKeyStore(options)
        );

        assertTrue(exception.getMessage().contains("keyStorePemKeyPath is required"));
    }

    // ========================================================================
    // Tests for default values
    // ========================================================================

    @Test
    void testDefaultValues() {
        TestableRegistryMojo freshMojo = new TestableRegistryMojo();

        // String fields default to null
        assertNull(freshMojo.trustStorePath);
        assertNull(freshMojo.trustStorePassword);
        assertNull(freshMojo.trustStoreType);
        assertNull(freshMojo.keyStorePath);
        assertNull(freshMojo.keyStorePassword);
        assertNull(freshMojo.keyStoreType);
        assertNull(freshMojo.keyStorePemKeyPath);

        // Boolean fields default to Java defaults (false)
        // Note: Maven @Parameter defaultValue only applies during Maven execution
        assertFalse(freshMojo.trustAll);
        assertFalse(freshMojo.verifyHostname);
    }

    // ========================================================================
    // Concrete implementation of AbstractRegistryMojo for testing
    // ========================================================================

    private static class TestableRegistryMojo extends AbstractRegistryMojo {
        @Override
        protected void executeInternal()
                throws MojoExecutionException, MojoFailureException, ExecutionException, InterruptedException {
            // No-op for testing
        }
    }
}
