package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SearchQueryBuilder} implementations.
 */
class SearchQueryBuilderTest {

    private SqlStatements sqlStatements;
    private CommonSearchQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        sqlStatements = mock(SqlStatements.class);
        when(sqlStatements.dbType()).thenReturn("postgresql");
        queryBuilder = new CommonSearchQueryBuilder(sqlStatements);
    }

    @Test
    void testBuildArtifactSearchQuery_noFilters() {
        Set<SearchFilter> filters = Set.of();

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertNotNull(query.sql());
        assertTrue(query.sql().contains("SELECT"));
        assertTrue(query.sql().contains("FROM artifacts"));
        assertTrue(query.sql().contains("COUNT(*) OVER()"));
        assertTrue(query.sql().contains("ORDER BY"));
        assertTrue(query.sql().contains("LIMIT"));
        // No filters, so no WHERE clause beyond the base
        assertFalse(query.sql().contains("GROUP BY")); // No label joins, no GROUP BY needed
    }

    @Test
    void testBuildArtifactSearchQuery_withNameFilter() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofName("test*"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("WHERE"));
        assertTrue(query.sql().contains("a.name"));
        assertTrue(query.sql().contains("LIKE"));
        assertEquals(2, query.binders().size()); // name and artifactId binders
    }

    @Test
    void testBuildArtifactSearchQuery_withSingleLabelFilter() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofLabel("env", "prod"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("INNER JOIN artifact_labels"));
        assertTrue(query.sql().contains("labelKey"));
        assertTrue(query.sql().contains("labelValue"));
        assertTrue(query.sql().contains("GROUP BY")); // Label JOIN requires GROUP BY
        assertEquals(2, query.binders().size()); // labelKey and labelValue
    }

    @Test
    void testBuildArtifactSearchQuery_withMultipleLabelFilters() {
        // Use LinkedHashSet to preserve order for consistent testing
        Set<SearchFilter> filters = new LinkedHashSet<>();
        filters.add(SearchFilter.ofLabel("env", "prod"));
        filters.add(SearchFilter.ofLabel("team", "platform"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        // Should have two separate JOIN clauses with different aliases
        assertTrue(query.sql().contains("l0"));
        assertTrue(query.sql().contains("l1"));
        assertTrue(query.sql().contains("INNER JOIN artifact_labels l0"));
        assertTrue(query.sql().contains("INNER JOIN artifact_labels l1"));
        assertTrue(query.sql().contains("GROUP BY"));
        assertEquals(4, query.binders().size()); // 2 labels x 2 (key + value)
    }

    @Test
    void testBuildArtifactSearchQuery_withNegatedLabelFilter() {
        SearchFilter filter = SearchFilter.ofLabel("deprecated").negated();
        Set<SearchFilter> filters = Set.of(filter);

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("LEFT JOIN artifact_labels"));
        assertTrue(query.sql().contains("IS NULL"));
        assertTrue(query.sql().contains("GROUP BY"));
    }

    @Test
    void testBuildArtifactSearchQuery_withMixedFilters() {
        Set<SearchFilter> filters = new LinkedHashSet<>();
        filters.add(SearchFilter.ofName("my-api*"));
        filters.add(SearchFilter.ofLabel("env", "prod"));
        filters.add(SearchFilter.ofGroupId("com.example"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("INNER JOIN artifact_labels"));
        assertTrue(query.sql().contains("a.name"));
        assertTrue(query.sql().contains("a.groupId"));
        assertTrue(query.sql().contains("GROUP BY"));
    }

    @Test
    void testBuildVersionSearchQuery_withLabelFilter() {
        Set<SearchFilter> filters = new LinkedHashSet<>();
        filters.add(SearchFilter.ofGroupId("default"));
        filters.add(SearchFilter.ofArtifactId("my-artifact"));
        filters.add(SearchFilter.ofLabel("version-tag", "stable"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.VERSION, filters, OrderBy.globalId, OrderDirection.desc);

        assertNotNull(query);
        assertTrue(query.sql().contains("FROM versions v"));
        assertTrue(query.sql().contains("INNER JOIN version_labels"));
        assertTrue(query.sql().contains("l0.globalId = v.globalId"));
        assertTrue(query.sql().contains("GROUP BY"));
        assertTrue(query.sql().contains("ORDER BY v.globalId"));
        assertTrue(query.sql().contains("desc"));
    }

    @Test
    void testBuildGroupSearchQuery_withLabelFilter() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofLabel("team", "backend"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.GROUP, filters, OrderBy.groupId, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("FROM groups g"));
        assertTrue(query.sql().contains("INNER JOIN group_labels"));
        assertTrue(query.sql().contains("l0.groupId = g.groupId"));
        assertTrue(query.sql().contains("GROUP BY"));
    }

    @Test
    void testBuildArtifactSearchQuery_sqlServerPagination() {
        when(sqlStatements.dbType()).thenReturn("mssql");
        CommonSearchQueryBuilder mssqlBuilder = new CommonSearchQueryBuilder(sqlStatements);

        Set<SearchFilter> filters = Set.of();
        SearchQueryBuilder.SearchQuery query = mssqlBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertFalse(query.sql().contains("LIMIT"));
    }

    @Test
    void testBuildArtifactSearchQuery_labelKeyOnlyFilter() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofLabel("deprecated"));

        SearchQueryBuilder.SearchQuery query = queryBuilder.buildSearchQuery(
                SearchQueryBuilder.EntityType.ARTIFACT, filters, OrderBy.name, OrderDirection.asc);

        assertNotNull(query);
        assertTrue(query.sql().contains("INNER JOIN artifact_labels"));
        assertTrue(query.sql().contains("labelKey"));
        // Only 1 binder for the key (no value filter)
        assertEquals(1, query.binders().size());
    }
}
