package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.storage.impl.search.LuceneIndexWriter;
import io.apicurio.registry.storage.impl.search.LuceneStartupIndexer;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@link LuceneStartupIndexer} and {@link
 * io.apicurio.registry.metrics.health.readiness.LuceneIndexReadinessCheck}. Verifies that the
 * startup indexer completes successfully, the readiness check reports UP, and that the Lucene
 * search decorator routes searches correctly after startup.
 */
@QuarkusTest
@TestProfile(LuceneSearchTestProfile.class)
public class LuceneStartupIndexerTest extends AbstractResourceTestBase {

    /**
     * Override to set RestAssured base URI to the server root (without "/apis") so that
     * health check endpoint tests can access "/health/ready" directly.
     */
    @Override
    protected void setupRestAssured() {
        RestAssured.baseURI = "http://localhost:" + testPort;
        RestAssured.registerParser(ArtifactMediaTypes.BINARY.toString(), Parser.JSON);
    }

    @Inject
    LuceneStartupIndexer startupIndexer;

    @Inject
    LuceneIndexWriter indexWriter;

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
     * Verifies the completed timestamp is set after startup. A non-zero timestamp confirms
     * the indexer ran through the reindex (or skip) path and recorded completion.
     */
    @Test
    public void testCompletedTimestampIsSet() {
        Assertions.assertTrue(startupIndexer.getCompletedTimestamp() > 0,
                "Completed timestamp should be set after startup");
    }

    /**
     * Verifies the LuceneIndexReadinessCheck reports UP via the Quarkus health endpoint.
     * This confirms the readiness health check wiring is correct and that it unblocks
     * after the startup indexer finishes.
     */
    @Test
    public void testReadinessCheckReportsUp() {
        RestAssured.given()
                .when().get("/health/ready")
                .then()
                .statusCode(200);
    }

    /**
     * Verifies that the LuceneIndexReadinessCheck is present in the health check response
     * and reports status UP.
     */
    @Test
    public void testLuceneReadinessCheckPresent() {
        String body = RestAssured.given()
                .when().get("/health/ready")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertTrue(body.contains("LuceneIndexReadinessCheck"),
                "Health response should include LuceneIndexReadinessCheck");
    }

    /**
     * Verifies that after creating artifacts via the REST API, searches return results
     * through the Lucene index. Since the test profile uses synchronous indexing, newly
     * created artifacts are indexed immediately and should be searchable.
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

        // Verify search returns results (routed through Lucene since startup indexer is ready)
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(3, results.getCount(),
                "Should find all 3 versions via search");
    }

    /**
     * Verifies that the Lucene index contains documents after creating artifacts. This
     * confirms the synchronous indexing path works correctly after the startup indexer
     * has completed and the decorator is routing to Lucene.
     */
    @Test
    public void testIndexContainsDocumentsAfterCreation() throws Exception {
        String group = TestUtils.generateGroupId();

        int countBefore = indexWriter.getDocumentCount();

        createArtifact(group, "testIndexDocs_api-1", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifact(group, "testIndexDocs_api-2", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.1\"}", ContentTypes.APPLICATION_JSON);

        int countAfter = indexWriter.getDocumentCount();
        Assertions.assertTrue(countAfter >= countBefore + 2,
                "Index should contain at least 2 more documents after artifact creation");
    }
}
