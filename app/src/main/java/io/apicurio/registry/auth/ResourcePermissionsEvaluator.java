package io.apicurio.registry.auth;

import io.apicurio.registry.auth.grants.GrantsAccessController;
import io.apicurio.registry.auth.grants.GrantsAccessControllerConfig;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.kroxylicious.authorizer.service.Action;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Reports what the caller may do with each resource of a search or list result page: whether a
 * read, write or admin request on it would be authorized. Mirrors the point-access rules of
 * {@link AuthorizedInterceptor} and {@link ResourceAccessGuard} (bypasses, RBAC, owner-only
 * authorization, the owner bypass and per-resource grants), but evaluates the grants of a whole
 * page in a single access rules call, and does not audit the denials: nothing is being accessed.
 *
 * <p>The flags are advisory. Every request is still authorized on its own, so a stale or wrong
 * flag can hide or show an action, but never grant access.</p>
 */
@ApplicationScoped
public class ResourcePermissionsEvaluator {

    /**
     * A resource to report permissions for.
     *
     * @param resource an artifact or a group
     * @param owner the recorded owner of that resource, or null if it has none
     */
    public record Target(AuthorizedResource resource, String owner) {
    }

    /** What the caller may do with a resource. */
    public record Permissions(boolean read, boolean write, boolean admin) {
        static final Permissions ALL = new Permissions(true, true, true);
    }

    private static final List<AuthorizedLevel> LEVELS = List.of(AuthorizedLevel.Read, AuthorizedLevel.Write,
            AuthorizedLevel.Admin);

    @Inject
    ResourceAccessGuard guard;

    @Inject
    GrantsAccessControllerConfig grantsConfig;

    @Inject
    GrantsAccessController grantsAc;

    @Inject
    AuthConfig authConfig;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    AuthorizationLookups lookups;

    /** @return true if permissions are reported, i.e. per-resource authorization is enabled */
    public boolean isEnabled() {
        return grantsConfig.isEnabled();
    }

    /**
     * @return true if resource owners can affect the current caller's permissions; false for
     *         bypassed and anonymous callers, and when neither owner rule is enabled, so callers
     *         can skip looking owners up
     */
    public boolean usesOwners() {
        return !guard.isBypassed() && !guard.isAnonymous()
                && (grantsConfig.isOwnerBypassEnabled() || authConfig.ownerOnlyAuthorizationEnabled.get());
    }

    /**
     * @return the target for an artifact version: versions are authorized as their artifact, and
     *         its owner (not the version's) is the one the owner rules apply to
     * @param withOwner whether to look the owner up; pass {@link #usesOwners()}
     */
    public Target versionTarget(String groupId, String artifactId, boolean withOwner) {
        String owner = withOwner ? lookups.artifact(storage, groupId, artifactId)
                .map(ArtifactMetaDataDto::getOwner).orElse(null) : null;
        return new Target(AuthorizedResource.artifact(groupId, artifactId), owner);
    }

    /**
     * @return the caller's permissions for each target, in the same order
     */
    public List<Permissions> evaluate(List<Target> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        // Authentication disabled, trusted proxy authorization, admin override, RBAC admins
        if (guard.isBypassed()) {
            return Collections.nCopies(targets.size(), Permissions.ALL);
        }
        Rules rules = currentRules();

        List<Action> actions = new ArrayList<>();
        for (Target target : targets) {
            for (AuthorizedLevel level : LEVELS) {
                if (rules.roleAllows(level)) {
                    actions.add(action(level, target.resource()));
                }
            }
        }
        // One access rules evaluation for the whole page
        Set<Action> allowed = grantsAc.allowedActions(actions);

        List<Permissions> result = new ArrayList<>(targets.size());
        for (Target target : targets) {
            boolean ownerBypass = rules.ownerBypass && rules.caller.equals(target.owner());
            boolean[] flags = new boolean[LEVELS.size()];
            for (int i = 0; i < LEVELS.size(); i++) {
                AuthorizedLevel level = LEVELS.get(i);
                flags[i] = rules.roleAllows(level) && rules.ownerOnlyAllows(level, target)
                        && (ownerBypass || allowed.contains(action(level, target.resource())));
            }
            result.add(new Permissions(flags[0], flags[1], flags[2]));
        }
        return result;
    }

    private Rules currentRules() {
        boolean anonymous = guard.isAnonymous();
        boolean rbacEnabled = authConfig.roleBasedAuthorizationEnabled;
        if (anonymous) {
            // Anonymous callers may only read, when anonymous reads are enabled; never as owners
            return new Rules(null, authConfig.anonymousReadAccessEnabled.get(), false, false, false, false,
                    false);
        }
        boolean read = authConfig.authenticatedReadAccessEnabled.get() || !rbacEnabled || rbac.isReadOnly()
                || rbac.isDeveloper() || rbac.isAdmin();
        boolean write = !rbacEnabled || rbac.isDeveloper() || rbac.isAdmin();
        boolean admin = !rbacEnabled || rbac.isAdmin();
        return new Rules(securityIdentity.getPrincipal().getName(), read, write, admin,
                authConfig.ownerOnlyAuthorizationEnabled.get(),
                authConfig.ownerOnlyAuthorizationLimitGroupAccess.get(), grantsConfig.isOwnerBypassEnabled());
    }

    private static Action action(AuthorizedLevel level, AuthorizedResource resource) {
        return switch (resource.kind()) {
            case ARTIFACT -> new Action(GrantsAccessController.toArtifactOp(level),
                    GrantsAccessController.buildResourceName(resource.groupId(), resource.artifactId()));
            case GROUP -> new Action(GrantsAccessController.toGroupOp(level),
                    GrantsAccessController.normalizeGroup(resource.groupId()));
            case CONTENT -> throw new IllegalArgumentException("Permissions are reported for artifacts and groups");
        };
    }

    /**
     * The caller-wide part of the rules: role checks (RBAC, anonymous and authenticated read
     * access) do not depend on the resource.
     */
    private record Rules(String caller, boolean read, boolean write, boolean admin, boolean ownerOnly,
            boolean ownerOnlyLimitsGroups, boolean ownerBypass) {

        boolean roleAllows(AuthorizedLevel level) {
            return switch (level) {
                case Read -> read;
                case Write -> write;
                default -> admin;
            };
        }

        /**
         * Owner-only authorization restricts writes only, and is lenient: a resource without an
         * owner is allowed. Groups are restricted only when configured to be.
         */
        boolean ownerOnlyAllows(AuthorizedLevel level, Target target) {
            if (!ownerOnly || level != AuthorizedLevel.Write || target.owner() == null) {
                return true;
            }
            if (target.resource().kind() == AuthorizedResource.Kind.GROUP && !ownerOnlyLimitsGroups) {
                return true;
            }
            return target.owner().equals(caller);
        }
    }
}
