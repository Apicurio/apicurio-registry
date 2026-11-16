package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.dto.AuthorizationNames;
import io.apicurio.scope.ResourceScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Elasticsearch translation of {@link AuthorizationFilter} by evaluating the
 * generated query against documents in memory and comparing with the reference semantics.
 */
class ElasticsearchAuthorizationQueryTest {

    private final ElasticsearchSearchService service = new ElasticsearchSearchService();

    /** Indexed documents: groupId (the default group is indexed as "default") and artifactId. */
    private static final List<Map<String, String>> DOCS = List.of(
            doc("team-a", "x"), doc("team-a", "secret-1"), doc("team-a", "b/c"), doc("team-a/b", "c"),
            doc("team-ab", "x"), doc("default", "shared"), doc("defaults", "x"), doc("other", "team-a/x"));

    private static Map<String, String> doc(String groupId, String artifactId) {
        return Map.of("groupId", groupId, "artifactId", artifactId);
    }

    private static List<AuthorizationFilter> filters() {
        return List.of(
                new AuthorizationFilter(false, Set.of(), Set.of("team-a/"), Set.of(), Set.of(), null),
                new AuthorizationFilter(false, Set.of("team-a/b/c"), Set.of(), Set.of(), Set.of(), null),
                new AuthorizationFilter(false, Set.of("team-a%2Fb/c"), Set.of("team-a%"), Set.of(), Set.of(), null),
                new AuthorizationFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), "carol",
                        Set.of("team-ab/x")),
                new AuthorizationFilter(false, Set.of(), Set.of("team-a/"), Set.of(), Set.of("team-a/"), "carol",
                        Set.of("team-a/secret-1")),
                new AuthorizationFilter(false, Set.of(), Set.of("team-a"), Set.of(), Set.of("team-a/secret"), null),
                new AuthorizationFilter(true, Set.of(), Set.of(), Set.of("team-ab/x"), Set.of("def"), null),
                new AuthorizationFilter(false, Set.of("default/shared"), Set.of(), Set.of(), Set.of(), null),
                new AuthorizationFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), null),
                new AuthorizationFilter(false, Set.of("no-slash"), Set.of(), Set.of(), Set.of(), null));
    }

    @Test
    void queryMatchesReferenceSemanticsForEveryDocument() {
        for (AuthorizationFilter filter : filters()) {
            Query query = service.buildAuthorizationQuery(filter);
            ResourceScope reference = ResourceScope.of(filter.allowAll(), filter.allowExact(),
                    filter.allowPrefix(), Set.of(), filter.denyExact(), filter.denyPrefix(), Set.of());
            for (Map<String, String> doc : DOCS) {
                String name = AuthorizationNames.artifact(doc.get("groupId"), doc.get("artifactId"));
                boolean expected = reference.matches(name) || filter.ownedArtifacts().contains(name);
                assertEquals(expected, evaluate(query, doc), filter + " on " + name);
            }
        }
    }

    @Test
    void namesAreUnambiguousWhenIdsContainSlash() {
        Query artifactWithSlash = service.buildAuthorizationQuery(
                new AuthorizationFilter(false, Set.of("team-a/b/c"), Set.of(), Set.of(), Set.of(), null));
        Query groupWithSlash = service.buildAuthorizationQuery(
                new AuthorizationFilter(false, Set.of("team-a%2Fb/c"), Set.of(), Set.of(), Set.of(), null));

        assertTrue(evaluate(artifactWithSlash, doc("team-a", "b/c")));
        assertFalse(evaluate(artifactWithSlash, doc("team-a/b", "c")));
        assertTrue(evaluate(groupWithSlash, doc("team-a/b", "c")));
        assertFalse(evaluate(groupWithSlash, doc("team-a", "b/c")));
    }

    @Test
    void ownedArtifactsMatchDespiteDenyRules() {
        Query query = service.buildAuthorizationQuery(new AuthorizationFilter(false, Set.of(), Set.of(),
                Set.of(), Set.of("team-a/"), "carol", Set.of("team-a/x")));

        assertTrue(evaluate(query, doc("team-a", "x")));
        assertFalse(evaluate(query, doc("team-a", "secret-1")));
    }

    @Test
    void evaluatorFollowsElasticsearchShouldSemantics() {
        Query matchAll = Query.of(q -> q.matchAll(m -> m));
        Query matchNone = Query.of(q -> q.matchNone(m -> m));
        Map<String, String> doc = doc("g", "a");

        // Alongside must, should clauses are optional unless minimumShouldMatch requires them
        assertTrue(evaluate(Query.of(q -> q.bool(b -> b.must(matchAll).should(matchNone))), doc));
        assertFalse(evaluate(Query.of(q -> q.bool(b -> b.must(matchAll).should(matchNone)
                .minimumShouldMatch("1"))), doc));
        // Without must or filter, at least one should clause must match
        assertFalse(evaluate(Query.of(q -> q.bool(b -> b.should(matchNone))), doc));
        assertTrue(evaluate(Query.of(q -> q.bool(b -> b.should(matchNone).should(matchAll))), doc));
        // A minimum the should clauses cannot reach matches nothing
        assertFalse(evaluate(Query.of(q -> q.bool(b -> b.must(matchAll).minimumShouldMatch("1"))), doc));
    }

    /**
     * Minimal evaluator for the query shapes buildAuthorizationQuery emits, following Elasticsearch
     * bool semantics: {@code should} clauses are required only as far as {@code minimumShouldMatch}
     * says, which defaults to 1 when there are no {@code must} or {@code filter} clauses and to 0
     * otherwise. Shapes it does not model are rejected rather than approximated.
     */
    private static boolean evaluate(Query query, Map<String, String> doc) {
        if (query.isMatchAll()) {
            return true;
        }
        if (query.isMatchNone()) {
            return false;
        }
        if (query.isTerm()) {
            return query.term().value().stringValue().equals(doc.get(query.term().field()));
        }
        if (query.isPrefix()) {
            String value = doc.get(query.prefix().field());
            return value != null && value.startsWith(query.prefix().value());
        }
        if (query.isBool()) {
            BoolQuery bool = query.bool();
            boolean must = bool.must().stream().allMatch(q -> evaluate(q, doc))
                    && bool.filter().stream().allMatch(q -> evaluate(q, doc));
            boolean mustNot = bool.mustNot().stream().noneMatch(q -> evaluate(q, doc));
            long requiredShould;
            if (bool.minimumShouldMatch() != null) {
                // Only absolute counts are modeled (not percentages or combinations)
                requiredShould = Long.parseLong(bool.minimumShouldMatch());
            } else {
                requiredShould = !bool.should().isEmpty() && bool.must().isEmpty() && bool.filter().isEmpty()
                        ? 1 : 0;
            }
            long matchedShould = bool.should().stream().filter(q -> evaluate(q, doc)).count();
            boolean should = matchedShould >= requiredShould;
            return must && mustNot && should;
        }
        throw new IllegalArgumentException("Unexpected query kind: " + query._kind());
    }
}
