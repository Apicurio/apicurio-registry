package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ElasticsearchSearchServiceTest {

    @Test
    @ResourceLock(value = "jvm.defaultLocale", mode = ResourceAccessMode.READ_WRITE)
    void stateFilterUppercasesUnderTurkishDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            ElasticsearchSearchService service = new ElasticsearchSearchService();

            SearchFilter filter = new SearchFilter();
            filter.setType(SearchFilterType.state);
            filter.setStringValue("disabled");

            Query query = service.buildEsQuery(Set.of(filter));

            String termValue = query.bool().must().get(0).term().value().stringValue();
            assertEquals("DISABLED", termValue);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void mapToSearchedArtifactDtoMapsAllFields() {
        ElasticsearchSearchService service = new ElasticsearchSearchService();
        service.documentBuilder = new ElasticsearchDocumentBuilder();

        Map<String, Object> source = new HashMap<>();
        source.put("groupId", "test-group");
        source.put("artifactId", "test-artifact");
        source.put("name", "Test Agent");
        source.put("description", "A test agent");
        source.put("artifactType", "AGENT_CARD");
        source.put("owner", "testuser");
        source.put("modifiedBy", "testuser");
        source.put("createdOn", 1700000000000L);
        source.put("modifiedOn", 1700000001000L);
        source.put("labels", List.of(
                Map.of("key", "env", "value", "prod")));

        SearchedArtifactDto dto = service.mapToSearchedArtifactDto(source);

        assertEquals("test-group", dto.getGroupId());
        assertEquals("test-artifact", dto.getArtifactId());
        assertEquals("Test Agent", dto.getName());
        assertEquals("A test agent", dto.getDescription());
        assertEquals("AGENT_CARD", dto.getArtifactType());
        assertEquals("testuser", dto.getOwner());
        assertEquals("testuser", dto.getModifiedBy());
        assertNotNull(dto.getCreatedOn());
        assertNotNull(dto.getModifiedOn());
        assertEquals("prod", dto.getLabels().get("env"));
    }

    @Test
    void mapToSearchedArtifactDtoHandlesNulls() {
        ElasticsearchSearchService service = new ElasticsearchSearchService();
        service.documentBuilder = new ElasticsearchDocumentBuilder();

        Map<String, Object> source = new HashMap<>();
        source.put("groupId", "g");
        source.put("artifactId", "a");

        SearchedArtifactDto dto = service.mapToSearchedArtifactDto(source);

        assertEquals("g", dto.getGroupId());
        assertEquals("a", dto.getArtifactId());
        assertNull(dto.getName());
        assertNull(dto.getDescription());
        assertNull(dto.getCreatedOn());
    }

    @Test
    void buildEsQueryWithContentAndArtifactTypeFilters() {
        ElasticsearchSearchService service = new ElasticsearchSearchService();

        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofContent("sentiment analysis"),
                SearchFilter.ofArtifactType("AGENT_CARD"));

        Query query = service.buildEsQuery(filters);

        assertNotNull(query.bool());
        assertEquals(2, query.bool().must().size());
    }
}
