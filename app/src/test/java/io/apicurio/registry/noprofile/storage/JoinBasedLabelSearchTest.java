package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the JOIN-based label search optimization.
 * This test class uses JoinBasedLabelSearchProfile to enable the optimized search path.
 */
@QuarkusTest
@TestProfile(JoinBasedLabelSearchProfile.class)
public class JoinBasedLabelSearchTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = JoinBasedLabelSearchTest.class.getSimpleName();

    private static final String OPENAPI_CONTENT = """
            {
                "openapi": "3.0.2",
                "info": {
                    "title": "Test API",
                    "version": "1.0.0"
                }
            }
            """;

    @Inject
    @Current
    RegistryStorage storage;

    protected RegistryStorage storage() {
        return storage;
    }

    @Test
    public void testJoinBasedLabelSearch() throws Exception {
        String artifactIdPrefix = "testJoinBasedLabelSearch-";

        // Create artifacts with different label combinations
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            Map<String, String> labels = new HashMap<>();
            labels.put("env", idx <= 5 ? "prod" : "dev");
            labels.put("team", idx % 2 == 0 ? "platform" : "backend");
            if (idx == 3 || idx == 4) {
                labels.put("priority", "high");
            }
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(artifactId + "-name",
                    artifactId + "-description", null, labels);
            storage().createArtifact(
                    GROUP_ID, artifactId, ArtifactType.OPENAPI, metaData, null, ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    null, Collections.emptyList(), false, false, null).getValue();
        }

        // Test: Search for artifacts with env=prod (should find 5)
        Set<SearchFilter> filters = Collections.singleton(SearchFilter.ofLabel("env", "prod"));
        ArtifactSearchResultsDto results = storage().searchArtifacts(filters, OrderBy.name,
                OrderDirection.asc, 0, 20);
        Assertions.assertEquals(5, results.getCount());

        // Test: Search for artifacts with env=prod AND team=platform (should find: 2, 4 - even numbers <= 5)
        filters = Set.of(SearchFilter.ofLabel("env", "prod"), SearchFilter.ofLabel("team", "platform"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(2, results.getCount());

        // Test: Search for artifacts with env=prod AND team=backend (should find: 1, 3, 5 - odd numbers <= 5)
        filters = Set.of(SearchFilter.ofLabel("env", "prod"), SearchFilter.ofLabel("team", "backend"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(3, results.getCount());

        // Test: Search for artifacts with priority=high (should find: 3, 4)
        filters = Collections.singleton(SearchFilter.ofLabel("priority", "high"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(2, results.getCount());

        // Test: Search for artifacts with env=prod AND priority=high (should find: 3, 4)
        filters = Set.of(SearchFilter.ofLabel("env", "prod"), SearchFilter.ofLabel("priority", "high"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(2, results.getCount());

        // Test: Search for artifacts with label key only (env exists)
        filters = Collections.singleton(SearchFilter.ofLabel("env"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(10, results.getCount());

        // Test: Search for artifacts with negated label (NOT env=prod should find dev artifacts: 6-10)
        filters = Set.of(SearchFilter.ofName("testJoinBasedLabelSearch*"), SearchFilter.ofLabel("env", "prod").negated());
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(5, results.getCount());

        // Test: Search for artifacts that don't have the 'priority' label
        filters = Set.of(SearchFilter.ofName("testJoinBasedLabelSearch*"), SearchFilter.ofLabel("priority").negated());
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        Assertions.assertEquals(8, results.getCount()); // All except 3 and 4
    }

    @Test
    public void testJoinBasedSearchCountAccuracy() throws Exception {
        String artifactIdPrefix = "testJoinCountAccuracy-";

        // Create artifacts with the same label to test count accuracy
        for (int idx = 1; idx <= 25; idx++) {
            String artifactId = artifactIdPrefix + idx;
            ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
            Map<String, String> labels = new HashMap<>();
            labels.put("category", "test");
            if (idx <= 15) {
                labels.put("status", "active");
            }
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(artifactId + "-name",
                    artifactId + "-description", null, labels);
            storage().createArtifact(
                    GROUP_ID, artifactId, ArtifactType.OPENAPI, metaData, null, ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON).content(content).build(),
                    null, Collections.emptyList(), false, false, null).getValue();
        }

        // Test pagination with label filter - verify count reflects total, not page size
        Set<SearchFilter> filters = Set.of(SearchFilter.ofLabel("category", "test"));
        ArtifactSearchResultsDto results = storage().searchArtifacts(filters, OrderBy.name,
                OrderDirection.asc, 0, 10);

        // Count should be 25 (total matching), but artifacts list should be 10 (page size)
        Assertions.assertEquals(25, results.getCount());
        Assertions.assertEquals(10, results.getArtifacts().size());

        // Test with multiple labels
        filters = Set.of(SearchFilter.ofLabel("category", "test"), SearchFilter.ofLabel("status", "active"));
        results = storage().searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 5);

        // Count should be 15 (those with status=active), artifacts list should be 5 (page size)
        Assertions.assertEquals(15, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
    }
}
