package io.apicurio.registry.resolver.config;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchemaResolverConfig} focusing on configuration options
 * and proper handling of various property types.
 */
public class SchemaResolverConfigTest {

    /**
     * Test that Vertx instance can be configured and retrieved properly.
     */
    @Test
    void testVertxInstanceConfiguration() {
        Map<String, Object> originals = new HashMap<>();

        // Test 1: Default - no Vertx instance provided
        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        assertNull(config.getVertx());
        assertNull(config.getObject(SchemaResolverConfig.VERTX_INSTANCE));

        // Test 2: Provide a Vertx instance
        Vertx customVertx = Vertx.vertx();
        try {
            originals.put(SchemaResolverConfig.VERTX_INSTANCE, customVertx);
            config = new SchemaResolverConfig(originals);

            // Verify the same instance is returned
            Vertx retrievedVertx = config.getVertx();
            assertNotNull(retrievedVertx);
            assertSame(customVertx, retrievedVertx, "Should return the exact same Vertx instance");

            // Verify getObject also returns it
            Object objectVertx = config.getObject(SchemaResolverConfig.VERTX_INSTANCE);
            assertSame(customVertx, objectVertx);

        } finally {
            // Clean up the Vertx instance
            customVertx.close();
        }

        // Test 3: Invalid type provided (should return null, not throw)
        originals.clear();
        originals.put(SchemaResolverConfig.VERTX_INSTANCE, "not-a-vertx-instance");
        config = new SchemaResolverConfig(originals);
        assertNull(config.getVertx(), "Should return null for non-Vertx objects");

        // Test 4: Integer provided (should return null, not throw)
        originals.clear();
        originals.put(SchemaResolverConfig.VERTX_INSTANCE, 123);
        config = new SchemaResolverConfig(originals);
        assertNull(config.getVertx(), "Should return null for non-Vertx objects");
    }

    /**
     * Test TLS/SSL configuration properties.
     */
    @Test
    void testTlsConfiguration() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test defaults
        assertNull(config.getTlsTruststoreLocation());
        assertNull(config.getTlsTruststorePassword());
        assertEquals("JKS", config.getTlsTruststoreType());
        assertNull(config.getTlsCertificates());
        assertFalse(config.getTlsTrustAll());
        assertTrue(config.getTlsVerifyHost());

