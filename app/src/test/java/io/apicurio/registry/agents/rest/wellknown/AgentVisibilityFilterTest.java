package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.agents.a2a.A2AConfig;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentVisibilityFilter}. The filter is mechanism-agnostic: once
 * {@link AuthConfig#isAuthenticationEnabled()} is true (for any mechanism, see
 * {@code AuthConfigAuthenticationEnabledTest}) the visibility rules apply.
 */
class AgentVisibilityFilterTest {

    private static final SearchedArtifactDto PUBLIC = card("public-card", "owner", "public");
    private static final SearchedArtifactDto ENTITLED = card("entitled-card", "owner", null);
    private static final SearchedArtifactDto PRIVATE = card("private-card", "owner", "private");
    private static final List<SearchedArtifactDto> ALL = List.of(PUBLIC, ENTITLED, PRIVATE);

    private AgentVisibilityFilter filter;
    private AuthConfig authConfig;
    private SecurityIdentity identity;
    private RoleBasedAccessController rbac;

    @BeforeEach
    void setUp() {
        authConfig = mock(AuthConfig.class);
        identity = mock(SecurityIdentity.class);
        rbac = mock(RoleBasedAccessController.class);
        A2AConfig a2aConfig = mock(A2AConfig.class);
        when(a2aConfig.getDefaultVisibility()).thenReturn("entitled");
        when(authConfig.isAuthenticationEnabled()).thenReturn(true);
        when(authConfig.isRbacEnabled()).thenReturn(true);

        filter = new AgentVisibilityFilter();
        filter.a2aConfig = a2aConfig;
        filter.authConfig = authConfig;
        filter.securityIdentity = identity;
        filter.adminOverride = mock(AdminOverride.class);
        filter.rbac = rbac;
    }

    @Test
    void testNoFilteringWithoutAuthentication() {
        when(authConfig.isAuthenticationEnabled()).thenReturn(false);
        anonymous();
        assertEquals(List.of("public-card", "entitled-card", "private-card"), ids(filter.filterDtosByVisibility(ALL)));
    }

    @Test
    void testAnonymousSeesOnlyPublic() {
        anonymous();
        assertEquals(List.of("public-card"), ids(filter.filterDtosByVisibility(ALL)));
    }

    @Test
    void testUserWithoutReadAccessSeesOnlyPublic() {
        user("stranger");
        assertEquals(List.of("public-card"), ids(filter.filterDtosByVisibility(ALL)));
    }

    @Test
    void testReaderSeesPublicAndEntitled() {
        user("reader");
        when(rbac.isReadOnly()).thenReturn(true);
        assertEquals(List.of("public-card", "entitled-card"), ids(filter.filterDtosByVisibility(ALL)));
    }

    @Test
    void testAuthenticatedReadAccessGrantsEntitled() {
        user("stranger");
        when(authConfig.isAuthenticatedReadsEnabled()).thenReturn(true);
        assertEquals(List.of("public-card", "entitled-card"), ids(filter.filterDtosByVisibility(ALL)));
    }

    @Test
    void testOwnerSeesTheirPrivateCard() {
        user("owner");
        when(rbac.isDeveloper()).thenReturn(true);
        assertEquals(List.of("public-card", "entitled-card", "private-card"),
                ids(filter.filterDtosByVisibility(ALL)));
    }

    private void anonymous() {
        when(identity.isAnonymous()).thenReturn(true);
    }

    private void user(String name) {
        Principal principal = () -> name;
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
    }

    private static SearchedArtifactDto card(String id, String owner, String visibility) {
        SearchedArtifactDto dto = new SearchedArtifactDto();
        dto.setGroupId("g");
        dto.setArtifactId(id);
        dto.setOwner(owner);
        dto.setLabels(visibility == null ? Map.of() : Map.of("apicurio.agent.visibility", visibility));
        return dto;
    }

    private static List<String> ids(List<SearchedArtifactDto> dtos) {
        return dtos.stream().map(SearchedArtifactDto::getArtifactId).toList();
    }
}
