package io.apicurio.registry.rest.v2.impl;

import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.VersionState;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression for #5224 — version list/search responses must include artifact references.
 */
class V2ApiUtilVersionReferencesTest {

    @Test
    void dtoToSearchResultsIncludesReferences() {
        ArtifactReferenceDto ref = ArtifactReferenceDto.builder()
                .groupId("default")
                .artifactId("Address")
                .version("1")
                .name("com.example.Address")
                .build();

        SearchedVersionDto version = SearchedVersionDto.builder()
                .groupId("default")
                .artifactId("Order")
                .version("3")
                .name("Order")
                .owner("tester")
                .createdOn(new Date())
                .globalId(48L)
                .contentId(37L)
                .artifactType("AVRO")
                .state(VersionState.ENABLED)
                .references(List.of(ref))
                .build();

        VersionSearchResultsDto dto = new VersionSearchResultsDto();
        dto.setCount(1);
        dto.setVersions(List.of(version));

        var results = V2ApiUtil.dtoToSearchResults(dto);
        assertEquals(1, results.getVersions().size());
        assertNotNull(results.getVersions().get(0).getReferences());
        assertEquals(1, results.getVersions().get(0).getReferences().size());
        assertEquals("Address", results.getVersions().get(0).getReferences().get(0).getArtifactId());
        assertEquals("com.example.Address", results.getVersions().get(0).getReferences().get(0).getName());
    }

    @Test
    void dtoToSearchResultsUsesEmptyListWhenReferencesNull() {
        SearchedVersionDto version = SearchedVersionDto.builder()
                .artifactId("Order")
                .version("1")
                .owner("tester")
                .createdOn(new Date())
                .globalId(1L)
                .contentId(1L)
                .artifactType("AVRO")
                .state(VersionState.ENABLED)
                .build();

        VersionSearchResultsDto dto = new VersionSearchResultsDto();
        dto.setCount(1);
        dto.setVersions(List.of(version));

        var results = V2ApiUtil.dtoToSearchResults(dto);
        assertNotNull(results.getVersions().get(0).getReferences());
        assertEquals(0, results.getVersions().get(0).getReferences().size());
    }
}
