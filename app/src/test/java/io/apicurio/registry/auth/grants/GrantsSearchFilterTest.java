package io.apicurio.registry.auth.grants;

import io.apicurio.authz.Artifact;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.scope.ResourceScope;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How {@link GrantsSearchFilter} turns a scope into a search restriction, in particular the owner
 * bypass on the fail-closed paths: rules that allow nothing, and rules with patterns.
 */
class GrantsSearchFilterTest {

    private static final Set<String> NONE = Set.of();
    private static final ResourceScope WITH_PATTERNS = ResourceScope.of(false, Set.of("team-a/x"), NONE,
            Set.of("team-a/.*"), NONE, NONE, Set.of(".*-secret"));

    private static GrantsSearchFilter searchFilter(ResourceScope scope, boolean ownerBypass) {
        GrantsSearchFilter filter = new GrantsSearchFilter();
        filter.guard = mock(ResourceAccessGuard.class);
        when(filter.guard.isEnforced(AuthorizedLevel.Read)).thenReturn(true);
        filter.grantsAc = mock(GrantsAccessController.class);
        when(filter.grantsAc.readScope(Artifact.READ)).thenReturn(scope);
        filter.config = mock(GrantsAccessControllerConfig.class);
        when(filter.config.isOwnerBypassEnabled()).thenReturn(ownerBypass);
        filter.securityIdentity = QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("alice"))
                .build();
        return filter;
    }

    /** @return the authorization filter added to the search, or null if the search is skipped */
    private static AuthorizationFilter restriction(ResourceScope scope, boolean ownerBypass) {
        Set<SearchFilter> restricted = searchFilter(scope, ownerBypass).restrict(Set.of(), Artifact.READ);
        if (restricted == null) {
            return null;
        }
        return restricted.stream().filter(f -> f.getType() == SearchFilterType.authorization).findFirst()
                .map(SearchFilter::getAuthorizationValue).orElseThrow();
    }

    private static final AuthorizationFilter OWNED_BY_ALICE = new AuthorizationFilter(false, NONE, NONE, NONE, NONE,
            "alice");

    @Test
    void ownersSeeTheirOwnResourcesWhenTheRulesAllowNothing() {
        assertEquals(OWNED_BY_ALICE, restriction(ResourceScope.forbidden(), true));
        assertNull(restriction(ResourceScope.forbidden(), false));
    }

    @Test
    void ownersSeeTheirOwnResourcesWhenTheRulesHavePatterns() {
        // The rules select nothing in search, as with a forbidden scope; the owner bypass still applies
        assertEquals(OWNED_BY_ALICE, restriction(WITH_PATTERNS, true));
        assertNull(restriction(WITH_PATTERNS, false));
    }

    @Test
    void scopesWithoutPatternsArePassedToStorage() {
        ResourceScope scope = ResourceScope.of(true, NONE, NONE, NONE, Set.of("team-a/secret"), Set.of("team-b/"),
                NONE);
        assertEquals(new AuthorizationFilter(true, NONE, NONE, Set.of("team-a/secret"), Set.of("team-b/"), "alice"),
                restriction(scope, true));
        assertEquals(new AuthorizationFilter(true, NONE, NONE, Set.of("team-a/secret"), Set.of("team-b/"), null),
                restriction(scope, false));
    }

    @Test
    void unrestrictedScopesAddNoFilter() {
        assertEquals(Set.of(), searchFilter(ResourceScope.unrestricted(), true).restrict(Set.of(), Artifact.READ));
    }
}
