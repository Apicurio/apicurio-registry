package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.rest.UsageTelemetryBuffer;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * Legacy SQL usage telemetry on, the usage metric left at its default (off), with the SDK exporting. The SQL
 * path behaves as before and nothing is added to the export.
 */
@QuarkusTest
@TestProfile(UsageMetricsSqlOnlyTest.Profile.class)
class UsageMetricsSqlOnlyTest extends AbstractUsageMetricsTest {

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("apicurio.usage.telemetry.enabled", "true",
                    "apicurio.usage.flush.every", "2s");
        }
    }

    @InjectSpy
    UsageTelemetryBuffer buffer;

    @BeforeEach
    void resetSpy() {
        Mockito.clearInvocations(buffer);
    }

    @Test
    void theLegacyEventIsEnqueuedUnchangedAndNoUsageMetricIsExported() throws Exception {
        Schema schema = createSchema();
        String client = newClient("sql-only");

        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "serialize"));
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId() + "/references", client, "DESERIALIZE"));

        ArgumentCaptor<SchemaUsageEventDto> events = ArgumentCaptor.forClass(SchemaUsageEventDto.class);
        verify(buffer, Mockito.times(2)).addEvent(events.capture());
        SchemaUsageEventDto fetch = events.getAllValues().get(0);
        assertEquals(schema.globalId(), fetch.getGlobalId());
        assertEquals(0L, fetch.getContentId());
        assertEquals(client, fetch.getClientId());
        assertEquals("serialize", fetch.getOperation(), "the SQL path keeps the raw operation value");
        assertEquals(schema.globalId(), events.getAllValues().get(1).getGlobalId(),
                "the legacy path still accepts the reference listing route");

        // The export is alive (the artifact counter arrives) but the usage metric never does.
        awaitExports(3);
        assertTrue(receiver.metricNames().contains("apicurio.artifacts.created"));
        assertFalse(receiver.metricNames().contains(METRIC));
    }

    @Test
    void withTheUsageMetricOffTheSdkResourceIsNotGivenAnInstanceId() throws Exception {
        String group = newClient("sql-only-identity");
        createSchema(group);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OtlpMetricsTestReceiver.Point> points = receiver.latestPoints("apicurio.artifacts.created")
                    .stream().filter(point -> group.equals(point.attributes().get("apicurio.registry.group_id")))
                    .toList();
            assertEquals(1, points.size());
            assertEquals(1L, points.getFirst().value());
            assertNull(points.getFirst().resource().get("service.instance.id"),
                    "the fallback identity is only added while the usage metric is enabled");
        });
    }
}
