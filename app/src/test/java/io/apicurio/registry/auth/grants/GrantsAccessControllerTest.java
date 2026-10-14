package io.apicurio.registry.auth.grants;

import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.authz.RolePrincipal;
import io.apicurio.authz.User;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.kroxylicious.identity.Subject;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Registry side of per-resource authorization: resource naming, the mapping
 * of the Quarkus identity to a Kroxylicious subject, and decisions for each resource kind.
 */
public class GrantsAccessControllerTest {

    private static final String GRANTS = """
            {
              "config": {"admin_roles": ["sr-admin"]},
              "grants": [
                {"principal_role": "team-a-dev", "operation": "write", "resource_type": "artifact",
                 "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
                {"principal_role": "team-a-dev", "operation": "read", "resource_type": "artifact",
                 "resource_pattern_type": "exact", "resource_pattern": "team-a/victim", "deny": true},
                {"principal_role": "team-a-dev", "operation": "read", "resource_type": "group",
                 "resource_pattern_type": "exact", "resource_pattern": "TEAM-C"},
                {"principal_role": "team-a-dev", "operation": "read", "resource_type": "group",
                 "resource_pattern_type": "exact", "resource_pattern": "team-a"},
                {"principal": "alice", "operation": "read", "resource_type": "artifact",
                 "resource_pattern_type": "exact", "resource_pattern": "default/shared"}
              ]
            }""";

    @TempDir
    Path dir;

    /**
     * Storage in which team-b/owned is owned by "owner", and team-a/victim and group team-a exist.
     * Like MySQL and SQL Server, it matches IDs case-insensitively and returns the stored spelling.
     */
    private final RegistryStorage storage = mock(RegistryStorage.class);

    @BeforeEach
    void setUpStorage() {
        when(storage.getArtifactMetaData(anyString(), anyString()))
                .thenThrow(new ArtifactNotFoundException("not found"));
        doReturn(ArtifactMetaDataDto.builder().groupId("team-b").artifactId("owned").owner("owner").build())
                .when(storage).getArtifactMetaData("team-b", "owned");
        ArtifactMetaDataDto victim = ArtifactMetaDataDto.builder().groupId("team-a").artifactId("victim").build();
        for (String artifactId : List.of("victim", "VICTIM", "Victim")) {
            doReturn(victim).when(storage).getArtifactMetaData("team-a", artifactId);
            doReturn(victim).when(storage).getArtifactMetaData("TEAM-A", artifactId);
        }
        when(storage.getGroupMetaData(anyString())).thenThrow(new GroupNotFoundException("not found"));
        doReturn(GroupMetaDataDto.builder().groupId("team-a").build()).when(storage).getGroupMetaData("TEAM-A");
    }

