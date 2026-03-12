package io.apicurio.registry.search;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.DownloadRef;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

/**
 * Integration tests that verify the Elasticsearch search index is correctly rebuilt after
 * importing data via the admin API. When data is imported, the low-level storage methods
 * bypass the per-entity CDI events, so a full reindex must be triggered. These tests confirm
 * that imported artifacts are discoverable via the search index.
 *
 * <p>Uses the same {@link ElasticsearchSearchTestProfile} as other search tests so that
 * Quarkus Dev Services starts an Elasticsearch container.</p>
 */
@QuarkusTest
@TestProfile(ElasticsearchSearchTestProfile.class)
public class ImportReindexSearchTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void testImportDataReindexesSearchIndex() throws Exception {
        String group = TestUtils.generateGroupId();

        // Step 1: Create artifacts so there is data to export
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "testImportReindex_api-" + idx;
            createArtifact(group, artifactId, ArtifactType.OPENAPI,
                    "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"API " + idx + "\"}}",
                    ContentTypes.APPLICATION_JSON);
        }

        // Verify all 5 are searchable before export
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(5, results.getCount());

        // Step 2: Export all data
        File exportFile = exportData();

        // Step 3: Delete all data (clears both DB and search index)
        storage.deleteAllUserData();

        // Verify the search index is now empty for this group
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(0, results.getCount());

        // Step 4: Import the data back — this should trigger a reindex
        importData(exportFile);

        // Step 5: Verify all 5 artifacts are searchable again via the index
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(5, results.getCount());
        for (SearchedVersion version : results.getVersions()) {
            Assertions.assertEquals(group, version.getGroupId());
        }
    }

    @Test
    public void testImportDataReindexesWithMetadata() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create an artifact and set metadata (name, description, labels)
        CreateArtifactResponse car = createArtifact(group, "testImportReindexMeta_api-1",
                ArtifactType.OPENAPI, "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setName("Pet Store API");
        emd.setDescription("An API for managing pets");
        emd.setLabels(new Labels());
        emd.getLabels().setAdditionalData(Map.of("env", "production", "team", "platform"));
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testImportReindexMeta_api-1")
                .versions().byVersionExpression(car.getVersion().getVersion()).put(emd);

        // Create a second artifact with different metadata
        CreateArtifactResponse car2 = createArtifact(group, "testImportReindexMeta_api-2",
                ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[]}",
                ContentTypes.APPLICATION_JSON);
        EditableVersionMetaData emd2 = new EditableVersionMetaData();
        emd2.setName("User Events Schema");
        emd2.setDescription("Avro schema for user events");
        clientV3.groups().byGroupId(group).artifacts().byArtifactId("testImportReindexMeta_api-2")
                .versions().byVersionExpression(car2.getVersion().getVersion()).put(emd2);

        // Export, delete, import
        File exportFile = exportData();
        storage.deleteAllUserData();
        importData(exportFile);

        // Verify search by name works after import
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.name = "Pet Store";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("Pet Store API", results.getVersions().get(0).getName());

        // Verify search by description works after import
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.description = "user events";
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testImportReindexMeta_api-2",
                results.getVersions().get(0).getArtifactId());

        // Verify search by artifact type works after import
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testImportReindexMeta_api-2",
                results.getVersions().get(0).getArtifactId());

        // Verify search by label works after import
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.labels = new String[] { "env:production" };
        });
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("testImportReindexMeta_api-1",
                results.getVersions().get(0).getArtifactId());
    }

    @Test
    public void testImportDataReindexesMultipleVersions() throws Exception {
        String group = TestUtils.generateGroupId();

        // Create an artifact with multiple versions
        createArtifact(group, "testImportReindexVersions_api-1", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, "testImportReindexVersions_api-1",
                "{\"openapi\":\"3.0.1\"}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, "testImportReindexVersions_api-1",
                "{\"openapi\":\"3.1.0\"}", ContentTypes.APPLICATION_JSON);

        // Create another single-version artifact
        createArtifact(group, "testImportReindexVersions_api-2", ArtifactType.OPENAPI,
                "{\"openapi\":\"3.0.0\"}", ContentTypes.APPLICATION_JSON);

        // Verify all 4 versions are indexed
        VersionSearchResults results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(4, results.getCount());

        // Export, delete, import
        File exportFile = exportData();
        storage.deleteAllUserData();
        importData(exportFile);

        // Verify all 4 versions are searchable after import
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
        });
        Assertions.assertEquals(4, results.getCount());

        // Verify searching for specific artifact returns correct version count
        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testImportReindexVersions_api-1";
        });
        Assertions.assertEquals(3, results.getCount());

        results = clientV3.search().versions().get(config -> {
            config.queryParameters.groupId = group;
            config.queryParameters.artifactId = "testImportReindexVersions_api-2";
        });
        Assertions.assertEquals(1, results.getCount());
    }

    /**
     * Exports all data from the registry to a temporary ZIP file.
     *
     * @return the temporary file containing the export
     */
    private File exportData() throws Exception {
        DownloadRef downloadRef = clientV3.admin().export().get();
        String href = downloadRef.getHref();
        String urlString = String.format("http://localhost:%s%s", testPort, href);
        URL url = new URL(urlString);
        File tempFile = File.createTempFile("ImportReindexTest", ".zip");
        try (InputStream in = url.openStream()) {
            Files.deleteIfExists(tempFile.toPath());
            Files.copy(in, tempFile.toPath());
        }
        return tempFile;
    }

    /**
     * Imports data from a ZIP file into the registry.
     *
     * @param exportFile the ZIP file to import
     */
    private void importData(File exportFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(exportFile)) {
            clientV3.admin().importEscaped().post(fis, config -> {
                config.headers.putIfAbsent("Content-Type", Set.of("application/zip"));
            });
        } finally {
            Files.deleteIfExists(exportFile.toPath());
        }
    }
}
