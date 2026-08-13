package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.apicurio.registry.storage.dto.SearchFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the structure-filter query construction in {@link ElasticsearchSearchService}.
 *
 * <p>Structured elements are indexed as {@code type:kind:name} (see
 * {@link ElasticsearchDocumentBuilder}), where {@code type} and {@code kind} are colon-free
 * identifiers but {@code name} may contain a colon (for example a namespaced Agent Card skill id).
 * A fully-qualified filter must always resolve to an exact match on the {@code structure} field, so
 * that a colon in the name does not silently downgrade the query to a text search that can never
 * match the indexed value.
 */
class ElasticsearchStructureQueryTest {

    private final ElasticsearchSearchService service = new ElasticsearchSearchService();

    /**
     * Builds the query for a single structure filter and returns its one positive (must) clause.
     * {@link ElasticsearchSearchService#buildEsQuery} always adds a {@code mustNot} clause that
     * excludes internal documents; that clause is not the subject of these tests.
     */
    private Query structureClause(String structureValue) {
        Query query = service.buildEsQuery(Set.of(SearchFilter.ofStructure(structureValue)));
        assertTrue(query.isBool(), "top-level query should be a bool query");
        BoolQuery bool = query.bool();
        List<Query> must = bool.must();
        assertEquals(1, must.size(), "exactly one positive clause is expected for a single filter");
        return must.get(0);
    }

    @Test
    void fullyQualifiedStructureUsesExactMatch() {
        Query clause = structureClause("agent_card:skill:summarize");
        assertTrue(clause.isTerm(), "type:kind:name should be an exact term query");
        assertEquals("structure", clause.term().field());
        assertEquals("agent_card:skill:summarize", clause.term().value().stringValue());
    }

    @Test
    void colonBearingNameStillUsesExactMatch() {
        // Regression: a namespaced skill id contains a colon, so the fully-qualified filter has
        // four colon-separated segments. It must still be an exact match on the structure field
        // rather than falling back to a structure_text search that cannot match the indexed value.
        Query clause = structureClause("agent_card:skill:acme:translate");
        assertTrue(clause.isTerm(), "a name containing ':' must not fall back to a text query");
        assertEquals("structure", clause.term().field());
        assertEquals("agent_card:skill:acme:translate", clause.term().value().stringValue());
    }

    @Test
    void kindAndNameUsesTextMatch() {
        Query clause = structureClause("skill:summarize");
        assertTrue(clause.isMatch(), "kind:name should be a text query on structure_text");
        assertEquals("structure_text", clause.match().field());
    }

    @Test
    void plainNameUsesTextMatch() {
        Query clause = structureClause("summarize");
        assertTrue(clause.isMatch(), "a plain name should be a text query on structure_text");
        assertEquals("structure_text", clause.match().field());
    }
}
