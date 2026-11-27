package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.apicurio.registry.rest.UsageTelemetryBuffer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The usage metric on, SQL usage telemetry off (both at their documented settings), through a running
 * Quarkus application that exports to a receiver owned by the test.
 */
@QuarkusTest
@TestProfile(UsageMetricsOtelOnlyTest.Profile.class)
class UsageMetricsOtelOnlyTest extends AbstractUsageMetricsTest {

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("apicurio.usage.metrics.enabled", "true");
        }
    }

    @InjectSpy
    UsageTelemetryBuffer buffer;

    @Inject
    UsageMetricsConfig metricsConfig;

    @BeforeEach
    void resetSpy() {
        Mockito.clearInvocations(buffer);
    }

    @Test
    void theDocumentedDefaultsApply() {
        assertTrue(metricsConfig.isEnabled());
        assertEquals(2000, metricsConfig.getMaxSeries());
    }

    @Test
    void successfulFetchesAreExportedWithExactAttributes() throws Exception {
        Schema schema = createSchema();
        String client = newClient("payments");

        for (int i = 0; i < 3; i++) {
            assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        }
        assertEquals(200, get(CONTENT_ROUTE + schema.contentId(), client, "deserialize"));
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, null));
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "DROP TABLE"));

        // SQL deduplication would have collapsed the three identical fetches into one row. Here nothing is
        // deduplicated, the operation is normalized, and the content ID is its own identifier type.
        Map<String, Long> expected = new TreeMap<>();
        expected.put("global_id:" + schema.globalId() + ":SERIALIZE", 3L);
        expected.put("content_id:" + schema.contentId() + ":DESERIALIZE", 1L);
        expected.put("global_id:" + schema.globalId() + ":UNKNOWN", 2L);
        awaitSeries(client, expected);
    }

    @Test
    void aQueryStringDoesNotHideAValidFetch() throws Exception {
        Schema schema = createSchema();
        String client = newClient("query");

        // The server answers this request like the plain fetch, and the path it reports has no query part.
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId() + "?returnArtifactType=true", client, "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));

        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 2L));
    }

    @Test
    void matrixParametersExcludeAFetchFromTheCounterEvenWhenTheApiServesIt() throws Exception {
        Schema schema = createSchema();
        String client = newClient("matrix");
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId() + ";source=client", client, "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE.replace("/ids/", "/ids;source=client/") + schema.globalId(), client,
                "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 1L));
    }

    @Test
    void explicitPositiveSignsUseTheSameSeriesAsUnsignedIds() throws Exception {
        Schema schema = createSchema();
        String client = newClient("positive-sign");
        for (String prefix : new String[] { "", "+", "%2B" }) {
            assertEquals(200, get(GLOBAL_ROUTE + prefix + schema.globalId(), client, "SERIALIZE"));
            assertEquals(200, get(CONTENT_ROUTE + prefix + schema.contentId(), client, "SERIALIZE"));
        }
        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 3L,
                "content_id:" + schema.contentId() + ":SERIALIZE", 3L));
    }

    @Test
    void missingInvalidUnsupportedAndUnsuccessfulRequestsAreNotCounted() throws Exception {
        Schema ignored = createSchema();
        Schema sentinelSchema = createSchema();
        String sentinel = newClient("sentinel");

        assertEquals(200, get(GLOBAL_ROUTE + ignored.globalId(), null, "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE + ignored.globalId(), "bad client id", "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE + ignored.globalId(), "a".repeat(257), "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE + ignored.globalId() + "/references", newClient("refs"), "SERIALIZE"));
        assertEquals(200, get(CONTENT_ROUTE + ignored.contentId() + "/references", newClient("refs"),
                "SERIALIZE"));
        String missingClient = newClient("missing");
        assertEquals(404, get(GLOBAL_ROUTE + "987654321", missingClient, "SERIALIZE"));
        assertEquals(404, get(CONTENT_ROUTE + "987654321", missingClient, "SERIALIZE"));
        assertEquals(200, send("HEAD", GLOBAL_ROUTE + ignored.globalId(), newClient("head"), "SERIALIZE"));
        // Recorded after everything above, so an export that carries it also carries anything counted before.
        assertEquals(200, get(GLOBAL_ROUTE + sentinelSchema.globalId(), sentinel, "SERIALIZE"));
        awaitSeries(sentinel, Map.of("global_id:" + sentinelSchema.globalId() + ":SERIALIZE", 1L));

        String ignoredGlobal = String.valueOf(ignored.globalId());
        String ignoredContent = String.valueOf(ignored.contentId());
        assertTrue(allSchemaFetchPoints().stream().noneMatch(p -> {
            String value = p.attributes().get("apicurio.registry.identifier_value");
            return ignoredGlobal.equals(value) || ignoredContent.equals(value);
        }), "nothing may be counted for the schema that was only fetched through rejected requests");
        assertEquals(Map.of(), seriesOf(missingClient), "neither 404 response may produce a series");
        assertEquals(1, allSchemaFetchPoints().stream()
                .filter(p -> sentinel.equals(p.attributes().get("apicurio.registry.client_id"))).count());
    }

    @Test
    void noLegacyUsagePathIsTouchedAndTheSqlUsageApiStaysOff() throws Exception {
        Schema schema = createSchema();
        String client = newClient("otel-only");

        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        assertEquals(200, get(CONTENT_ROUTE + schema.contentId(), client, "SERIALIZE"));
        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 1L,
                "content_id:" + schema.contentId() + ":SERIALIZE", 1L));

        verify(buffer, never()).addEvent(any());
        assertEquals(409, get("/apis/registry/v3/admin/usage/summary", null, null),
                "SQL usage telemetry is still disabled");
    }

    @Test
    void theExportedResourceIdentifiesThisProcess() throws Exception {
        Schema schema = createSchema();
        String client = newClient("identity");
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 1L));

        Map<String, String> resource = receiver.latestPoints(METRIC).get(0).resource();
        String instanceId = resource.get("service.instance.id");
        assertEquals(UUID.fromString(instanceId).toString(), instanceId);
        assertEquals(ConfigProvider.getConfig().getValue("quarkus.otel.service.name", String.class),
                resource.get("service.name"));
    }
}
