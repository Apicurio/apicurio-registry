package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.Current;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
public class RegistryStoragePerformanceTest {

    private static final String GROUP_ID = RegistryStoragePerformanceTest.class.getSimpleName();

    private static final int NUM_ARTIFACTS = 50000;
//    private static final int NUM_VERSIONS = 5;

    private static final String OPENAPI_CONTENT_TEMPLATE = "{" +
            "    \"openapi\": \"3.0.2\"," +
            "    \"info\": {" +
            "        \"title\": \"TITLE\"," +
            "        \"version\": \"VERSION\"," +
            "        \"description\": \"DESCRIPTION\"" +
            "    }" +
            "}";

    @Inject
    @Current
    RegistryStorage storage;

    private boolean isTestEnabled() {
        return "enabled".equals(System.getProperty(RegistryStoragePerformanceTest.class.getSimpleName()));
    }

    @Test
    public void testStoragePerformance() throws Exception {
        if (!isTestEnabled()) {
            return;
        }

        System.out.println("========================================================================");
        System.out.println("= Running artifactStore performance test.  Please wait...                    =");
        System.out.println("========================================================================");

        String artifactIdPrefix = "testStoragePerformance-";

        long startCreate = System.currentTimeMillis();
        for (int idx = 1; idx <= NUM_ARTIFACTS; idx++) {
            String artifactId = artifactIdPrefix + idx;
            String title = "API " + artifactId;
            String description = "Number " + idx + " all time on the top APIs list.";
            Map<String, String> labels = new HashMap<>();
            labels.put("key", "value");
            labels.put("key-" + idx, "value-" + idx);
            ContentHandle content = ContentHandle.create(
                    OPENAPI_CONTENT_TEMPLATE
                        .replaceAll("TITLE", title)
                        .replaceAll("DESCRIPTION", description)
                        .replaceAll("VERSION", String.valueOf(idx)));
            ContentWrapperDto versionContent = ContentWrapperDto.builder()
                    .content(content)
                    .contentType(ContentTypes.APPLICATION_JSON)
                    .build();
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(title, description, null, labels);
            EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder()
                    .name(title)
                    .description(description)
                    .build();
            storage.createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, metaData, null, versionContent,
                    versionMetaData, List.of());

            System.out.print(".");
            if (idx % 100 == 0) {
                System.out.println(" " + idx);
            }
        }
        long endCreate = System.currentTimeMillis();

        long startGetArtifact = System.currentTimeMillis();
        StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(GROUP_ID, artifactIdPrefix + "77", "1");
        long endGetArtifact = System.currentTimeMillis();
        Assertions.assertNotNull(storedArtifact);

        long startGetArtifactMetaData = System.currentTimeMillis();
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(GROUP_ID, artifactIdPrefix + "998");
        long endGetArtifactMetaData = System.currentTimeMillis();
        Assertions.assertNotNull(dto);

        long startAllSearch = System.currentTimeMillis();
        Set<SearchFilter> filters = Collections.emptySet();
        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 20);
        long endAllSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());

        long startNameSearch = System.currentTimeMillis();
        filters = Collections.singleton(SearchFilter.ofName("testStoragePerformance-9999"));
        results = storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10);
        long endNameSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        long startAllNameSearch = System.currentTimeMillis();
        filters = Collections.singleton(SearchFilter.ofName("testStoragePerformance"));
        results = storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10);
        long endAllNameSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());

        long startLabelSearch = System.currentTimeMillis();

        filters = Collections.singleton(SearchFilter.ofLabel("key-" + (NUM_ARTIFACTS-1)));
        results = storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10);
        long endLabelSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());

        long startAllLabelSearch = System.currentTimeMillis();
        filters = Collections.singleton(SearchFilter.ofLabel("key"));
        results = storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10);
        long endAllLabelSearch = System.currentTimeMillis();
        Assertions.assertNotNull(results);
        Assertions.assertEquals(NUM_ARTIFACTS, results.getCount());

        System.out.println("========================================================================");
        System.out.println("= Storage Performance Results                                          =");
        System.out.println("=----------------------------------------------------------------------=");
        System.out.println("| Time to create " + NUM_ARTIFACTS + " artifacts: " + (endCreate - startCreate) + "ms");
        System.out.println("| ");
        System.out.println("| Get Artifact Content:   " + (endGetArtifact - startGetArtifact) + "ms");
        System.out.println("| Get Artifact Meta-Data: " + (endGetArtifactMetaData - startGetArtifactMetaData) + "ms");
        System.out.println("| ");
        System.out.println("| All Artifact Search:    " + (endAllSearch - startAllSearch) + "ms");
        System.out.println("| Single Name Search:     " + (endNameSearch - startNameSearch) + "ms");
        System.out.println("| All Name Search:        " + (endAllNameSearch - startAllNameSearch) + "ms");
        System.out.println("| Label Search:           " + (endLabelSearch - startLabelSearch) + "ms");
        System.out.println("| All Label Search:       " + (endAllLabelSearch - startAllLabelSearch) + "ms");
        System.out.println("========================================================================");
    }

}
