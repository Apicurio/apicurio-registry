package io.apicurio.registry.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Pins that deprecated properties which still carry a {@code @ConfigProperty} default are not visible to
 * {@link DeprecatedPropertiesCheck} on a default start. {@code DeprecatedPropertiesCheck.isConfigured} reads
 * {@code Config.getOptionalValue}, and the annotation default is not part of that lookup.
 */
@QuarkusTest
class DeprecatedPropertiesDefaultSilenceTest {

    @Inject
    Config config;

    @Test
    void deadThresholdDaysIsNotConfiguredByDefault() {
        assertEquals(Optional.empty(),
                config.getOptionalValue("apicurio.usage.dead-threshold-days", String.class),
                "DeprecatedPropertiesCheck.isConfigured reads exactly this lookup, so a non-empty value"
                        + " would log a deprecation WARN on every start");
    }

    @Test
    void useApitomyIsNotConfiguredByDefault() {
        assertEquals(Optional.empty(),
                config.getOptionalValue("apicurio.compat.json-schema.use-apitomy", String.class),
                "DeprecatedPropertiesCheck.isConfigured reads exactly this lookup, so a non-empty value"
                        + " would log a deprecation WARN on every start");
    }

    @Test
    void deprecatedAgentPropertiesAreNotConfiguredByDefault() {
        for (String name : List.of("apicurio.a2a.agent.protocol-version", "apicurio.ai-catalog.spec-version",
                "apicurio.ard.federation.default", "apicurio.a2a.agent.name", "apicurio.ai-catalog.host-name")) {
            assertEquals(Optional.empty(), config.getOptionalValue(name, String.class),
                    name + " must not be set by default, or every start would log a deprecation WARN");
        }
    }
}
