package io.apicurio.registry.noprofile.otel;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Performance benchmark test for OpenTelemetry with all signals ENABLED.
 *
 * This test extends the base performance test but uses a different profile
 * with OTEL traces, metrics, and logs enabled at 100% sampling.
 *
 * To run this test:
 * ./mvnw test -pl app -Dtest=OpenTelemetryPerformanceEnabledTest -DOpenTelemetryPerformanceTest=enabled
 *
 * Compare results with OpenTelemetryPerformanceTest (OTEL disabled) to measure overhead.
 */
@QuarkusTest
@TestProfile(OpenTelemetryPerformanceEnabledTest.OTelFullyEnabledProfile.class)
public class OpenTelemetryPerformanceEnabledTest extends OpenTelemetryPerformanceTest {

    /**
     * Test profile with all OTEL signals enabled at 100% sampling.
     * Uses 'none' exporter to isolate instrumentation overhead from network overhead.
     */
    public static class OTelFullyEnabledProfile extends OTelEnabledProfile {
        @Override
        public String getConfigProfile() {
            return "otel-fully-enabled";
        }
    }
}
