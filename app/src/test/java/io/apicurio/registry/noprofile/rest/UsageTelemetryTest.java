package io.apicurio.registry.noprofile.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactUsageMetrics;
import io.apicurio.registry.rest.client.models.ConsumerVersionHeatmap;
import io.apicurio.registry.rest.client.models.DeprecationReadiness;
import io.apicurio.registry.rest.client.models.UsageSummary;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@QuarkusTest
@TestProfile(UsageTelemetryTest.TelemetryEnabledProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class UsageTelemetryTest extends AbstractResourceTestBase {

    @Inject
    HandleFactory handles;

    public static class TelemetryEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "apicurio.features.experimental.enabled", "true",
                    "apicurio.usage.telemetry.enabled", "true",
                    "apicurio.usage.flush.every", "2s"
            );
        }
    }

    @Test
    public void testUsageSummaryReturnsData() throws Exception {
        String groupId = "UsageTelemetryTest_summary";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();

        fetchSchemaWithTelemetry(globalId, "test-client", "SERIALIZE");
        awaitAsserted(() -> {
            UsageSummary summary = clientV3.admin().usage().summary().get();
            Assertions.assertNotNull(summary);
            // The summary counts every version in the registry, from the aggregation job's cached result once
            // the job has run, so this test cannot pin an exact number; the per-artifact tests do.
            Assertions.assertTrue(summary.getActive() > 0, "Should have at least one active schema");
        });
    }

    @Test
    public void testArtifactUsageMetrics() throws Exception {
        String groupId = "UsageTelemetryTest_metrics";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();

        fetchSchemaWithTelemetry(globalId, "metrics-client", "DESERIALIZE");
        awaitAsserted(() -> {
            ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                    .byGroupId(groupId).byArtifactId(artifactId).get();
            Assertions.assertNotNull(metrics);
            Assertions.assertFalse(metrics.getVersions().isEmpty(), "Should have version metrics");
            Assertions.assertEquals(1, metrics.getVersions().get(0).getTotalFetches());
            Assertions.assertTrue(metrics.getVersions().get(0).getClients().contains("metrics-client"));
        });
    }

    @Test
    public void testConsumerVersionHeatmap() throws Exception {
        String groupId = "UsageTelemetryTest_heatmap";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, "{\"type\": \"int\"}", ContentTypes.APPLICATION_JSON);

        long v1GlobalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1").get().getGlobalId();
        long v2GlobalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("2").get().getGlobalId();

        fetchSchemaWithTelemetry(v2GlobalId, "current-app", "SERIALIZE");
        fetchSchemaWithTelemetry(v1GlobalId, "legacy-app", "DESERIALIZE");
        awaitAsserted(() -> {
            ConsumerVersionHeatmap heatmap = clientV3.admin().usage().artifacts()
                    .byGroupId(groupId).byArtifactId(artifactId).heatmap().get();
            Assertions.assertNotNull(heatmap);
            Assertions.assertEquals(2, heatmap.getConsumers().size());

            var legacyConsumer = heatmap.getConsumers().stream()
                    .filter(c -> "legacy-app".equals(c.getClientId())).findFirst().orElse(null);
            Assertions.assertNotNull(legacyConsumer, "Should find legacy-app consumer");
            Assertions.assertEquals(1, legacyConsumer.getVersionsBehind());
        });
    }

    @Test
    public void testDeprecationReadiness() throws Exception {
        String groupId = "UsageTelemetryTest_deprecation";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();

        fetchSchemaWithTelemetry(globalId, "active-consumer", "DESERIALIZE");
        awaitAsserted(() -> {
            DeprecationReadiness readiness = clientV3.admin().usage().artifacts()
                    .byGroupId(groupId).byArtifactId(artifactId).versions()
                    .byVersion("1").deprecationReadiness().get();
            Assertions.assertNotNull(readiness);
            Assertions.assertFalse(readiness.getSafeToDeprecate(), "Should not be safe to deprecate");
            Assertions.assertFalse(readiness.getActiveConsumers().isEmpty());
            Assertions.assertEquals("active-consumer", readiness.getActiveConsumers().get(0).getClientId());
        });
    }

    @Test
    public void testDeduplication() throws Exception {
        String groupId = "UsageTelemetryTest_dedup";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();

        for (int i = 0; i < 10; i++) {
            fetchSchemaWithTelemetry(globalId, "dedup-client", "SERIALIZE");
        }
        awaitAsserted(() -> {
            ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                    .byGroupId(groupId).byArtifactId(artifactId).get();
            Assertions.assertNotNull(metrics);
            Assertions.assertFalse(metrics.getVersions().isEmpty());
            Assertions.assertEquals(1, metrics.getVersions().get(0).getTotalFetches(),
                    "the dedup window keeps one of the 10 fetches");
        });
    }

    @Test
    public void testInvalidClientIdRejected() throws Exception {
        String groupId = "UsageTelemetryTest_invalid";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();

        fetchSchemaWithTelemetry(globalId, "invalid client!@#", "SERIALIZE");
        awaitSentinelFlushed(groupId);

        ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).get();
        Assertions.assertTrue(metrics.getVersions().isEmpty(), "Invalid clientId should not be recorded");
    }

    @Test
    public void testNoTrackingWithoutHeader() throws Exception {
        String groupId = "UsageTelemetryTest_noheader";
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);

        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();

        clientV3.ids().globalIds().byGlobalId(globalId).get();
        awaitSentinelFlushed(groupId);

        ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).get();
        Assertions.assertTrue(metrics.getVersions().isEmpty(),
                "Fetches without X-Registry-Client-Id should not be tracked");
    }

    /**
     * An operation the operation column cannot hold must not stop the fetch from being recorded: the
     * 40-character one is stored as UNKNOWN, and a valid fetch is stored as sent. UsageTelemetryFilterTest
     * covers non-ASCII values reaching the filter.
     */
    @Test
    public void testAnOverlongOperationIsStoredAsUnknown() throws Exception {
        String groupId = "UsageTelemetryTest_operations";
        String artifactId = generateArtifactId();
        createArtifact(groupId, artifactId, ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON);
        long globalId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();
        String prefix = artifactId + "-";

        fetchSchemaWithTelemetry(globalId, prefix + "valid", "SERIALIZE");
        fetchSchemaWithTelemetry(globalId, prefix + "long", "S".repeat(40));

        awaitAsserted(() -> Assertions.assertEquals(
                List.of(prefix + "long|UNKNOWN|1", prefix + "valid|SERIALIZE|1"),
                usageRows(prefix)));
    }

    private List<String> usageRows(String clientPrefix) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery("SELECT clientId, operation, fetchCount FROM schema_usage_daily "
                    + "WHERE clientId LIKE ? ORDER BY clientId")
                    .bind(0, clientPrefix + "%")
                    .map(rs -> rs.getString("clientId") + "|" + rs.getString("operation") + "|"
                            + rs.getLong("fetchCount"))
                    .list();
        });
    }

    private void fetchSchemaWithTelemetry(long globalId, String clientId, String operation)
            throws Exception {
        String url = "http://localhost:" + testPort + "/apis/registry/v3/ids/globalIds/" + globalId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Registry-Client-Id", clientId)
                .header("X-Registry-Operation", operation)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        Assertions.assertEquals(200, response.statusCode(), "Schema fetch should return 200");
    }

    private static void awaitAsserted(ThrowingRunnable assertion) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(assertion);
    }

    /**
     * A negative check needs a flush to have happened after the fetch it checks. Events are flushed in
     * arrival order, so once a sentinel fetched afterwards is visible, every earlier fetch has been flushed.
     */
    private void awaitSentinelFlushed(String groupId) throws Exception {
        String sentinel = generateArtifactId();
        createArtifact(groupId, sentinel, ArtifactType.JSON, "{\"type\": \"boolean\"}",
                ContentTypes.APPLICATION_JSON);
        long sentinelId = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(sentinel)
                .versions().byVersionExpression("branch=latest").get().getGlobalId();
        fetchSchemaWithTelemetry(sentinelId, "sentinel-client", "SERIALIZE");
        awaitAsserted(() -> Assertions.assertFalse(clientV3.admin().usage().artifacts().byGroupId(groupId)
                .byArtifactId(sentinel).get().getVersions().isEmpty()));
    }
}
