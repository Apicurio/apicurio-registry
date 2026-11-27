package io.apicurio.registry.metrics.usage;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A resource identity chosen by the operator is exported unchanged, not replaced by the fallback. */
@QuarkusTest
@TestProfile(UsageMetricsOperatorIdentityTest.Profile.class)
class UsageMetricsOperatorIdentityTest extends AbstractUsageMetricsTest {

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("apicurio.usage.metrics.enabled", "true",
                    "quarkus.otel.service.name", "registry-eu-west",
                    "quarkus.otel.resource.attributes", "service.instance.id=operator-instance-9,deployment.environment=staging");
        }
    }

    @Test
    void theOperatorsServiceNameAndInstanceIdAreExported() throws Exception {
        Schema schema = createSchema();
        String client = newClient("identity");

        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 1L));

        Map<String, String> resource = receiver.latestPoints(METRIC).get(0).resource();
        assertEquals("operator-instance-9", resource.get("service.instance.id"));
        assertEquals("registry-eu-west", resource.get("service.name"));
        assertEquals("staging", resource.get("deployment.environment"));
    }
}
