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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertTrue(allSchemaFetchPoints().stream().noneMatch(p -> isCountedFor(p, ignored)),
                "nothing may be counted for the schema that was only fetched through rejected requests");
        assertEquals(Map.of(), seriesOf(missingClient), "neither 404 response may produce a series");
        assertEquals(1, allSchemaFetchPoints().stream()
                .filter(p -> sentinel.equals(p.attributes().get("apicurio.registry.client_id"))).count());
    }

    /**
     * Whether a point carries one of the two identifiers of the schema. For a recognised identifier type the type
     * counts: global IDs and content IDs are numbered independently, so the same number under the other type is
     * another schema. A point with a missing or unknown type is matched by either number, as a point whose type
     * cannot be told must not hide a count for the schema. A point without an identifier value is never matched.
     */
    private static boolean isCountedFor(OtlpMetricsTestReceiver.Point point, Schema schema) {
        String type = point.attributes().get("apicurio.registry.identifier_type");
        String value = point.attributes().get("apicurio.registry.identifier_value");
        if (value == null) {
            return false;
        }
        String globalId = String.valueOf(schema.globalId());
        String contentId = String.valueOf(schema.contentId());
        if ("global_id".equals(type)) {
            return globalId.equals(value);
        }
        if ("content_id".equals(type)) {
            return contentId.equals(value);
        }
        return globalId.equals(value) || contentId.equals(value);
    }

    private static OtlpMetricsTestReceiver.Point pointFor(String type, long value) {
        return new OtlpMetricsTestReceiver.Point(METRIC, Map.of(),
                Map.of("apicurio.registry.identifier_type", type, "apicurio.registry.identifier_value",
                        String.valueOf(value)),
                1L);
    }

    @Test
    void aSameNumberUnderTheOtherIdentifierTypeIsNotTheIgnoredSchema() {
        // Global ID 2 and content ID 3 are the ignored schema. A valid fetch of another schema may have global ID 3,
        // and another schema may have content ID 2. Neither is the ignored schema.
        Schema ignored = new Schema(2L, 3L);
        assertFalse(isCountedFor(pointFor("global_id", 3L), ignored));
        assertFalse(isCountedFor(pointFor("content_id", 2L), ignored));
        assertFalse(isCountedFor(pointFor("global_id", 4L), ignored));
    }

    @Test
    void aPointOfTheIgnoredSchemaUnderItsOwnIdentifierTypeIsStillDetected() {
        Schema ignored = new Schema(2L, 3L);
        assertTrue(isCountedFor(pointFor("global_id", 2L), ignored));
        assertTrue(isCountedFor(pointFor("content_id", 3L), ignored));
    }

    @Test
    void aPointWithAMissingOrUnknownTypeIsMatchedByEitherIgnoredNumber() {
        Schema ignored = new Schema(2L, 3L);
        OtlpMetricsTestReceiver.Point untyped2 = new OtlpMetricsTestReceiver.Point(METRIC, Map.of(),
                Map.of("apicurio.registry.identifier_value", "2"), 1L);
        OtlpMetricsTestReceiver.Point untyped3 = new OtlpMetricsTestReceiver.Point(METRIC, Map.of(),
                Map.of("apicurio.registry.identifier_value", "3"), 1L);
        assertTrue(isCountedFor(untyped2, ignored), "no type: the ignored global ID number");
        assertTrue(isCountedFor(untyped3, ignored), "no type: the ignored content ID number");
        assertTrue(isCountedFor(pointFor("globalid", 2L), ignored), "a misspelled type: the global ID number");
        assertTrue(isCountedFor(pointFor("contentid", 3L), ignored), "a misspelled type: the content ID number");
        assertTrue(isCountedFor(pointFor("", 3L), ignored), "an empty type: the content ID number");
        assertFalse(isCountedFor(pointFor("globalid", 4L), ignored), "an unknown type with another number");
        assertFalse(isCountedFor(new OtlpMetricsTestReceiver.Point(METRIC, Map.of(), Map.of(), 1L), ignored));
    }

    @Test
    void aPointWithoutAnIdentifierValueIsNeverMatched() {
        Schema ignored = new Schema(2L, 3L);
        OtlpMetricsTestReceiver.Point overflow = new OtlpMetricsTestReceiver.Point(METRIC, Map.of(),
                Map.of("otel.metric.overflow", "true"), 5L);
        OtlpMetricsTestReceiver.Point typedWithoutValue = new OtlpMetricsTestReceiver.Point(METRIC, Map.of(),
                Map.of("apicurio.registry.identifier_type", "global_id"), 1L);
        assertFalse(isCountedFor(overflow, ignored), "an overflow point has no identifier at all");
        assertFalse(isCountedFor(typedWithoutValue, ignored), "a recognised type without a value");
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
