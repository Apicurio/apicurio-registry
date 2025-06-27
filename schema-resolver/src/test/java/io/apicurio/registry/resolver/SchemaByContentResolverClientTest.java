package io.apicurio.registry.resolver;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static io.apicurio.registry.rest.client.models.VersionState.DISABLED;
import static io.apicurio.registry.rest.client.models.VersionState.ENABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SchemaByContentResolverClientTest {

    public static final String SCHEMA_CONTENT = """
            {
              "type": "record",
              "name": "artifactTestId",
              "namespace": "test.group.id",
              "fields": [
                {
                  "name": "userId",
                  "type": {
                    "type": "string",
                    "logicalType": "uuid"
                  }
                }
              ]
            }
            """;
    RequestAdapter requestAdapter = mock(RequestAdapter.class);
    RegistryClient client;
    SchemaByContentResolverClient schemaByContentResolverClient;

    @BeforeEach
    public void init() {
        client = new RegistryClient(requestAdapter);
        schemaByContentResolverClient = new SchemaByContentResolverClient(client);
    }

    @Test
    void should_fetch_version_using_content() {
        // Given
        ArtifactReference reference = ArtifactReference.builder().artifactId("artifactTestId").groupId("test.group.id").build();
        VersionSearchResults response = createVersionSearchResults(createSearchedVersion(ENABLED, "1"));

        doReturn(response).when(requestAdapter).send(any(), any(), any());

        // When
        var artifact = schemaByContentResolverClient.handleResolveSchemaByContent(SCHEMA_CONTENT, reference, "AVRO", "application/json");

        // Then
        assertThat(artifact).isNotNull();
        assertThat(artifact.getArtifactType()).isEqualTo("AVRO");
        assertThat(artifact.getArtifactId()).isEqualTo("artifactTestId");
        assertThat(artifact.getGroupId()).isEqualTo("test.group.id");
        assertThat(artifact.getVersion()).isEqualTo("1");
    }

    @Test
    void should_fail_to_fetch_version_when_artifact_state_is_disabled() {
        // Given

        ArtifactReference reference = ArtifactReference.builder().artifactId("artifactTestId").groupId("test.group.id").build();
        VersionSearchResults response = createVersionSearchResults(createSearchedVersion(DISABLED, "1"));

        doReturn(response).when(requestAdapter).send(any(), any(), any());

        // When / Then
        assertThatThrownBy(() -> schemaByContentResolverClient.handleResolveSchemaByContent(SCHEMA_CONTENT, reference, "AVRO", "application/json"))
                .isInstanceOfSatisfying(RuntimeException.class, e -> assertThat(e.getMessage()).startsWith("Could not resolve artifact reference by content"));

    }

    @Test
    void should_to_fetch_version_when_one_artifact_is_not_disabled_in_search_results() {
        // Given

        ArtifactReference reference = ArtifactReference.builder().artifactId("artifactTestId").groupId("test.group.id").build();
        VersionSearchResults response =
                createVersionSearchResults(
                        createSearchedVersion(ENABLED, "2"),
                        createSearchedVersion(DISABLED, "1")
                );

        doReturn(response).when(requestAdapter).send(any(), any(), any());

        // When / Then
        // When
        var artifact = schemaByContentResolverClient.handleResolveSchemaByContent(SCHEMA_CONTENT, reference, "AVRO", "application/json");

        // Then
        assertThat(artifact).isNotNull();
        assertThat(artifact.getVersion()).isEqualTo("2");
    }

    private static VersionSearchResults createVersionSearchResults(SearchedVersion... versions) {
        VersionSearchResults results = new VersionSearchResults();
        results.setCount(1);
        results.setVersions(List.of(versions));
        return results;
    }

    private static SearchedVersion createSearchedVersion(VersionState state, String version) {
        SearchedVersion searchedVersion = new SearchedVersion();
        searchedVersion.setName("artifactTest");
        searchedVersion.setGlobalId(1L);
        searchedVersion.setArtifactId("artifactTestId");
        searchedVersion.setGroupId("test.group.id");
        searchedVersion.setVersion(version);
        searchedVersion.setState(state);
        searchedVersion.setArtifactType("AVRO");
        return searchedVersion;
    }

}