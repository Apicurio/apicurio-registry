package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@link ElasticsearchStartupIndexer} and
 * {@link io.apicurio.registry.metrics.health.readiness.ElasticsearchIndexReadinessCheck}.
 * Verifies that the startup indexer completes successfully, the readiness check reports UP,
 * and that the Elasticsearch search decorator routes searches correctly after startup.
 * Quarkus Dev Services auto-starts an Elasticsearch container for the tests.
 */
@QuarkusTest
@TestProfile(ElasticsearchSearchTestProfile.class)
public class ElasticsearchStartupIndexerTest extends AbstractResourceTestBase {

    @Inject
    ElasticsearchStartupIndexer startupIndexer;

    /**
     * Verifies the startup indexer reports ready after Quarkus startup. Since the database
     * is empty at startup, the indexer runs the reindex path (finds empty index, queries
     * storage which returns zero versions) and marks itself ready.
     */
    @Test
    public void testStartupIndexerIsReady() {
        Assertions.assertTrue(startupIndexer.isReady(),
                "Startup indexer should be ready after application startup");
    }

    /**
     * Verifies the ElasticsearchIndexReadinessCheck reports UP via the Quarkus health
     * endpoint. Health checks are served on the management port, not the main HTTP port.
     */
    @Test
    public void testReadinessCheckReportsUp() {
        RestAssured.given()
                .baseUri("http://localhost:" + managementTestPort)
                .when().get("/health/ready")
                .then()
                .statusCode(200);
    }

    /**
     * Verifies that the ElasticsearchIndexReadinessCheck is present in the health check
     * response and reports status UP. Health checks are served on the management port.
     */
    @Test
    public void testElasticsearchReadinessCheckPresent() {
        String body = RestAssured.given()
                .baseUri("http://localhost:" + managementTestPort)
                .when().get("/health/ready")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertTrue(body.contains("ElasticsearchIndexReadinessCheck"),
                "Health response should include ElasticsearchIndexReadinessCheck");
    }

    /**
     * Verifies that after creating artifacts via the REST API, searches return results
     * through the Elasticsearch index. Since indexing is synchronous (shared cluster),
     * newly created artifacts are indexed immediately and should be searchable.
     */
    @Test
    public void testSearchWorksAfterStartupIndexerCompletes() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create several artifacts
        for (int idx = 0; idx < 3; idx++) {
            createArtifact(group, "testStartupSearch_api-" + idx, ArtifactType.OPENAPI,
                    "{\"openapi\":\"3.0.0\",\"idx\":" + idx + "}",
                    ContentTypes.APPLICATION_JSON);
        }

        // Verify search returns results (routed through ES since startup indexer is ready)
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(3, results.getCount(),
                "Should find all 3 versions via search");
    }
}
