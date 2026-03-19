package io.apicurio.tests.search;

import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Integration tests for the Elasticsearch-based search feature. These tests require a running
 * Elasticsearch cluster and the search feature to be enabled in the registry configuration.
 * They are excluded from normal test runs and can be executed via the {@code -Psearch} maven
 * profile or by selecting the {@code search} JUnit tag.
 */
@Tag(Constants.SEARCH)
@QuarkusIntegrationTest
public class SearchIT extends ApicurioRegistryBaseIT {

    private static final String GROUP = "SearchIT";

    /**
     * Verifies that artifacts can be found by searching their content using full-text search.
     * This is the primary capability that requires the Elasticsearch index.
     */
    @Test
    public void testSearchVersionsByContent() throws Exception {
        String artifactId1 = "searchByContent-petstore-" + TestUtils.generateArtifactId();
        String artifactId2 = "searchByContent-users-" + TestUtils.generateArtifactId();

        String content1 = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Pet Store API\","
                + "\"description\":\"An API for managing pets in the store\"}}";
        String content2 = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"User Management API\","
                + "\"description\":\"An API for managing user accounts\"}}";

        createArtifact(GROUP, artifactId1, ArtifactType.OPENAPI, content1,
                ContentTypes.APPLICATION_JSON, null, null);
        createArtifact(GROUP, artifactId2, ArtifactType.OPENAPI, content2,
                ContentTypes.APPLICATION_JSON, null, null);

        // Allow time for ES indexing
        Thread.sleep(3000);

        // Search for "pets" — should find the pet store API
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.content = "pets";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for content search 'pets', got: "
                            + results.getCount());
        });

        // Search for "user accounts" — should find the user management API
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.content = "user accounts";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for content search 'user accounts', got: "
                            + results.getCount());
        });

        // Search for a term not in any content — should return 0
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.content = "nonexistent-xyz-term-12345";
            });
            Assertions.assertEquals(0, results.getCount(),
                    "Expected 0 results for non-matching content search");
        });
    }

    /**
     * Verifies that basic version search filters (groupId, artifactId, name) work correctly
     * when the search feature is enabled. These go through SQL, but this confirms they still
     * function properly alongside the ES decorator.
     */
    @Test
    public void testSearchVersionsByMetadata() throws Exception {
        String artifactId = "searchByMeta-" + TestUtils.generateArtifactId();

        String content = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Test\"}}";
        CreateArtifactResponse car = createArtifact(GROUP, artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON, null, null);

        // Set a name on the version
        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setName("Search Smoke Test API");
        registryClient.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);

        // Search by groupId + artifactId
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.artifactId = artifactId;
            });
            Assertions.assertEquals(1, results.getCount(),
                    "Expected 1 result when searching by groupId + artifactId");
            Assertions.assertEquals(artifactId,
                    results.getVersions().get(0).getArtifactId());
        });

        // Search by name
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.name = "Search Smoke Test";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result when searching by name");
        });
    }

    /**
     * Verifies that label-based filtering works when the search feature is enabled.
     */
    @Test
    public void testSearchVersionsByLabels() throws Exception {
        String artifactId = "searchByLabels-" + TestUtils.generateArtifactId();

        String content = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Labeled API\"}}";
        CreateArtifactResponse car = createArtifact(GROUP, artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON, null, null);

        // Add labels
        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setLabels(new Labels());
        emd.getLabels().setAdditionalData(Map.of(
                "env", "search-smoke-test",
                "team", "platform"));
        registryClient.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);

        // Search by label key and value
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.labels = new String[] { "env:search-smoke-test" };
            });
            Assertions.assertEquals(1, results.getCount(),
                    "Expected 1 result when searching by label key:value");
            Assertions.assertEquals(artifactId,
                    results.getVersions().get(0).getArtifactId());
        });
    }

    /**
     * Verifies that deleting an artifact removes its versions from ES search results.
     */
    @Test
    public void testDeleteArtifactRemovesFromSearch() throws Exception {
        String artifactId = "searchDelete-" + TestUtils.generateArtifactId();

        String content = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Deletable API\"}}";
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content,
                ContentTypes.APPLICATION_JSON, null, null);

        // Confirm it shows up in search
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.artifactId = artifactId;
            });
            Assertions.assertEquals(1, results.getCount(),
                    "Expected artifact to appear in search results before deletion");
        });

        // Delete the artifact
        registryClient.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId).delete();

        // Allow time for ES index update
        Thread.sleep(3000);

        // Verify it no longer appears in search results
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.artifactId = artifactId;
            });
            Assertions.assertEquals(0, results.getCount(),
                    "Expected artifact to be removed from search results after deletion");
        });
    }

    /**
     * Verifies that OpenAPI artifacts can be found using the structured content search filter.
     * The structure filter searches indexed elements such as schemas, paths, operations, and
     * tags extracted from OpenAPI documents. Supports three query formats:
     * {@code type:kind:name}, {@code kind:name}, or just {@code name}.
     */
    @Test
    public void testSearchVersionsByStructure_OpenApi() throws Exception {
        String artifactId1 = "structOpenApi-petstore-" + TestUtils.generateArtifactId();
        String artifactId2 = "structOpenApi-users-" + TestUtils.generateArtifactId();

        // OpenAPI with Pet and Order schemas, /pets path, getPets operation
        String openApi1 = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": { \"title\": \"Pet Store\", \"version\": \"1.0\" },\n"
                + "  \"paths\": {\n"
                + "    \"/pets\": {\n"
                + "      \"get\": { \"operationId\": \"getPets\", \"responses\": { \"200\": { \"description\": \"OK\" } } }\n"
                + "    }\n"
                + "  },\n"
                + "  \"components\": {\n"
                + "    \"schemas\": {\n"
                + "      \"Pet\": { \"type\": \"object\", \"properties\": { \"name\": { \"type\": \"string\" } } },\n"
                + "      \"Order\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"integer\" } } }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        // OpenAPI with User schema and /users path
        String openApi2 = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": { \"title\": \"User Service\", \"version\": \"1.0\" },\n"
                + "  \"paths\": {\n"
                + "    \"/users\": {\n"
                + "      \"get\": { \"operationId\": \"listUsers\", \"responses\": { \"200\": { \"description\": \"OK\" } } }\n"
                + "    }\n"
                + "  },\n"
                + "  \"components\": {\n"
                + "    \"schemas\": {\n"
                + "      \"User\": { \"type\": \"object\", \"properties\": { \"email\": { \"type\": \"string\" } } }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        createArtifact(GROUP, artifactId1, ArtifactType.OPENAPI, openApi1,
                ContentTypes.APPLICATION_JSON, null, null);
        createArtifact(GROUP, artifactId2, ArtifactType.OPENAPI, openApi2,
                ContentTypes.APPLICATION_JSON, null, null);

        // Allow time for ES indexing
        Thread.sleep(3000);

        // Search by full faceted format: openapi:schema:Pet — should match only petstore
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "openapi:schema:Pet";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'openapi:schema:Pet'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId1)),
                    "Expected petstore artifact in results for 'openapi:schema:Pet'");
        });

        // Search by kind:name format: schema:User — should match only user service
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "schema:User";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'schema:User'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId2)),
                    "Expected user service artifact in results for 'schema:User'");
        });

        // Search by path: path:/pets — should match only petstore
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "openapi:path:/pets";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'openapi:path:/pets'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId1)),
                    "Expected petstore artifact in results for 'openapi:path:/pets'");
        });

        // Search by operation: operation:getPets — should match only petstore
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "operation:getPets";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'operation:getPets'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId1)),
                    "Expected petstore artifact in results for 'operation:getPets'");
        });

        // Search for a non-existent schema — should return 0
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "openapi:schema:NonExistentSchema99";
            });
            Assertions.assertEquals(0, results.getCount(),
                    "Expected 0 results for non-existent structured element");
        });
    }

    /**
     * Verifies that JSON Schema artifacts can be found using the structured content search
     * filter. The structure filter searches indexed elements such as properties, definitions,
     * and required fields extracted from JSON Schema documents.
     */
    @Test
    public void testSearchVersionsByStructure_JsonSchema() throws Exception {
        String artifactId1 = "structJson-address-" + TestUtils.generateArtifactId();
        String artifactId2 = "structJson-product-" + TestUtils.generateArtifactId();

        // JSON Schema with Address properties and a GeoLocation definition
        String jsonSchema1 = "{\n"
                + "  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n"
                + "  \"$id\": \"https://example.com/address\",\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"street\": { \"type\": \"string\" },\n"
                + "    \"city\": { \"type\": \"string\" },\n"
                + "    \"zipCode\": { \"type\": \"string\" },\n"
                + "    \"country\": { \"type\": \"string\" }\n"
                + "  },\n"
                + "  \"required\": [\"street\", \"city\"],\n"
                + "  \"$defs\": {\n"
                + "    \"GeoLocation\": {\n"
                + "      \"type\": \"object\",\n"
                + "      \"properties\": {\n"
                + "        \"latitude\": { \"type\": \"number\" },\n"
                + "        \"longitude\": { \"type\": \"number\" }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        // JSON Schema with Product properties and a Category definition
        String jsonSchema2 = "{\n"
                + "  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n"
                + "  \"$id\": \"https://example.com/product\",\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"productName\": { \"type\": \"string\" },\n"
                + "    \"price\": { \"type\": \"number\" },\n"
                + "    \"sku\": { \"type\": \"string\" }\n"
                + "  },\n"
                + "  \"required\": [\"productName\", \"price\"],\n"
                + "  \"$defs\": {\n"
                + "    \"Category\": {\n"
                + "      \"type\": \"object\",\n"
                + "      \"properties\": {\n"
                + "        \"name\": { \"type\": \"string\" }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        createArtifact(GROUP, artifactId1, ArtifactType.JSON, jsonSchema1,
                ContentTypes.APPLICATION_JSON, null, null);
        createArtifact(GROUP, artifactId2, ArtifactType.JSON, jsonSchema2,
                ContentTypes.APPLICATION_JSON, null, null);

        // Allow time for ES indexing
        Thread.sleep(3000);

        // Search by full faceted format: json:property:street — should match only address
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "json:property:street";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'json:property:street'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId1)),
                    "Expected address artifact in results for 'json:property:street'");
        });

        // Search by kind:name format: definition:GeoLocation — should match only address
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "definition:GeoLocation";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'definition:GeoLocation'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId1)),
                    "Expected address artifact in results for 'definition:GeoLocation'");
        });

        // Search by definition:Category — should match only product
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "definition:Category";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'definition:Category'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId2)),
                    "Expected product artifact in results for 'definition:Category'");
        });

        // Search by property: property:productName — should match only product
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "property:productName";
            });
            Assertions.assertTrue(results.getCount() >= 1,
                    "Expected at least 1 result for structure 'property:productName'");
            Assertions.assertTrue(
                    results.getVersions().stream()
                            .anyMatch(v -> v.getArtifactId().equals(artifactId2)),
                    "Expected product artifact in results for 'property:productName'");
        });

        // Search for a non-existent definition — should return 0
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.groupId = GROUP;
                config.queryParameters.structure = "json:definition:NonExistentDef99";
            });
            Assertions.assertEquals(0, results.getCount(),
                    "Expected 0 results for non-existent structured element");
        });
    }

    /**
     * Verifies that search results contain the expected fields (globalId, contentId, name,
     * description, labels, etc.) when the search feature is enabled.
     */
    @Test
    public void testSearchResultsFieldMapping() throws Exception {
        String artifactId = "searchFields-" + TestUtils.generateArtifactId();

        String content = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Fields Test\"}}";
        CreateArtifactResponse car = createArtifact(GROUP, artifactId, ArtifactType.OPENAPI,
                content, ContentTypes.APPLICATION_JSON, null, null);
        Long globalId = car.getVersion().getGlobalId();

        // Update metadata
        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setName("Fields Test API");
        emd.setDescription("API to verify search result field mapping");
        emd.setLabels(new Labels());
        emd.getLabels().setAdditionalData(Map.of("env", "test"));
        registryClient.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);

        // Search and verify fields
        retry(() -> {
            VersionSearchResults results = registryClient.search().versions().get(config -> {
                config.queryParameters.globalId = globalId;
            });
            Assertions.assertEquals(1, results.getCount());

            SearchedVersion version = results.getVersions().get(0);
            Assertions.assertEquals(globalId, version.getGlobalId());
            Assertions.assertNotNull(version.getContentId());
            Assertions.assertEquals(GROUP, version.getGroupId());
            Assertions.assertEquals(artifactId, version.getArtifactId());
            Assertions.assertEquals("1", version.getVersion());
            Assertions.assertEquals(ArtifactType.OPENAPI, version.getArtifactType());
            Assertions.assertEquals("Fields Test API", version.getName());
            Assertions.assertEquals("API to verify search result field mapping",
                    version.getDescription());
            Assertions.assertNotNull(version.getCreatedOn());
            Assertions.assertNotNull(version.getLabels());
            Assertions.assertEquals("test",
                    version.getLabels().getAdditionalData().get("env"));
        });
    }
}
