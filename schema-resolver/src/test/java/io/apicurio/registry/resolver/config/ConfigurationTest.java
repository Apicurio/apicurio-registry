package io.apicurio.registry.resolver.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
class ConfigurationTest {

    /**
     * Ensure that the default configuration values are consistent with the documentation.
     */
    @Test
    void testDefaultConfiguration() {

        var originals = new HashMap<String, Object>();
        var config = new DefaultSchemaResolverConfig(originals);

        var key = "apicurio.registry.auto-register.if-exists";
        assertEquals("RETURN_OR_UPDATE", config.autoRegisterArtifactIfExists());
        assertEquals("RETURN_OR_UPDATE", config.getObject(key));

        originals.put(key, "foo");
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
        try {
            config.autoRegisterArtifact();
            Assertions.fail();
        } catch (IllegalArgumentException ex) {
            // OK
        }

        assertEquals(null, config.getAuthClientId());
        assertEquals(null, config.getObject("apicurio.auth.client.id"));

        assertEquals(null, config.getAuthClientSecret());
        assertEquals(null, config.getObject("apicurio.auth.client.secret"));

        assertEquals(null, config.getAuthClientScope());
        assertEquals(null, config.getObject("apicurio.auth.client.scope"));

        assertEquals(null, config.getAuthPassword());
        assertEquals(null, config.getObject("apicurio.auth.password"));

        assertEquals(null, config.getAuthRealm());
        assertEquals(null, config.getObject("apicurio.auth.realm"));

        assertEquals(null, config.getAuthServiceUrl());
        assertEquals(null, config.getObject("apicurio.auth.service.url"));

        assertEquals(null, config.getAuthUsername());
        assertEquals(null, config.getObject("apicurio.auth.username"));

        assertEquals(null, config.getExplicitArtifactGroupId());
        assertEquals(null, config.getObject("apicurio.registry.artifact.group-id"));

        assertEquals(null, config.getExplicitArtifactVersion());
        assertEquals(null, config.getObject("apicurio.registry.artifact.version"));

        assertEquals(null, config.getExplicitArtifactId());
        assertEquals(null, config.getObject("apicurio.registry.artifact.artifact-id"));

        assertEquals(null, config.getRegistryUrl());
        assertEquals(null, config.getObject("apicurio.registry.url"));

        assertEquals(null, config.getTokenEndpoint());
        assertEquals(null, config.getObject("apicurio.auth.service.token.endpoint"));
        originals.put("apicurio.auth.service.token.endpoint", "foo");
        assertEquals("foo", config.getTokenEndpoint());

        assertEquals(false, config.findLatest());
        assertEquals(false, config.getObject("apicurio.registry.find-latest"));

        // TODO: Does not match documentation, overridden in `io.apicurio.registry.serde.SerdeConfig.ARTIFACT_RESOLVER_STRATEGY_DEFAULT`
        assertEquals("io.apicurio.registry.resolver.strategy.DynamicArtifactReferenceResolverStrategy", config.getArtifactResolverStrategy());
        assertEquals("io.apicurio.registry.resolver.strategy.DynamicArtifactReferenceResolverStrategy", config.getObject("apicurio.registry.artifact-resolver-strategy"));

        key = "apicurio.registry.check-period-ms";
        assertEquals(Duration.ofMillis(30000), config.getCheckPeriod());

        assertEquals(30000L, config.getObject(key));
        originals.put(key, "123"); // String
        assertEquals(Duration.ofMillis(123), config.getCheckPeriod());
        originals.put(key, 456); // Integer
        assertEquals(Duration.ofMillis(456), config.getCheckPeriod());
        originals.put(key, 123.0); // Float
        assertEquals(Duration.ofMillis(123), config.getCheckPeriod());
        originals.put(key, Duration.ofMillis(456)); // Duration
        assertEquals(Duration.ofMillis(456), config.getCheckPeriod());

        key = "apicurio.registry.retry-backoff-ms";
        assertEquals(Duration.ofMillis(300), config.getRetryBackoff());
        assertEquals(300L, config.getObject(key));

        originals.put(key, "123"); // String
        assertEquals(Duration.ofMillis(123), config.getRetryBackoff());
        originals.put(key, 456); // Integer
        assertEquals(Duration.ofMillis(456), config.getRetryBackoff());
        originals.put(key, 123.0); // Float
        assertEquals(Duration.ofMillis(123), config.getRetryBackoff());
        originals.put(key, Duration.ofMillis(456)); // Duration
        assertEquals(Duration.ofMillis(456), config.getRetryBackoff());

        key = "apicurio.registry.retry-count";
        assertEquals(3, config.getRetryCount());
        assertEquals(3L, config.getObject(key));

        originals.put(key, "123"); // String
        assertEquals(123L, config.getRetryCount());
        originals.put(key, 456); // Integer
        assertEquals(456L, config.getRetryCount());
        originals.put(key, 123.0); // Float
        assertEquals(123L, config.getRetryCount());
    }
}
