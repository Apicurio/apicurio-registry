package io.apicurio.registry.auth;

import io.apicurio.authz.Artifact;
import io.apicurio.authz.Group;
import io.apicurio.registry.auth.ResourcePermissionsEvaluator.Permissions;
import io.apicurio.registry.auth.ResourcePermissionsEvaluator.Target;
import io.apicurio.registry.auth.grants.GrantsAccessController;
import io.apicurio.registry.auth.grants.GrantsAccessControllerConfig;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.kroxylicious.authorizer.service.Action;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ResourcePermissionsEvaluator} must report, per resource, exactly what point access would
 * allow, with one access rules evaluation per page and no audited decisions.
 */
class ResourcePermissionsEvaluatorTest {

    private static final Permissions NONE = new Permissions(false, false, false);
    private static final Permissions ALL = new Permissions(true, true, true);

    private final ResourceAccessGuard guard = mock(ResourceAccessGuard.class);
    private final GrantsAccessController grantsAc = mock(GrantsAccessController.class);
    private final GrantsAccessControllerConfig grantsConfig = mock(GrantsAccessControllerConfig.class);
    private final RoleBasedAccessController rbac = mock(RoleBasedAccessController.class);
    private final RegistryStorage storage = mock(RegistryStorage.class);
    private final AuthConfig authConfig = new AuthConfig();
    private final Set<Action> granted = new HashSet<>();

    private boolean ownerOnly;
    private boolean ownerOnlyLimitsGroups;
    private boolean anonymousRead;
    private boolean authenticatedRead;

    @BeforeEach
    void setUp() {
        authConfig.roleBasedAuthorizationEnabled = true;
        authConfig.ownerOnlyAuthorizationEnabled = () -> ownerOnly;
        authConfig.ownerOnlyAuthorizationLimitGroupAccess = () -> ownerOnlyLimitsGroups;
        authConfig.anonymousReadAccessEnabled = () -> anonymousRead;
        authConfig.authenticatedReadAccessEnabled = () -> authenticatedRead;
        when(grantsConfig.isEnabled()).thenReturn(true);
        when(grantsConfig.isOwnerBypassEnabled()).thenReturn(true);
        // The access rules allow exactly the actions in 'granted'
        when(grantsAc.allowedActions(anyList())).thenAnswer(inv -> {
            List<Action> asked = inv.getArgument(0);
            Set<Action> allowed = new HashSet<>(asked);
            allowed.retainAll(granted);
            return allowed;
        });
    }

