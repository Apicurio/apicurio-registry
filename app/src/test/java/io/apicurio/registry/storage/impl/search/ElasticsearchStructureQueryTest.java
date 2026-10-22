package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.apicurio.registry.a2a.A2AConstants;
import io.apicurio.registry.content.extract.AgentCardStructuredContentExtractor;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the structure-filter query construction in {@link ElasticsearchSearchService}, plus
 * a consistency check against the indexing side.
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

    @Test
    void indexedStructureValueMatchesQueryTerm() {
        // The query-only tests above build the expected term from the same lowercased input on both
        // sides, so they cannot detect a mismatch with the indexer. Drive the real indexing path for
        // an Agent Card whose skill id mixes case and contains a colon, then assert the query for the
        // same skill resolves to an exact term on the identical stored value. Both sides normalize
        // with Locale.ROOT, so the indexed value and the query term must agree exactly.
        String agentCard = """
                {
                    "protocolVersion": "1.0",
                    "name": "Translator Agent",
                    "skills": [ { "id": "Acme:Translate" } ]
                }
                """;
        ArtifactVersionMetaDataDto metadata = ArtifactVersionMetaDataDto.builder()
                .artifactType(ArtifactType.AGENT_CARD)
                .build();
        ElasticsearchSearchConfig config = new ElasticsearchSearchConfig();
        config.contentMaxSize = 1_048_576;
        ElasticsearchDocumentBuilder documentBuilder = new ElasticsearchDocumentBuilder();
        documentBuilder.config = config;

        Map<String, Object> doc = documentBuilder.buildVersionDocument(metadata,
                agentCard.getBytes(StandardCharsets.UTF_8), new AgentCardStructuredContentExtractor());

        @SuppressWarnings("unchecked")
        List<String> indexedStructure = (List<String>) doc.get("structure");
        String expected = "agent_card:skill:acme:translate";
        assertTrue(indexedStructure != null && indexedStructure.contains(expected),
                "the indexer should store the lowercased type:kind:name value");

        Query clause = structureClause(A2AConstants.PREFIX_AGENT_CARD_SKILL + "Acme:Translate");
        assertTrue(clause.isTerm(), "the query for the same skill must be an exact term query");
        assertEquals("structure", clause.term().field());
        assertEquals(expected, clause.term().value().stringValue(),
                "the query term must equal the value the indexer stored");
    }
}
