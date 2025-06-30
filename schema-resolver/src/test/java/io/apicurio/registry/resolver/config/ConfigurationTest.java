package io.apicurio.registry.resolver.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationTest {

    /**
     * Ensure that the default configuration values are consistent with the documentation.
     */
    @Test
    void testDefaultConfiguration() {

        var originals = new HashMap<String, Object>();
        var config = new SchemaResolverConfig(originals);

        var key = "apicurio.registry.auto-register.if-exists";
        assertEquals("FIND_OR_CREATE_VERSION", config.autoRegisterArtifactIfExists());
        assertEquals("FIND_OR_CREATE_VERSION", config.getObject(key));

        originals.put(key, "foo");
        config = new SchemaResolverConfig(originals);

        try {
            config.autoRegisterArtifactIfExists();
            Assertions.fail();
        } catch (IllegalArgumentException ex) {
            // OK
        }

        key = "apicurio.registry.auto-register";
        assertEquals(false, config.autoRegisterArtifact());
        assertEquals(false, config.getObject(key));
        originals.put(key, "foo");
        config = new SchemaResolverConfig(originals);
        try {
            config.autoRegisterArtifact();
            Assertions.fail();
        } catch (IllegalArgumentException ex) {
            // OK
        }

        assertEquals(null, config.getAuthClientId());
        assertEquals(null, config.getObject("apicurio.registry.auth.client.id"));

        assertEquals(null, config.getAuthClientSecret());
        assertEquals(null, config.getObject("apicurio.registry.auth.client.secret"));

        assertEquals(null, config.getAuthClientScope());
        assertEquals(null, config.getObject("apicurio.registry.auth.client.scope"));

        assertEquals(null, config.getAuthPassword());
        assertEquals(null, config.getObject("apicurio.registry.auth.password"));

        assertEquals(null, config.getAuthUsername());
        assertEquals(null, config.getObject("apicurio.registry.auth.username"));

        assertEquals(null, config.getExplicitArtifactGroupId());
        assertEquals(null, config.getObject("apicurio.registry.artifact.group-id"));

        assertEquals(null, config.getExplicitArtifactVersion());
        assertEquals(null, config.getObject("apicurio.registry.artifact.version"));

        assertEquals(null, config.getExplicitArtifactId());
        assertEquals(null, config.getObject("apicurio.registry.artifact.artifact-id"));

        assertEquals(null, config.getRegistryUrl());
        assertEquals(null, config.getObject("apicurio.registry.url"));

        assertEquals(null, config.getTokenEndpoint());
        assertEquals(null, config.getObject("apicurio.registry.auth.service.token.endpoint"));
        originals.put("apicurio.registry.auth.service.token.endpoint", "foo");
        config = new SchemaResolverConfig(originals);
        assertEquals("foo", config.getTokenEndpoint());

        assertEquals(false, config.findLatest());
        assertEquals(false, config.getObject("apicurio.registry.find-latest"));

        // TODO: Does not match documentation, overridden in
        // `io.apicurio.registry.serde.SerdeConfig.ARTIFACT_RESOLVER_STRATEGY_DEFAULT`
        assertEquals("io.apicurio.registry.resolver.strategy.DynamicArtifactReferenceResolverStrategy",
                config.getArtifactResolverStrategy());
        assertEquals("io.apicurio.registry.resolver.strategy.DynamicArtifactReferenceResolverStrategy",
                config.getObject("apicurio.registry.artifact-resolver-strategy"));

        key = "apicurio.registry.check-period-ms";
        assertEquals(Duration.ofMillis(30000), config.getCheckPeriod());

        assertEquals(30000L, config.getObject(key));
        originals.put(key, "123"); // String
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(123), config.getCheckPeriod());
        originals.put(key, 456); // Integer
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(456), config.getCheckPeriod());
        originals.put(key, 123.0); // Float
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(123), config.getCheckPeriod());
        originals.put(key, Duration.ofMillis(456)); // Duration
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(456), config.getCheckPeriod());

        key = "apicurio.registry.retry-backoff-ms";
        assertEquals(Duration.ofMillis(300), config.getRetryBackoff());
        assertEquals(300L, config.getObject(key));

        originals.put(key, "123"); // String
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(123), config.getRetryBackoff());
        originals.put(key, 456); // Integer
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(456), config.getRetryBackoff());
        originals.put(key, 123.0); // Float
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(123), config.getRetryBackoff());
        originals.put(key, Duration.ofMillis(456)); // Duration
        config = new SchemaResolverConfig(originals);
        assertEquals(Duration.ofMillis(456), config.getRetryBackoff());

        key = "apicurio.registry.retry-count";
        assertEquals(3, config.getRetryCount());
        assertEquals(3L, config.getObject(key));

        originals.put(key, "123"); // String
        config = new SchemaResolverConfig(originals);
        assertEquals(123L, config.getRetryCount());
        originals.put(key, 456); // Integer
        config = new SchemaResolverConfig(originals);
        assertEquals(456L, config.getRetryCount());
        originals.put(key, 123.0); // Float
        config = new SchemaResolverConfig(originals);
        assertEquals(123L, config.getRetryCount());
    }

    @Test
    void testRegistryUrlWithCredentials() {
        var originals = new HashMap<String, Object>();
        originals.put("apicurio.registry.url", "https://user:pass@registry.example.com");

        var config = new SchemaResolverConfig(originals);
        config.getRegistryUrl(); // Trigger the URL parsing

        // Verify that the credentials are extracted and set correctly
        assertEquals("user", config.getAuthUsername());
        assertEquals("pass", config.getAuthPassword());

        // Verify that the returned URL does not include the credentials
        assertEquals("https://registry.example.com", config.getRegistryUrl());
    }
}
