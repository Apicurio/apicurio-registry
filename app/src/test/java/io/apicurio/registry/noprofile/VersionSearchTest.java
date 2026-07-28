package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
public class VersionSearchTest extends AbstractResourceTestBase {

    @Test
    void testFilterByArtifactType() throws Exception {
        String groupId = TestUtils.generateGroupId();

        createArtifact(groupId, "avro-artifact", ArtifactType.AVRO, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, "avro-artifact", "{ }", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "json-artifact", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.artifactType = ArtifactType.JSON;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

    }

    @Test
    void testFilterByLabels() throws Exception {
        String groupId = TestUtils.generateGroupId();

        CreateArtifactResponse car1 = createArtifact(groupId, "labels-artifact-1", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifact(groupId, "labels-artifact-2", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        EditableVersionMetaData emd = new EditableVersionMetaData();
        Labels labels = new Labels();
        labels.setAdditionalData(Map.of("env", "dev"));
        emd.setLabels(labels);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("labels-artifact-1").versions()
                .byVersionExpression(car1.getVersion().getVersion()).put(emd);

        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "env" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "env:" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "env:dev" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.labels = new String[] { "env:prod" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(0, results.getCount());
    }

    /**
     * Tests that version name search supports exact matching and wildcard patterns, consistent with
     * artifact name search (see {@code ArtifactSearchTest#testArtifactNameSearchWildcards}, introduced
     * in #6298). Verifies the fix for #8002, where version name search previously performed substring
     * matching and treated {@code *} literally. Now:
     * <ul>
     * <li>"name" performs exact match</li>
     * <li>"name*" matches names starting with "name"</li>
     * <li>"*name" matches names ending with "name"</li>
     * <li>"*name*" matches names containing "name"</li>
     * </ul>
     */
    @Test
    void testVersionNameSearchWildcards() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Create artifacts whose first version has various names. The artifact IDs deliberately do
        // not contain "name"/"test" so the counts are driven purely by the version name matching.
        createArtifact(groupId, "va-1", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.getFirstVersion().setName("name");
                });
        createArtifact(groupId, "va-2", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.getFirstVersion().setName("name-test");
                });
        createArtifact(groupId, "va-3", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.getFirstVersion().setName("test-name");
                });
        createArtifact(groupId, "va-4", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.getFirstVersion().setName("some-name-here");
                });
        createArtifact(groupId, "va-5", ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON,
                (createArtifact) -> {
                    createArtifact.getFirstVersion().setName("other");
                });

        // Exact match - only the version named exactly "name"
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "name";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount(), "Exact match for 'name' should return 1 result");

        // End wildcard - names starting with "name"
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "name*";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount(),
                "Wildcard 'name*' should return 2 results (name, name-test)");

        // Start wildcard - names ending with "name"
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "*name";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount(),
                "Wildcard '*name' should return 2 results (name, test-name)");

        // Both wildcards - names containing "name"
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "*name*";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(4, results.getCount(),
                "Wildcard '*name*' should return 4 results (name, name-test, test-name, some-name-here)");

        // Exact match with no results
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "nonexistent";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(0, results.getCount(),
                "Exact match for 'nonexistent' should return 0 results");

        // Name filter also matches the artifact ID, consistent with artifact name search
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "va-1";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount(),
                "Name filter should also match by artifact ID (va-1)");

        // A wildcard-only value matches everything in scope and must not error
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = groupId;
            config.queryParameters.name = "*";
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount(), "'*' should match all versions in the group");
    }
}