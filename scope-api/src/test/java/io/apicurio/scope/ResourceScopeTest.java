package io.apicurio.scope;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceScopeTest {

    private static final Set<String> NONE = Set.of();

    private static ResourceScope.Scoped scoped(ResourceScope scope) {
        return assertInstanceOf(ResourceScope.Scoped.class, scope);
    }

    @Test
    void ofNormalizesTheShortCuts() {
        assertInstanceOf(ResourceScope.Forbidden.class, ResourceScope.of(false, NONE, NONE, NONE, NONE, NONE, NONE));
        // Denies without allows still allow nothing
        assertInstanceOf(ResourceScope.Forbidden.class,
                ResourceScope.of(false, NONE, NONE, NONE, Set.of("a"), NONE, NONE));
        // An empty deny prefix denies everything
        assertInstanceOf(ResourceScope.Forbidden.class,
                ResourceScope.of(true, NONE, NONE, NONE, NONE, Set.of(""), NONE));
        assertInstanceOf(ResourceScope.Unrestricted.class,
                ResourceScope.of(true, NONE, NONE, NONE, NONE, NONE, NONE));
        // The empty allow prefix is allow-all
        assertInstanceOf(ResourceScope.Unrestricted.class,
                ResourceScope.of(false, NONE, Set.of(""), NONE, NONE, NONE, NONE));
        ResourceScope.Scoped emptyPrefixWithDeny = scoped(
                ResourceScope.of(false, NONE, Set.of("", "a/"), NONE, Set.of("x"), NONE, NONE));
        assertTrue(emptyPrefixWithDeny.allowAll());
        assertEquals(Set.of(), emptyPrefixWithDeny.allowPrefix());
        // Not every empty scope is detected: the same name allowed and denied is a Scoped that
        // selects nothing, which consumers evaluate
        assertFalse(scoped(ResourceScope.of(false, Set.of("a"), NONE, NONE, Set.of("a"), NONE, NONE))
                .matches("a"));
        // Allow-all with a deny is a scope, not unrestricted
        assertFalse(scoped(ResourceScope.of(true, NONE, NONE, NONE, Set.of("secret"), NONE, NONE))
                .matches("secret"));
    }

    @Test
    void exactPrefixAndPatternSelectorsMatchAsDocumented() {
        ResourceScope scope = ResourceScope.of(false, Set.of("orders"), Set.of("team-a/"), Set.of("logs-[0-9]+"),
                NONE, NONE, NONE);

        assertTrue(scope.matches("orders"));
        assertFalse(scope.matches("orders2"));
        // Case-sensitive, no normalization
        assertFalse(scope.matches("ORDERS"));
        assertTrue(scope.matches("team-a/x"));
        assertTrue(scope.matches("team-a/"));
        assertFalse(scope.matches("team-a"));
        // Patterns match the whole name
        assertTrue(scope.matches("logs-42"));
        assertFalse(scope.matches("logs-42-old"));
        assertFalse(scope.matches("old-logs-42"));
    }

    @Test
    void denyWinsOverEveryAllow() {
        ResourceScope scope = ResourceScope.of(true, Set.of("team-a/secret"), Set.of("team-a/"), NONE,
                Set.of("team-a/secret"), Set.of("team-a/internal-"), Set.of(".*-tmp"));

        assertTrue(scope.matches("team-a/x"));
        assertTrue(scope.matches("team-b/x"));
        assertFalse(scope.matches("team-a/secret"));
        assertFalse(scope.matches("team-a/internal-x"));
        assertFalse(scope.matches("team-b/x-tmp"));
    }

    @Test
    void hasPatternsReportsAllowAndDenyPatterns() {
        assertFalse(scoped(ResourceScope.of(false, Set.of("a"), NONE, NONE, NONE, NONE, NONE)).hasPatterns());
        assertTrue(scoped(ResourceScope.of(false, NONE, NONE, Set.of("a.*"), NONE, NONE, NONE)).hasPatterns());
        assertTrue(scoped(ResourceScope.of(true, NONE, NONE, NONE, NONE, NONE, Set.of("a.*"))).hasPatterns());
    }

    @Test
    void invalidAndBacktrackingPatternsAreHandled() {
        Set<String> invalid = Set.of("(");
        assertThrows(IllegalArgumentException.class,
                () -> ResourceScope.of(false, NONE, NONE, invalid, NONE, NONE, NONE));
        // RE2 has no backreferences, so a pattern that needs them is rejected rather than evaluated
        Set<String> backreference = Set.of("(a)\\1");
        assertThrows(IllegalArgumentException.class,
                () -> ResourceScope.of(false, NONE, NONE, backreference, NONE, NONE, NONE));
        // A pattern that backtracks catastrophically in java.util.regex runs in linear time
        ResourceScope scope = ResourceScope.of(false, NONE, NONE, Set.of("(a+)+b"), NONE, NONE, NONE);
        assertFalse(scope.matches("a".repeat(10_000) + "c"));
    }

    @Test
    void scopesWithTheSameSelectorsAreEqual() {
        ResourceScope first = ResourceScope.of(false, Set.of("a", "b"), Set.of("p/"), Set.of("x.*"), NONE, NONE, NONE);
        ResourceScope second = ResourceScope.of(false, Set.of("b", "a"), Set.of("p/"), Set.of("x.*"), NONE, NONE, NONE);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, ResourceScope.of(false, Set.of("a"), Set.of("p/"), Set.of("x.*"), NONE, NONE, NONE));
        assertEquals(ResourceScope.forbidden(), ResourceScope.of(false, NONE, NONE, NONE, NONE, NONE, NONE));
        assertEquals(ResourceScope.unrestricted(), ResourceScope.of(true, NONE, NONE, NONE, NONE, NONE, NONE));
    }
}
