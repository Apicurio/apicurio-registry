package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.search.versions.VersionsRequestBuilder.PostRequestConfiguration;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.function.Consumer;

import static io.apicurio.registry.types.ArtifactType.AVRO;
import static io.apicurio.registry.types.ContentTypes.APPLICATION_JSON;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearchAvroWithConfigCombinationsTest extends AbstractResourceTestBase {
    private static final String GROUP_ID = "io.apicurio.registry.avro.example";
    private static final String ARTIFACT_ID = "ExampleRecord";
    private static final String ARTIFACT_ID_EMBEDDED = "UserType";
    private static final String ARTIFACT_ID_WITH_REF = "ExampleRecordWithRef";
    private String schemaContent;
    private String schemaContentEmbedded;
    private String schemaContentWithRef;
    private String schemaContentDereferenced;

    @BeforeAll
    public void setupTestData() throws Exception {
        schemaContent = resourceToString("schema.avsc");
        createArtifact(GROUP_ID, ARTIFACT_ID, AVRO, schemaContent, APPLICATION_JSON);
        schemaContentEmbedded = resourceToString("schema-embedded.avsc");
        CreateArtifactResponse reference = createArtifact(GROUP_ID, ARTIFACT_ID_EMBEDDED, AVRO, schemaContentEmbedded, APPLICATION_JSON);
        schemaContentWithRef = resourceToString("schema-with-reference.avsc");
        List<ArtifactReference> artifactReferences = List.of(new ArtifactReference(reference.getArtifact().getGroupId(),
                reference.getArtifact().getArtifactId(),
                reference.getVersion().getVersion(),
                "%s.%s".formatted(reference.getArtifact().getGroupId(), reference.getArtifact().getArtifactId())));
        createArtifactWithReferences(GROUP_ID,
                ARTIFACT_ID_WITH_REF,
                AVRO,
                schemaContentWithRef,
                APPLICATION_JSON,
                artifactReferences);
        // Load the dereferenced version - this simulates what Avro generated classes contain
        schemaContentDereferenced = resourceToString("schema-dereferenced.avsc");
    }

    @Test
    public void testSearchExampleRecordWithBasicConfig() {
        testSchemaSearch(this::getBasicSearchConfig);
    }

    @Test
    public void testSearchExampleRecordWithArtifactRefConfig() {
        testSchemaSearch(config -> getSearchConfigWithArtifactRef(config, ARTIFACT_ID));
    }

    @Test
    public void testSearchExampleRecordWithBasicAndCanonicalConfig() {
        testSchemaSearch(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
        });
    }

    @Test
    public void testSearchExampleRecordWithAllConfig() {
        testSchemaSearch(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
            getSearchConfigWithArtifactRef(config, ARTIFACT_ID);
        });
    }

    @Test
    public void testSearchUserTypeWithBasicConfig() {
        testSchemaSearchEmbedded(this::getBasicSearchConfig);
    }

    @Test
    public void testSearchUserTypeWithArtifactRefConfig() {
        testSchemaSearchEmbedded(config -> getSearchConfigWithArtifactRef(config, ARTIFACT_ID_EMBEDDED));
    }

    @Test
    public void testSearchUserTypeWithBasicAndCanonicalConfig() {
        testSchemaSearchEmbedded(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
        });
    }

    @Test
    public void testSearchUserTypeWithAllConfig() {
        testSchemaSearchEmbedded(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
            getSearchConfigWithArtifactRef(config, ARTIFACT_ID_EMBEDDED);
        });
    }

    @Test
    public void testSearchExampleRecordWithRefWithBasicConfig() {
        testSchemaSearchWithRef(this::getBasicSearchConfig);
    }

    @Test
    public void testSearchExampleRecordWithRefWithArtifactRefConfig() {
        testSchemaSearchWithRef(config -> getSearchConfigWithArtifactRef(config, ARTIFACT_ID_WITH_REF));
    }

    @Test
    public void testSearchExampleRecordWithRefWithBasicAndCanonicalConfig() {
        testSchemaSearchWithRef(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
        });
    }

    @Test
    public void testSearchExampleRecordWithRefWithAllConfig() {
        testSchemaSearchWithRef(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
            getSearchConfigWithArtifactRef(config, ARTIFACT_ID_WITH_REF);
        });
    }

    @Test
    public void testSearchDereferencedWithBasicAndCanonicalConfig() {
        testSchemaSearchDereferenced(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
        });
    }

    @Test
    public void testSearchDereferencedWithAllConfig() {
        testSchemaSearchDereferenced(config -> {
            getBasicSearchConfig(config);
            getCanonicalSearchConfig(config);
            getSearchConfigWithArtifactRef(config, ARTIFACT_ID_WITH_REF);
        });
    }

    private void testSchemaSearch(Consumer<PostRequestConfiguration> configConsumer) {
        performSchemaSearch(schemaContent, ARTIFACT_ID, configConsumer);
    }

    private void testSchemaSearchEmbedded(Consumer<PostRequestConfiguration> configConsumer) {
        performSchemaSearch(schemaContentEmbedded, ARTIFACT_ID_EMBEDDED, configConsumer);
    }

    private void testSchemaSearchWithRef(Consumer<PostRequestConfiguration> configConsumer) {
        performSchemaSearch(schemaContentWithRef, ARTIFACT_ID_WITH_REF, configConsumer);
    }

    private void testSchemaSearchDereferenced(Consumer<PostRequestConfiguration> configConsumer) {
        // Dereferenced schema should match the artifact with references
        // This simulates the scenario where Avro generated classes embed a dereferenced version
        performSchemaSearch(schemaContentDereferenced, ARTIFACT_ID_WITH_REF, configConsumer);
    }

    private void performSchemaSearch(String schemaContent, String expectedArtifactId,
                                     Consumer<PostRequestConfiguration> configConsumer) {
        VersionSearchResults results = clientV3.search().versions().post(asInputStream(schemaContent),
                APPLICATION_JSON, configConsumer);

        Assertions.assertEquals(1, results.getCount());

        SearchedVersion result = results.getVersions().get(0);

        Assertions.assertEquals(GROUP_ID, result.getGroupId());
        Assertions.assertEquals(expectedArtifactId, result.getArtifactId());
    }

    void getBasicSearchConfig(PostRequestConfiguration config) {
        Assertions.assertNotNull(config.queryParameters);
        config.queryParameters.artifactType = AVRO;
    }

    void getCanonicalSearchConfig(PostRequestConfiguration config) {
        Assertions.assertNotNull(config.queryParameters);
        config.queryParameters.canonical = true;
    }

    void getSearchConfigWithArtifactRef(PostRequestConfiguration config, String artifactId) {
        Assertions.assertNotNull(config.queryParameters);
        config.queryParameters.groupId = GROUP_ID;
        config.queryParameters.artifactId = artifactId;
    }
}