    private ResourcePermissionsEvaluator evaluator(String user) {
        ResourcePermissionsEvaluator evaluator = new ResourcePermissionsEvaluator();
        evaluator.guard = guard;
        evaluator.grantsAc = grantsAc;
        evaluator.grantsConfig = grantsConfig;
        evaluator.authConfig = authConfig;
        evaluator.rbac = rbac;
        evaluator.storage = storage;
        evaluator.lookups = new AuthorizationLookups();
        evaluator.securityIdentity = user == null ? QuarkusSecurityIdentity.builder().setAnonymous(true).build()
                : QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal(user)).build();
        when(guard.isAnonymous()).thenReturn(user == null);
        return evaluator;
    }

    private void developer() {
        when(rbac.isDeveloper()).thenReturn(true);
    }

    private static Target artifact(String groupId, String artifactId, String owner) {
        return new Target(AuthorizedResource.artifact(groupId, artifactId), owner);
    }

    private void grantArtifact(Artifact op, String name) {
        granted.add(new Action(op, name));
    }

    @Test
    void bypassedCallersMayDoEverythingWithoutEvaluatingTheRules() {
        when(guard.isBypassed()).thenReturn(true);

        List<Permissions> result = evaluator("alice").evaluate(List.of(artifact("g", "a", null),
                new Target(AuthorizedResource.group("g"), null)));

        assertEquals(List.of(ALL, ALL), result);
        verify(grantsAc, never()).allowedActions(anyList());
    }

    @Test
    void eachLevelFollowsItsOwnGrantAndTheWholePageIsEvaluatedInOneCall() {
        // Without RBAC, the grants alone decide every level
        authConfig.roleBasedAuthorizationEnabled = false;
        grantArtifact(Artifact.READ, "g/read");
        grantArtifact(Artifact.WRITE, "g/write");
        grantArtifact(Artifact.READ, "g/write");
        grantArtifact(Artifact.ADMIN, "g/admin");
        grantArtifact(Artifact.WRITE, "g/admin");
        grantArtifact(Artifact.READ, "g/admin");

        List<Permissions> result = evaluator("dev").evaluate(List.of(artifact("g", "read", null),
                artifact("g", "write", null), artifact("g", "admin", null), artifact("g", "hidden", null)));

        assertEquals(List.of(new Permissions(true, false, false), new Permissions(true, true, false),
                new Permissions(true, true, true), NONE), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Action>> asked = ArgumentCaptor.forClass(List.class);
        verify(grantsAc, times(1)).allowedActions(asked.capture());
        // Three levels for each of the four resources, in a single call
        assertEquals(12, asked.getValue().size());
    }

    @Test
    void developersCannotAdministerEvenWithAnAdminGrantWhenRbacIsEnabled() {
        developer();
        grantArtifact(Artifact.ADMIN, "g/a");

        assertEquals(List.of(NONE), evaluator("dev").evaluate(List.of(artifact("g", "a", null))));
        // A level the role denies is not even asked of the access rules
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Action>> asked = ArgumentCaptor.forClass(List.class);
        verify(grantsAc).allowedActions(asked.capture());
        assertEquals(List.of(new Action(Artifact.READ, "g/a"), new Action(Artifact.WRITE, "g/a")), asked.getValue());
    }

    @Test
    void withoutRbacTheGrantsAloneDecideAdmin() {
        authConfig.roleBasedAuthorizationEnabled = false;
        grantArtifact(Artifact.ADMIN, "g/a");

        assertEquals(List.of(new Permissions(false, false, true)),
                evaluator("someone").evaluate(List.of(artifact("g", "a", null))));
    }

    @Test
    void readOnlyUsersCannotWriteEvenWithAWriteGrant() {
        when(rbac.isReadOnly()).thenReturn(true);
        grantArtifact(Artifact.READ, "g/a");
        grantArtifact(Artifact.WRITE, "g/a");

        assertEquals(List.of(new Permissions(true, false, false)),
                evaluator("reader").evaluate(List.of(artifact("g", "a", null))));
    }

    @Test
    void ownersBypassTheGrantsButNotTheirRole() {
        developer();

        // No grant at all: the owner may still read and write, but a developer may not administer
        assertEquals(List.of(new Permissions(true, true, false)),
                evaluator("dev").evaluate(List.of(artifact("g", "mine", "dev"))));
    }

    @Test
    void groupOwnersBypassTheGrantsToo() {
        developer();

        assertEquals(List.of(new Permissions(true, true, false)),
                evaluator("dev").evaluate(List.of(new Target(AuthorizedResource.group("g"), "dev"))));
    }

    @Test
    void theOwnerBypassCanBeDisabled() {
        developer();
        when(grantsConfig.isOwnerBypassEnabled()).thenReturn(false);

        assertEquals(List.of(NONE), evaluator("dev").evaluate(List.of(artifact("g", "mine", "dev"))));
    }

    @Test
    void ownerOnlyAuthorizationRestrictsWritesToOwnedOrUnownedArtifacts() {
        developer();
        ownerOnly = true;
        for (String name : List.of("g/theirs", "g/mine", "g/unowned")) {
            grantArtifact(Artifact.READ, name);
            grantArtifact(Artifact.WRITE, name);
        }

        List<Permissions> result = evaluator("dev").evaluate(List.of(artifact("g", "theirs", "other"),
                artifact("g", "mine", "dev"), artifact("g", "unowned", null)));

        assertEquals(List.of(new Permissions(true, false, false), new Permissions(true, true, false),
                new Permissions(true, true, false)), result);
    }

    @Test
    void ownerOnlyAuthorizationRestrictsGroupsOnlyWhenConfiguredTo() {
        developer();
        ownerOnly = true;
        granted.add(new Action(Group.WRITE, "g"));
        Target group = new Target(AuthorizedResource.group("g"), "other");

        assertEquals(List.of(new Permissions(false, true, false)), evaluator("dev").evaluate(List.of(group)));
        ownerOnlyLimitsGroups = true;
        assertEquals(List.of(NONE), evaluator("dev").evaluate(List.of(group)));
    }

    @Test
    void anonymousCallersMayOnlyReadWhatTheRulesAllowAndOnlyWhenEnabled() {
        // Owner rules never apply to anonymous callers, whoever owns the resource
        ownerOnly = true;
        grantArtifact(Artifact.READ, "g/a");
        grantArtifact(Artifact.WRITE, "g/a");
        List<Target> targets = List.of(artifact("g", "a", "someone"), artifact("g", "b", "someone"));

        assertEquals(List.of(NONE, NONE), evaluator(null).evaluate(targets));
        anonymousRead = true;
        assertEquals(List.of(new Permissions(true, false, false), NONE), evaluator(null).evaluate(targets));
    }

    @Test
    void authenticatedReadAccessGrantsReadButNotWriteToUsersWithoutARole() {
        authenticatedRead = true;
        grantArtifact(Artifact.READ, "g/a");
        grantArtifact(Artifact.WRITE, "g/a");

        assertEquals(List.of(new Permissions(true, false, false)),
                evaluator("norole").evaluate(List.of(artifact("g", "a", null))));
    }

    @Test
    void versionsUseTheirArtifactsOwner() {
        when(storage.getArtifactMetaData("g", "a"))
                .thenReturn(ArtifactMetaDataDto.builder().groupId("g").artifactId("a").owner("dev").build());

        Target target = evaluator("dev").versionTarget("g", "a", true);

        assertEquals(artifact("g", "a", "dev"), target);
    }

    @Test
    void versionOwnersAreNotLookedUpWhenTheyCannotMatter() {
        assertEquals(artifact("g", "a", null), evaluator("dev").versionTarget("g", "a", false));
        verify(storage, never()).getArtifactMetaData("g", "a");
    }

    @Test
    void ownersMatterOnlyForRestrictedAuthenticatedCallersWithAnOwnerRule() {
        assertEquals(true, evaluator("dev").usesOwners());

        // Bypassed callers get everything, anonymous callers never own anything
        when(guard.isBypassed()).thenReturn(true);
        assertEquals(false, evaluator("alice").usesOwners());
        when(guard.isBypassed()).thenReturn(false);
        assertEquals(false, evaluator(null).usesOwners());

        // Neither the owner bypass nor owner-only authorization is enabled
        when(grantsConfig.isOwnerBypassEnabled()).thenReturn(false);
        assertEquals(false, evaluator("dev").usesOwners());
        ownerOnly = true;
        assertEquals(true, evaluator("dev").usesOwners());
    }

    @Test
    void aVersionOfAnArtifactThatCannotBeFoundHasNoOwner() {
        when(storage.getArtifactMetaData("g", "gone"))
                .thenThrow(new ArtifactNotFoundException("gone"));

        assertEquals(artifact("g", "gone", null), evaluator("dev").versionTarget("g", "gone", true));
    }

    @Test
    void anEmptyPageEvaluatesNothing() {
        assertEquals(List.of(), evaluator("dev").evaluate(List.of()));
        verify(grantsAc, never()).allowedActions(anyList());
    }
}
