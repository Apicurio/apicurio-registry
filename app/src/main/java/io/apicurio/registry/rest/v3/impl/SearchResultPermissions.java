package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.auth.ResourcePermissionsEvaluator;
import io.apicurio.registry.auth.ResourcePermissionsEvaluator.Permissions;
import io.apicurio.registry.auth.ResourcePermissionsEvaluator.Target;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.ResourcePermissions;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.apicurio.registry.rest.v3.beans.SearchedVersion;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Adds the caller's {@link ResourcePermissions} to each item of a v3 search or list result page,
 * when per-resource authorization is enabled. Otherwise results are returned unchanged and the
 * {@code permissions} field is omitted.
 */
@ApplicationScoped
public class SearchResultPermissions {

    @Inject
    ResourcePermissionsEvaluator evaluator;

    public ArtifactSearchResults addTo(ArtifactSearchResults results) {
        if (evaluator.isEnabled()) {
            apply(results.getArtifacts(),
                    a -> new Target(AuthorizedResource.artifact(a.getGroupId(), a.getArtifactId()), a.getOwner()),
                    SearchedArtifact::setPermissions);
        }
        return results;
    }

    public GroupSearchResults addTo(GroupSearchResults results) {
        if (evaluator.isEnabled()) {
            apply(results.getGroups(), g -> new Target(AuthorizedResource.group(g.getGroupId()), g.getOwner()),
                    SearchedGroup::setPermissions);
        }
        return results;
    }

    public VersionSearchResults addTo(VersionSearchResults results) {
        if (evaluator.isEnabled()) {
            // A version's owner is not its artifact's: versions are authorized as their artifact.
            // Its owner is looked up only when it can matter (not for admins or anonymous callers).
            boolean withOwner = evaluator.usesOwners();
            apply(results.getVersions(), v -> evaluator.versionTarget(v.getGroupId(), v.getArtifactId(), withOwner),
                    SearchedVersion::setPermissions);
        }
        return results;
    }

    private <T> void apply(List<T> items, Function<T, Target> target, BiConsumer<T, ResourcePermissions> setter) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Permissions> permissions = evaluator.evaluate(items.stream().map(target).toList());
        for (int i = 0; i < items.size(); i++) {
            Permissions p = permissions.get(i);
            setter.accept(items.get(i), new ResourcePermissions(p.read(), p.write(), p.admin()));
        }
    }
}
