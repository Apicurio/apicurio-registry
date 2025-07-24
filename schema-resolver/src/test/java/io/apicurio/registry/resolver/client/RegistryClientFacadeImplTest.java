package io.apicurio.registry.resolver.client;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RegistryClientFacadeImplTest {
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
    RegistryClientFacade schemaByContentResolverClient;

    @BeforeEach
    public void init() {
        client = new RegistryClient(requestAdapter);
        schemaByContentResolverClient = new RegistryClientFacadeImpl(client);
    }

    @Test
    void should_fetch_version_using_content() {
        // Given
        ArtifactReference reference = ArtifactReference.builder().artifactId("artifactTestId").groupId("test.group.id").build();
        VersionSearchResults response = createVersionSearchResults(createSearchedVersion(ENABLED, "1"));

        doReturn(response).when(requestAdapter).send(any(), any(), any());

        // When
        var coordinates = schemaByContentResolverClient.searchVersionsByContent(SCHEMA_CONTENT, "AVRO", reference,  true);

        // Then
        assertThat(coordinates).singleElement().
        satisfies(artifact -> {
            assertThat(artifact.getArtifactId()).isEqualTo("artifactTestId");
            assertThat(artifact.getGroupId()).isEqualTo("test.group.id");
            assertThat(artifact.getVersion()).isEqualTo("1");
        });
    }

    @Test
    void should_return_empty_coordinates_when_version_is_disabled() {
        // Given

        ArtifactReference reference = ArtifactReference.builder().artifactId("artifactTestId").groupId("test.group.id").build();
        VersionSearchResults response = createVersionSearchResults(createSearchedVersion(DISABLED, "1"));

        doReturn(response).when(requestAdapter).send(any(), any(), any());

        // When / Then
        assertThat(schemaByContentResolverClient.searchVersionsByContent(SCHEMA_CONTENT, "AVRO", reference, true)).isEmpty();
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
        var coordinates = schemaByContentResolverClient.searchVersionsByContent(SCHEMA_CONTENT, "AVRO", reference, true);

        // Then
        assertThat(coordinates).singleElement()
                .satisfies( artifact -> assertThat(artifact.getVersion()).isEqualTo("2"));
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