    private GrantsAccessController controller(String user, String... roles) {
        GrantsAccessController controller = new GrantsAccessController();
        OTelMetricsProvider metrics = new OTelMetricsProvider();
        metrics.init();
        controller.metrics = metrics;
        controller.setStorage(storage);
        controller.setSecurityIdentity(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(user))
                .addRoles(Set.of(roles))
                .build());
        return controller;
    }

    private GrantsAuthorizer authorizer() throws Exception {
        Path grants = dir.resolve("grants.json");
        Files.writeString(grants, GRANTS);
        return GrantsAuthorizer.create(grants, Map.of(
                RegistryResourceType.Artifact.class, "artifact",
                RegistryResourceType.Group.class, "group"));
    }

    // ==================== Resource naming ====================

    @Test
    void buildResourceNameWithGroup() {
        assertEquals("my-group/art1", GrantsAccessController.buildResourceName("my-group", "art1"));
    }

    @Test
    void buildResourceNameNamesTheDefaultGroupDefault() {
        assertEquals("default/art1", GrantsAccessController.buildResourceName(null, "art1"));
        assertEquals("default/art1", GrantsAccessController.buildResourceName("default", "art1"));
    }

    @Test
    void normalizeGroupNamesTheDefaultGroupDefault() {
        assertEquals("default", GrantsAccessController.normalizeGroup(null));
        assertEquals("team-a", GrantsAccessController.normalizeGroup("team-a"));
    }

    @Test
    void registryResourceTypeImplies() {
        assertEquals(Set.of(RegistryResourceType.Artifact.Write, RegistryResourceType.Artifact.Read),
                RegistryResourceType.Artifact.Admin.implies());
        assertEquals(Set.of(RegistryResourceType.Artifact.Read), RegistryResourceType.Artifact.Write.implies());
        assertTrue(RegistryResourceType.Artifact.Read.implies().isEmpty());
    }

    // ==================== Uninitialized controller fails closed ====================

    @Test
    void uninitializedControllerDeniesEverything() {
        GrantsAccessController uninit = controller("alice", "sr-admin");

        assertNull(uninit.getAuthorizer());
        assertNull(uninit.getGrantsData());
        assertFalse(uninit.canReadArtifact("team-a", "x"));
        assertFalse(uninit.isAllowed(AuthorizedLevel.Read, AuthorizedResource.group("team-a")));
    }

    // ==================== Kroxylicious Subject mapping ====================

    @Test
    void currentSubjectMapsUserAndRoles() {
        Subject subject = controller("alice", "sr-developer", "sr-readonly").currentSubject();

        assertEquals(Optional.of(new User("alice")), subject.uniquePrincipalOfType(User.class));
        assertEquals(Set.of(new RolePrincipal("sr-developer"), new RolePrincipal("sr-readonly")),
                subject.allPrincipalsOfType(RolePrincipal.class));
        assertEquals(3, subject.principals().size());
    }

    @Test
    void anonymousIdentityGetsOnlyTheAnonymousRole() {
        GrantsAccessController controller = new GrantsAccessController();
        controller.setSecurityIdentity(QuarkusSecurityIdentity.builder().setAnonymous(true).build());

        Subject subject = controller.currentSubject();

        assertEquals(Set.of(new RolePrincipal(GrantsAccessController.ANONYMOUS_ROLE)), subject.principals());
        assertEquals(Optional.empty(), subject.uniquePrincipalOfType(User.class));
    }

    // ==================== Decisions per resource kind ====================

    @Test
    void artifactDecisionsFollowGrantsAndLevels() throws Exception {
        GrantsAccessController dev = controller("bob", "team-a-dev");
        try (GrantsAuthorizer authorizer = authorizer()) {
            dev.setAuthorizer(authorizer);

            assertTrue(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.artifact("team-a", "orders")));
            assertTrue(dev.isAllowed(AuthorizedLevel.Write, AuthorizedResource.artifact("team-a", "orders")));
            assertFalse(dev.isAllowed(AuthorizedLevel.Admin, AuthorizedResource.artifact("team-a", "orders")));
            assertFalse(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.artifact("team-b", "orders")));
        }
    }

    @Test
    void groupDecisionsUseGroupGrants() throws Exception {
        GrantsAccessController dev = controller("bob", "team-a-dev");
        try (GrantsAuthorizer authorizer = authorizer()) {
            dev.setAuthorizer(authorizer);

            assertTrue(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.group("team-a")));
            // An artifact prefix grant does not grant the group itself
            assertFalse(dev.isAllowed(AuthorizedLevel.Write, AuthorizedResource.group("team-a")));
            assertFalse(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.group("team-b")));
        }
    }

    @Test
    void defaultGroupIsMatchedAsDefault() throws Exception {
        GrantsAccessController alice = controller("alice");
        try (GrantsAuthorizer authorizer = authorizer()) {
            alice.setAuthorizer(authorizer);

            assertTrue(alice.canReadArtifact(null, "shared"));
            assertTrue(alice.canReadArtifact("default", "shared"));
            assertFalse(alice.canReadArtifact(null, "other"));
        }
    }

    @Test
    void contentIsReadableIfAnyArtifactUsingItIsReadable() throws Exception {
        GrantsAccessController dev = controller("bob", "team-a-dev");
        try (GrantsAuthorizer authorizer = authorizer()) {
            dev.setAuthorizer(authorizer);

            AuthorizedResource shared = AuthorizedResource.content(List.of(
                    AuthorizedResource.artifact("team-b", "x"), AuthorizedResource.artifact("team-a", "y")));
            AuthorizedResource foreign = AuthorizedResource.content(List.of(
                    AuthorizedResource.artifact("team-b", "x")));

            assertTrue(dev.isAllowed(AuthorizedLevel.Read, shared));
            assertFalse(dev.isAllowed(AuthorizedLevel.Read, foreign));
        }
    }

    @Test
    void contentIsReadableByTheOwnerOfAnArtifactUsingIt() throws Exception {
        GrantsAccessController owner = controller("owner");
        GrantsAccessController other = controller("other");
        AuthorizedResource content = AuthorizedResource.content(List.of(AuthorizedResource.artifact("team-b", "owned")));
        try (GrantsAuthorizer authorizer = authorizer()) {
            owner.setAuthorizer(authorizer);
            other.setAuthorizer(authorizer);

            assertTrue(owner.isAllowed(AuthorizedLevel.Read, content));
            assertFalse(other.isAllowed(AuthorizedLevel.Read, content));
        }
    }

    @Test
    void contentUsedByNoArtifactIsAllowedOnlyForGrantsAdmins() throws Exception {
        GrantsAccessController admin = controller("root", "sr-admin");
        try (GrantsAuthorizer authorizer = authorizer()) {
            admin.setAuthorizer(authorizer);

            assertTrue(admin.isAllowed(AuthorizedLevel.Read, AuthorizedResource.content(List.of())));
        }
    }

    @Test
    void contentUsedByNoArtifactIsDenied() throws Exception {
        GrantsAccessController dev = controller("bob", "team-a-dev");
        try (GrantsAuthorizer authorizer = authorizer()) {
            dev.setAuthorizer(authorizer);

            assertFalse(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.content(List.of())));
        }
    }

    @Test
    void adminRoleFromGrantsFileIsAllowedEverything() throws Exception {
        GrantsAccessController admin = controller("root", "sr-admin");
        try (GrantsAuthorizer authorizer = authorizer()) {
            admin.setAuthorizer(authorizer);

            assertTrue(admin.isAllowed(AuthorizedLevel.Admin, AuthorizedResource.artifact("any", "thing")));
            assertTrue(admin.isAllowed(AuthorizedLevel.Admin, AuthorizedResource.group("any")));
        }
    }

    @Test
    void levelNoneIsAlwaysAllowed() throws Exception {
        GrantsAccessController nobody = controller("nobody");
        try (GrantsAuthorizer authorizer = authorizer()) {
            nobody.setAuthorizer(authorizer);

            assertTrue(nobody.isAllowed(AuthorizedLevel.None, AuthorizedResource.artifact("team-b", "x")));
        }
    }

    // ==================== Stored identity ====================

    @Test
    void aCaseOnlyChangeDoesNotEscapeADeny() throws Exception {
        GrantsAccessController dev = controller("dev", "team-a-dev");
        try (GrantsAuthorizer authorizer = authorizer()) {
            dev.setAuthorizer(authorizer);

            // Storage resolves every spelling to team-a/victim, so the deny must apply to all of them
            for (String artifactId : List.of("victim", "VICTIM", "Victim")) {
                assertFalse(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.artifact("team-a", artifactId)),
                        artifactId);
                assertFalse(dev.isAllowed(AuthorizedLevel.Write, AuthorizedResource.artifact("TEAM-A", artifactId)),
                        artifactId);
            }
            // Other artifacts in the group are still writable
            assertTrue(dev.isAllowed(AuthorizedLevel.Write, AuthorizedResource.artifact("team-a", "other")));
        }
    }

    @Test
    void grantsApplyToTheStoredGroupSpelling() throws Exception {
        GrantsAccessController dev = controller("dev", "team-a-dev");
        try (GrantsAuthorizer authorizer = authorizer()) {
            dev.setAuthorizer(authorizer);

            // TEAM-A resolves to the stored group team-a, also for an artifact not created yet
            assertTrue(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.group("TEAM-A")));
            assertTrue(dev.isAllowed(AuthorizedLevel.Write, AuthorizedResource.artifact("TEAM-A", "new")));
            // A group that does not exist keeps the requested spelling
            assertTrue(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.group("TEAM-C")));
            assertFalse(dev.isAllowed(AuthorizedLevel.Read, AuthorizedResource.group("team-c")));
        }
    }
}