        // Test setting values
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, "/path/to/truststore.jks");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "secret123");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PKCS12");
        originals.put(SchemaResolverConfig.TLS_CERTIFICATES, "/path/to/cert.pem");
        originals.put(SchemaResolverConfig.TLS_TRUST_ALL, true);
        originals.put(SchemaResolverConfig.TLS_VERIFY_HOST, false);

        config = new SchemaResolverConfig(originals);

        assertEquals("/path/to/truststore.jks", config.getTlsTruststoreLocation());
        assertEquals("secret123", config.getTlsTruststorePassword());
        assertEquals("PKCS12", config.getTlsTruststoreType());
        assertEquals("/path/to/cert.pem", config.getTlsCertificates());
        assertTrue(config.getTlsTrustAll());
        assertFalse(config.getTlsVerifyHost());
    }

    /**
     * Test proxy configuration properties.
     */
    @Test
    void testProxyConfiguration() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test defaults
        assertNull(config.getProxyHost());
        assertNull(config.getProxyPort());
        assertNull(config.getProxyUsername());
        assertNull(config.getProxyPassword());

        // Test setting proxy values
        originals.put(SchemaResolverConfig.PROXY_HOST, "proxy.example.com");
        originals.put(SchemaResolverConfig.PROXY_PORT, 8080);
        originals.put(SchemaResolverConfig.PROXY_USERNAME, "proxyuser");
        originals.put(SchemaResolverConfig.PROXY_PASSWORD, "proxypass");

        config = new SchemaResolverConfig(originals);

        assertEquals("proxy.example.com", config.getProxyHost());
        assertEquals(8080, config.getProxyPort());
        assertEquals("proxyuser", config.getProxyUsername());
        assertEquals("proxypass", config.getProxyPassword());
    }

    /**
     * Test proxy port with different value types.
     */
    @Test
    void testProxyPortWithDifferentTypes() {
        Map<String, Object> originals = new HashMap<>();

        // Test with Integer
        originals.put(SchemaResolverConfig.PROXY_PORT, 8080);
        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        assertEquals(8080, config.getProxyPort());

        // Test with String
        originals.put(SchemaResolverConfig.PROXY_PORT, "3128");
        config = new SchemaResolverConfig(originals);
        assertEquals(3128, config.getProxyPort());

        // Test with Long
        originals.put(SchemaResolverConfig.PROXY_PORT, 9999L);
        config = new SchemaResolverConfig(originals);
        assertEquals(9999, config.getProxyPort());

        // Test with invalid String (should throw)
        originals.put(SchemaResolverConfig.PROXY_PORT, "not-a-number");
        SchemaResolverConfig finalConfig = new SchemaResolverConfig(originals);
        assertThrows(IllegalArgumentException.class, finalConfig::getProxyPort);

        // Test with invalid type (should throw)
        originals.put(SchemaResolverConfig.PROXY_PORT, new Object());
        SchemaResolverConfig finalConfig2 = new SchemaResolverConfig(originals);
        assertThrows(IllegalArgumentException.class, finalConfig2::getProxyPort);
    }

    /**
     * Test registry URL version configuration.
     */
    @Test
    void testRegistryUrlVersion() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test default (null)
        assertNull(config.getRegistryUrlVersion());

        // Test setting version
        originals.put(SchemaResolverConfig.REGISTRY_URL_VERSION, "3");
        config = new SchemaResolverConfig(originals);
        assertEquals("3", config.getRegistryUrlVersion());

        originals.put(SchemaResolverConfig.REGISTRY_URL_VERSION, "2");
        config = new SchemaResolverConfig(originals);
        assertEquals("2", config.getRegistryUrlVersion());
    }

    /**
     * Test schema dereferencing configuration.
     */
    @Test
    void testDereferenceSchema() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test default (false)
        assertFalse(config.resolveDereferenced());

        // Test enabling dereference
        originals.put(SchemaResolverConfig.DEREFERENCE_SCHEMA, true);
        config = new SchemaResolverConfig(originals);
        assertTrue(config.resolveDereferenced());

        // Test with string "true"
        originals.put(SchemaResolverConfig.DEREFERENCE_SCHEMA, "true");
        config = new SchemaResolverConfig(originals);
        assertTrue(config.resolveDereferenced());
    }

    /**
     * Test canonicalize configuration.
     */
    @Test
    void testCanonicalize() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test default (false)
        assertFalse(config.isCanonicalize());

        // Test enabling canonicalize
        originals.put(SchemaResolverConfig.CANONICALIZE, true);
        config = new SchemaResolverConfig(originals);
        assertTrue(config.isCanonicalize());

        // Test with string "true"
        originals.put(SchemaResolverConfig.CANONICALIZE, "true");
        config = new SchemaResolverConfig(originals);
        assertTrue(config.isCanonicalize());
    }

    /**
     * Test explicit artifact configuration.
     */
    @Test
    void testExplicitArtifactConfiguration() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test defaults
        assertNull(config.getExplicitArtifactGroupId());
        assertNull(config.getExplicitArtifactId());
        assertNull(config.getExplicitArtifactVersion());
        assertNull(config.getExplicitSchemaLocation());

        // Test setting explicit values
        originals.put(SchemaResolverConfig.EXPLICIT_ARTIFACT_GROUP_ID, "my-group");
        originals.put(SchemaResolverConfig.EXPLICIT_ARTIFACT_ID, "my-artifact");
        originals.put(SchemaResolverConfig.EXPLICIT_ARTIFACT_VERSION, "1.0.0");
        originals.put(SchemaResolverConfig.SCHEMA_LOCATION, "schemas/myschema.avsc");

        config = new SchemaResolverConfig(originals);

        assertEquals("my-group", config.getExplicitArtifactGroupId());
        assertEquals("my-artifact", config.getExplicitArtifactId());
        assertEquals("1.0.0", config.getExplicitArtifactVersion());
        assertEquals("schemas/myschema.avsc", config.getExplicitSchemaLocation());
    }

    /**
     * Test auth configuration properties.
     */
    @Test
    void testAuthConfiguration() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test defaults
        assertNull(config.getAuthUsername());
        assertNull(config.getAuthPassword());
        assertNull(config.getTokenEndpoint());
        assertNull(config.getAuthClientId());
        assertNull(config.getAuthClientSecret());
        assertNull(config.getAuthClientScope());

        // Test basic auth
        originals.put(SchemaResolverConfig.AUTH_USERNAME, "testuser");
        originals.put(SchemaResolverConfig.AUTH_PASSWORD, "testpass");
        config = new SchemaResolverConfig(originals);
        assertEquals("testuser", config.getAuthUsername());
        assertEquals("testpass", config.getAuthPassword());

        // Test OAuth2
        originals.put(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT, "https://auth.example.com/token");
        originals.put(SchemaResolverConfig.AUTH_CLIENT_ID, "client123");
        originals.put(SchemaResolverConfig.AUTH_CLIENT_SECRET, "secret456");
        originals.put(SchemaResolverConfig.AUTH_CLIENT_SCOPE, "registry:read registry:write");
        config = new SchemaResolverConfig(originals);

        assertEquals("https://auth.example.com/token", config.getTokenEndpoint());
        assertEquals("client123", config.getAuthClientId());
        assertEquals("secret456", config.getAuthClientSecret());
        assertEquals("registry:read registry:write", config.getAuthClientScope());
    }

    /**
     * Test cache and retry configuration.
     */
    @Test
    void testCacheAndRetryConfiguration() {
        Map<String, Object> originals = new HashMap<>();
        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        // Test defaults
        assertTrue(config.getCacheLatest());
        assertFalse(config.getFaultTolerantRefresh());

        // Test setting values
        originals.put(SchemaResolverConfig.CACHE_LATEST, false);
        originals.put(SchemaResolverConfig.FAULT_TOLERANT_REFRESH, true);
        config = new SchemaResolverConfig(originals);

        assertFalse(config.getCacheLatest());
        assertTrue(config.getFaultTolerantRefresh());
    }
}
