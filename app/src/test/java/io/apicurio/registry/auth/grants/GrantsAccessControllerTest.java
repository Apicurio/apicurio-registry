package io.apicurio.registry.auth.grants;

import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.authz.GrantsData;
import io.apicurio.authz.RolePrincipal;
import io.apicurio.authz.SearchFilterData;
import io.apicurio.authz.User;
import io.kroxylicious.identity.Subject;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrantsAccessControllerTest {

    @Test
    void buildResourceNameWithGroup() {
        assertEquals("my-group/art1", GrantsAccessController.buildResourceName("my-group", "art1"));
    }

    @Test
    void buildResourceNameNullGroupDefaultsToDefault() {
        assertEquals("default/art1", GrantsAccessController.buildResourceName(null, "art1"));
    }

    @Test
    void uninitializedControllerDenies() {
        GrantsAccessController uninit = new GrantsAccessController();
        assertFalse(uninit.canReadArtifact("team-a", "x"));
    }

    @Test
    void uninitializedControllerGrantsDataNull() {
        GrantsAccessController uninit = new GrantsAccessController();
        assertNull(uninit.getGrantsData());
    }

    @Test
    void grantsDataParsingFromSharedModule() {
        String json = """
                {
                  "config": {"admin_roles": ["sr-admin"]},
                  "grants": [
                    {"principal": "alice", "operation": "write", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"}
                  ]
                }""";
        GrantsData data = GrantsData.parse(json);
        assertNotNull(data);
        assertTrue(data.isAdmin(Set.of("sr-admin")));
        assertFalse(data.isAdmin(Set.of("sr-developer")));
        assertEquals(1, data.getGrantsForUser("alice", Set.of()).size());
        assertTrue(data.getGrantsForUser("bob", Set.of()).isEmpty());
    }

    @Test
    void grantsDataSearchFilterFromSharedModule() {
        String json = """
                {
                  "grants": [
                    {"principal": "alice", "operation": "write", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
                    {"principal": "alice", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "shared/"}
                  ]
                }""";
        GrantsData data = GrantsData.parse(json);
        SearchFilterData filterData = data.getSearchFilterData("alice", Set.of(), "artifact", "/");
        assertNotNull(filterData);
        assertFalse(filterData.allowAll());
        assertTrue(filterData.allowedGroups().contains("team-a"));
        assertTrue(filterData.allowedGroups().contains("shared"));
    }

    @Test
    void grantsDataMalformedJsonFailsClosed() {
        GrantsData data = GrantsData.parse("broken{{{");
        assertFalse(data.isAdmin(Set.of("sr-admin")));
        assertTrue(data.getGrantsForUser("anyone", Set.of()).isEmpty());
    }

    @Test
    void registryResourceTypeImplies() {
        assertTrue(RegistryResourceType.Artifact.Admin.implies().contains(RegistryResourceType.Artifact.Write));
        assertTrue(RegistryResourceType.Artifact.Admin.implies().contains(RegistryResourceType.Artifact.Read));
        assertTrue(RegistryResourceType.Artifact.Write.implies().contains(RegistryResourceType.Artifact.Read));
        assertTrue(RegistryResourceType.Artifact.Read.implies().isEmpty());
    }

    @Test
    void uninitializedControllerAuthorizerIsNull() {
        GrantsAccessController uninit = new GrantsAccessController();
        assertNull(uninit.getAuthorizer());
    }

    @Test
    void subGroupPrefixGrantDoesNotCollapseToFullGroupInSearchFilter() {
        String json = """
                {
                  "grants": [
                    {"principal": "alice", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/secret/"}
                  ]
                }""";
        GrantsData data = GrantsData.parse(json);
        SearchFilterData filterData = data.getSearchFilterData("alice", Set.of(), "artifact", "/");
        assertNotNull(filterData);
        assertFalse(filterData.allowAll());
        assertTrue(filterData.allowedGroups().isEmpty());
        assertTrue(filterData.allowedPrefixResources().contains("team-a/secret/"));
    }

    // ==================== Kroxylicious Subject mapping ====================

    private static GrantsAccessController controllerFor(String user, String... roles) {
        GrantsAccessController controller = new GrantsAccessController();
        controller.securityIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(user))
                .addRoles(Set.of(roles))
                .build();
        return controller;
    }

    @Test
    void currentSubjectMapsUserAndRoles() {
        Subject subject = controllerFor("alice", "sr-developer", "sr-readonly").currentSubject();

        assertEquals(Optional.of(new User("alice")), subject.uniquePrincipalOfType(User.class));
        assertEquals(Set.of(new RolePrincipal("sr-developer"), new RolePrincipal("sr-readonly")),
                subject.allPrincipalsOfType(RolePrincipal.class));
        assertEquals(3, subject.principals().size());
    }

    @Test
    void currentSubjectIsAnonymousForAnonymousIdentity() {
        GrantsAccessController controller = new GrantsAccessController();
        controller.securityIdentity = QuarkusSecurityIdentity.builder().setAnonymous(true).build();

        Subject subject = controller.currentSubject();

        assertTrue(subject.isAnonymous());
        assertEquals(Subject.anonymous(), subject);
    }

    @Test
    void canReadArtifactEvaluatesGrantsThroughKroxyliciousAuthorizer(@TempDir Path dir) throws Exception {
        Path grants = dir.resolve("grants.json");
        Files.writeString(grants, """
                {
                  "grants": [
                    {"principal_role": "team-a-dev", "operation": "read", "resource_type": "artifact",
                     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"}
                  ]
                }""");
        GrantsAccessController allowed = controllerFor("alice", "team-a-dev");
        GrantsAccessController denied = controllerFor("bob", "team-b-dev");
        try (GrantsAuthorizer authorizer = GrantsAuthorizer.create(grants, Map.of(
                RegistryResourceType.Artifact.class, "artifact",
                RegistryResourceType.Group.class, "group"))) {
            allowed.setAuthorizer(authorizer);
            denied.setAuthorizer(authorizer);

            assertTrue(allowed.canReadArtifact("team-a", "orders"));
            assertFalse(allowed.canReadArtifact("team-b", "orders"));
            assertFalse(denied.canReadArtifact("team-a", "orders"));
        }
    }
}
