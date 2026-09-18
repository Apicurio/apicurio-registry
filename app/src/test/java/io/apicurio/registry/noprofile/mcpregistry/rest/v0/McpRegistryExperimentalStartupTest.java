package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.config.ExperimentalFeaturesConfig;
import io.apicurio.registry.logging.LoggerProducer;
import io.apicurio.registry.mcpregistry.McpRegistryConfig;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("experimental-startup")
class McpRegistryExperimentalStartupTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(ExperimentalFeaturesConfig.class,
                    LoggerProducer.class, McpRegistryConfig.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.oidc.enabled", "false")
            .overrideConfigKey("apicurio.features.experimental.enabled", "false")
            .overrideConfigKey("apicurio.mcp-registry.enabled", "true")
            .assertException(error -> {
                Throwable cause = error;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertTrue(cause.getMessage().contains("apicurio.mcp-registry.enabled"), cause.toString());
                assertTrue(cause.getMessage().contains("apicurio.features.experimental.enabled"));
            });

    @Test
    void refusesStartupWithoutGlobalExperimentalOptIn() {
        fail("Startup must fail before this test executes");
    }
}
