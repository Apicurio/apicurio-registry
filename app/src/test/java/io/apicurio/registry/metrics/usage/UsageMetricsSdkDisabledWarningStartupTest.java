package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enabling the usage metric while the OpenTelemetry SDK is disabled, which is the shipped default of
 * {@code quarkus.otel.sdk.disabled}, starts the application and logs one warning that nothing will be
 * exported. It runs in the separate JVM that the surefire execution {@code experimental-startup-test}
 * provides, because a QuarkusUnitTest and a QuarkusTest must not share one.
 */
@Tag("experimental-startup")
class UsageMetricsSdkDisabledWarningStartupTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(UsageMetricsConfig.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.oidc.enabled", "false")
            .overrideConfigKey("apicurio.usage.metrics.enabled", "true")
            .overrideConfigKey("quarkus.otel.sdk.disabled", "true")
            .setLogRecordPredicate(record -> UsageMetricsConfig.class.getName().equals(record.getLoggerName()))
            .assertLogRecords(records -> {
                assertEquals(1, records.size(), records.toString());
                assertEquals(Level.WARNING, records.get(0).getLevel());
                assertEquals("apicurio.usage.metrics.enabled is true but the OpenTelemetry SDK is disabled "
                        + "(quarkus.otel.sdk.disabled=true), so no schema fetch usage metric is exported",
                        records.get(0).getMessage());
            });

    @Test
    void warnsThatNothingIsExportedWhileTheSdkIsDisabled() {
        // The assertion on the startup log records above is the test; the application started to get here.
    }
}
