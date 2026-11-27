package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.config.ExperimentalFeaturesConfig;
import io.apicurio.registry.logging.LoggerProducer;
import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The usage metric is experimental: enabling it without the global experimental opt-in stops the application
 * at startup. It runs in the separate JVM that the surefire execution {@code experimental-startup-test}
 * provides, because a QuarkusUnitTest and a QuarkusTest must not share one.
 */
@Tag("experimental-startup")
class UsageMetricsExperimentalGateStartupTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(ExperimentalFeaturesConfig.class, LoggerProducer.class,
                    UsageMetricsConfig.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.oidc.enabled", "false")
            .overrideConfigKey("apicurio.features.experimental.enabled", "false")
            .overrideConfigKey("apicurio.usage.metrics.enabled", "true")
            .assertException(error -> {
                Throwable cause = error;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertTrue(cause.getMessage().contains("apicurio.usage.metrics.enabled"), cause.toString());
                assertTrue(cause.getMessage().contains("apicurio.features.experimental.enabled"));
            });

    @Test
    void refusesStartupWithoutGlobalExperimentalOptIn() {
        fail("Startup must fail before this test executes");
    }
}
