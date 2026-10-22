package io.apicurio.registry.noprofile.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactUsageMetrics;
import io.apicurio.registry.rest.client.models.ConsumerVersionHeatmap;
import io.apicurio.registry.rest.client.models.DeprecationReadiness;
import io.apicurio.registry.rest.client.models.UsageSummary;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@QuarkusTest
@TestProfile(UsageTelemetryTest.TelemetryEnabledProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class UsageTelemetryTest extends AbstractResourceTestBase {

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
        waitForFlush();

        UsageSummary summary = clientV3.admin().usage().summary().get();
        Assertions.assertNotNull(summary);
        Assertions.assertTrue(summary.getActive() > 0, "Should have at least one active schema");
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
        waitForFlush();

        ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).get();
        Assertions.assertNotNull(metrics);
        Assertions.assertFalse(metrics.getVersions().isEmpty(), "Should have version metrics");
        Assertions.assertTrue(metrics.getVersions().get(0).getTotalFetches() > 0);
        Assertions.assertTrue(metrics.getVersions().get(0).getClients().contains("metrics-client"));
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
        waitForFlush();

        ConsumerVersionHeatmap heatmap = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).heatmap().get();
        Assertions.assertNotNull(heatmap);
        Assertions.assertEquals(2, heatmap.getConsumers().size());

        var legacyConsumer = heatmap.getConsumers().stream()
                .filter(c -> "legacy-app".equals(c.getClientId())).findFirst().orElse(null);
        Assertions.assertNotNull(legacyConsumer, "Should find legacy-app consumer");
        Assertions.assertEquals(1, legacyConsumer.getVersionsBehind());
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
        waitForFlush();

        DeprecationReadiness readiness = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).versions()
                .byVersion("1").deprecationReadiness().get();
        Assertions.assertNotNull(readiness);
        Assertions.assertFalse(readiness.getSafeToDeprecate(), "Should not be safe to deprecate");
        Assertions.assertFalse(readiness.getActiveConsumers().isEmpty());
        Assertions.assertEquals("active-consumer", readiness.getActiveConsumers().get(0).getClientId());
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
        waitForFlush();

        ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).get();
        Assertions.assertNotNull(metrics);
        Assertions.assertFalse(metrics.getVersions().isEmpty());
        Assertions.assertTrue(metrics.getVersions().get(0).getTotalFetches() < 10,
                "Dedup should reduce 10 fetches to fewer events");
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
        waitForFlush();

        ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).get();
        Assertions.assertTrue(metrics.getVersions().isEmpty(),
                "Invalid clientId should not be recorded");
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
        waitForFlush();

        ArtifactUsageMetrics metrics = clientV3.admin().usage().artifacts()
                .byGroupId(groupId).byArtifactId(artifactId).get();
        Assertions.assertTrue(metrics.getVersions().isEmpty(),
                "Fetches without X-Registry-Client-Id should not be tracked");
    }

    private void fetchSchemaWithTelemetry(long globalId, String clientId, String operation)
            throws Exception {
        String url = "http://localhost:" + testPort + "/apis/registry/v3/ids/globalIds/" + globalId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Registry-Client-Id", clientId)
                .header("X-Registry-Operation", operation)
                .GET()
                .build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        Assertions.assertEquals(200, response.statusCode(), "Schema fetch should return 200");
    }

    private void waitForFlush() throws InterruptedException {
        Thread.sleep(10_000);
    }
}
