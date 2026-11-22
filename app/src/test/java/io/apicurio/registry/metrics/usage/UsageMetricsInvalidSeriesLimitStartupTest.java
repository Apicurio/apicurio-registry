package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An enabled usage metric with a series limit that is not positive stops the application at startup. It runs
 * in the separate JVM that the surefire execution {@code experimental-startup-test} provides, because a
 * QuarkusUnitTest and a QuarkusTest must not share one.
 */
@Tag("experimental-startup")
class UsageMetricsInvalidSeriesLimitStartupTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(UsageMetricsConfig.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.oidc.enabled", "false")
            .overrideConfigKey("apicurio.usage.metrics.enabled", "true")
            .overrideConfigKey("apicurio.usage.metrics.max-series", "0")
            .assertException(error -> {
                Throwable cause = error;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertEquals("apicurio.usage.metrics.max-series must be a positive integer, but was 0",
                        cause.getMessage());
            });

    @Test
    void refusesStartupWithANonPositiveSeriesLimit() {
        fail("Startup must fail before this test executes");
    }
}
