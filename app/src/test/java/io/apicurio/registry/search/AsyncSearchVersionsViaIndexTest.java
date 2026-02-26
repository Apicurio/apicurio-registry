package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Integration tests for the asynchronous Lucene search index pipeline. Uses a QuarkusTestProfile
 * that enables Lucene search with asynchronous (polling-based) updates. The first test creates
 * all test data and waits for the async indexer to pick it up; subsequent tests only search and
 * assert against the already-indexed data.
 */
@QuarkusTest
@TestProfile(AsyncLuceneSearchTestProfile.class)
@TestMethodOrder(OrderAnnotation.class)
public class AsyncSearchVersionsViaIndexTest extends AbstractResourceTestBase {

    private static final String OPENAPI_CONTENT = """
        {
          "openapi": "3.0.3",
          "info": {
            "title": "Empty API",
            "description": "An empty API definition. Zipper",
            "version": "1.0.0"
          },
          "paths": {}
        }
    """;

    private static final String AVRO_CONTENT = """
        {
          "type": "record",
          "name": "EmptyRecord",
          "namespace": "io.apicurio.example",
          "doc": "An empty Avro record schema. Zipper",
          "fields": []
        }
    """;

    /**
     * Creates test artifacts and waits for the async indexer to pick them up.
     *
     * <p>Test data:
     * <ul>
     *   <li>Group 1: 3 OpenAPI artifacts with names, descriptions, and labels</li>
     *   <li>Group 2: 2 Avro artifacts with different metadata</li>
     * </ul>
     */
    @Test
    public void testAsyncIndexPopulation() throws Exception {
        String group1 = TestUtils.generateGroupId();
        String group2 = TestUtils.generateGroupId();

        // --- Group 1: 3 OpenAPI artifacts ---
        CreateArtifactResponse car1 = createArtifact(group1, "async-openapi-1",
                ArtifactType.OPENAPI, OPENAPI_CONTENT, ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd1 = new EditableVersionMetaData();
        emd1.setName("Pet Store API");
        emd1.setDescription("API for managing pet inventory and orders");
        emd1.setLabels(new Labels());
        emd1.getLabels().setAdditionalData(Map.of("env", "production", "team", "platform"));
        clientV3.groups().byGroupId(group1).artifacts().byArtifactId("async-openapi-1")
                .versions().byVersionExpression(car1.getVersion().getVersion()).put(emd1);

        CreateArtifactResponse car2 = createArtifact(group1, "async-openapi-2",
                ArtifactType.OPENAPI, OPENAPI_CONTENT, ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd2 = new EditableVersionMetaData();
        emd2.setName("User Management API");
        emd2.setDescription("API for user authentication and authorization");
        emd2.setLabels(new Labels());
        emd2.getLabels().setAdditionalData(Map.of("env", "staging"));
        clientV3.groups().byGroupId(group1).artifacts().byArtifactId("async-openapi-2")
                .versions().byVersionExpression(car2.getVersion().getVersion()).put(emd2);

        CreateArtifactResponse car3 = createArtifact(group1, "async-openapi-3",
                ArtifactType.OPENAPI, OPENAPI_CONTENT, ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd3 = new EditableVersionMetaData();
        emd3.setName("Pet Inventory Service");
        emd3.setDescription("Service for tracking pet inventory levels");
        clientV3.groups().byGroupId(group1).artifacts().byArtifactId("async-openapi-3")
                .versions().byVersionExpression(car3.getVersion().getVersion()).put(emd3);

        // --- Group 2: 2 Avro artifacts ---
        CreateArtifactResponse car4 = createArtifact(group2, "async-avro-1",
                ArtifactType.AVRO,
                AVRO_CONTENT,
                ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd4 = new EditableVersionMetaData();
        emd4.setName("Customer Event Schema");
        emd4.setDescription("Avro schema for customer events");
        clientV3.groups().byGroupId(group2).artifacts().byArtifactId("async-avro-1")
                .versions().byVersionExpression(car4.getVersion().getVersion()).put(emd4);

        CreateArtifactResponse car5 = createArtifact(group2, "async-avro-2",
                ArtifactType.AVRO,
                AVRO_CONTENT,
                ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd5 = new EditableVersionMetaData();
        emd5.setName("Order Event Schema");
        emd5.setDescription("Avro schema for order processing events");
        clientV3.groups().byGroupId(group2).artifacts().byArtifactId("async-avro-2")
                .versions().byVersionExpression(car5.getVersion().getVersion()).put(emd5);

        // Wait for the async indexer to pick up all 5 artifacts.
        // The AsynchronousLuceneIndexUpdater has a configured 1s initial delay + 3s polling interval,
        // so 30s timeout is generous enough to cover startup overhead.
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    VersionSearchResults results = clientV3.search().versions().get(config -> {
                        config.queryParameters.content = "Zipper";
                    });
                    Assertions.assertEquals(5, results.getCount(),
                            "Expected 5 versions available after async indexing");
                });

        /*
         * Verifies that searching by groupId returns the correct number of results for each group.
         */
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group1;
        });
        Assertions.assertEquals(3, results.getCount());
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertEquals(group1, version.getGroupId());
        }

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group2;
        });
        Assertions.assertEquals(2, results.getCount());
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertEquals(group2, version.getGroupId());
        }

        /*
         * Verifies that searching by artifact type correctly filters OPENAPI vs AVRO artifacts.
         */
        VersionSearchResults openapiResults = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group1;
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });
        Assertions.assertEquals(3, openapiResults.getCount());
        for (SearchedVersion v : openapiResults.getVersions()) {
            Assertions.assertEquals(ArtifactType.OPENAPI, v.getArtifactType());
        }

        VersionSearchResults avroResults = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group2;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertEquals(2, avroResults.getCount());
        for (SearchedVersion v : avroResults.getVersions()) {
            Assertions.assertEquals(ArtifactType.AVRO, v.getArtifactType());
        }

        /*
         * Verify that the full text indexing worked by searching for "Zipper" in the content
         * (which should exist in the content of all 5 artifact versions).
         */
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.content = "Zipper";
        });
        Assertions.assertEquals(5, results.getCount(),
                "Expected 5 versions available after async indexing");


        // Now let's add a couple more artifacts and test again.
        // /////////////////////////////////////////////////////
        String group3 = TestUtils.generateGroupId();

        // --- Group 3: 1 Avro artifact and 1 OpenApi artifact ---
        createArtifact(group3, "async-avro-3-1",
                ArtifactType.AVRO,
                AVRO_CONTENT,
                ContentTypes.APPLICATION_JSON);

        createArtifact(group3, "async-openapi-3-2",
                ArtifactType.OPENAPI, OPENAPI_CONTENT, ContentTypes.APPLICATION_JSON);

        // Wait for the async indexer to pick up all 7 artifacts.
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    VersionSearchResults iresults = clientV3.search().versions().get(config -> {
                        config.queryParameters.content = "Zipper";
                    });
                    Assertions.assertEquals(7, iresults.getCount(),
                            "Expected 7 versions available after async indexing");
                });

        /*
         * Verify that the full text indexing worked by searching for "Zipper" in the content
         * (which should exist in the content of all 5 artifact versions).
         */
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.content = "Zipper";
        });
        Assertions.assertEquals(7, results.getCount(),
                "Expected 7 versions available after async indexing");

    }
}
