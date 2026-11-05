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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchResultPermissionsTest {

    private final ResourcePermissionsEvaluator evaluator = mock(ResourcePermissionsEvaluator.class);

    private SearchResultPermissions decorator(boolean enabled) {
        when(evaluator.isEnabled()).thenReturn(enabled);
        SearchResultPermissions decorator = new SearchResultPermissions();
        decorator.evaluator = evaluator;
        return decorator;
    }

    private static SearchedArtifact artifact(String artifactId, String owner) {
        SearchedArtifact a = new SearchedArtifact();
        a.setGroupId("g");
        a.setArtifactId(artifactId);
        a.setOwner(owner);
        return a;
    }

    @Test
    void permissionsAreOmittedWhenPerResourceAuthorizationIsDisabled() {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setArtifacts(new ArrayList<>(List.of(artifact("a", "o"))));

        decorator(false).addTo(results);

        assertNull(results.getArtifacts().get(0).getPermissions());
        verify(evaluator, never()).evaluate(anyList());
    }

    @Test
    void artifactPermissionsAreSetInOrderFromTheirOwnOwner() {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setArtifacts(new ArrayList<>(List.of(artifact("a", "o1"), artifact("b", "o2"))));
        when(evaluator.evaluate(List.of(new Target(AuthorizedResource.artifact("g", "a"), "o1"),
                new Target(AuthorizedResource.artifact("g", "b"), "o2"))))
                .thenReturn(List.of(new Permissions(true, false, false), new Permissions(true, true, true)));

        decorator(true).addTo(results);

        assertEquals(new ResourcePermissions(true, false, false), results.getArtifacts().get(0).getPermissions());
        assertEquals(new ResourcePermissions(true, true, true), results.getArtifacts().get(1).getPermissions());
    }

    @Test
    void groupPermissionsUseTheGroup() {
        SearchedGroup group = new SearchedGroup();
        group.setGroupId("g");
        group.setOwner("o");
        GroupSearchResults results = new GroupSearchResults();
        results.setGroups(new ArrayList<>(List.of(group)));
        when(evaluator.evaluate(List.of(new Target(AuthorizedResource.group("g"), "o"))))
                .thenReturn(List.of(new Permissions(true, true, false)));

        decorator(true).addTo(results);

        assertEquals(new ResourcePermissions(true, true, false), group.getPermissions());
    }

    private static VersionSearchResults oneVersion(SearchedVersion version) {
        version.setGroupId("g");
        version.setArtifactId("a");
        version.setOwner("version-owner");
        VersionSearchResults results = new VersionSearchResults();
        results.setVersions(new ArrayList<>(List.of(version)));
        return results;
    }

    @Test
    void versionPermissionsUseTheArtifactTarget() {
        SearchedVersion version = new SearchedVersion();
        VersionSearchResults results = oneVersion(version);
        Target artifactTarget = new Target(AuthorizedResource.artifact("g", "a"), "artifact-owner");
        when(evaluator.usesOwners()).thenReturn(true);
        when(evaluator.versionTarget("g", "a", true)).thenReturn(artifactTarget);
        when(evaluator.evaluate(List.of(artifactTarget))).thenReturn(List.of(new Permissions(true, true, false)));

        decorator(true).addTo(results);

        assertEquals(new ResourcePermissions(true, true, false), version.getPermissions());
    }

    @Test
    void versionOwnersAreNotLookedUpWhenTheyCannotMatter() {
        SearchedVersion version = new SearchedVersion();
        VersionSearchResults results = oneVersion(version);
        Target artifactTarget = new Target(AuthorizedResource.artifact("g", "a"), null);
        when(evaluator.usesOwners()).thenReturn(false);
        when(evaluator.versionTarget("g", "a", false)).thenReturn(artifactTarget);
        when(evaluator.evaluate(List.of(artifactTarget))).thenReturn(List.of(new Permissions(true, true, true)));

        decorator(true).addTo(results);

        assertEquals(new ResourcePermissions(true, true, true), version.getPermissions());
        verify(evaluator, never()).versionTarget("g", "a", true);
    }

    @Test
    void anEmptyPageIsLeftAlone() {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setArtifacts(new ArrayList<>());

        decorator(true).addTo(results);

        verify(evaluator, never()).evaluate(anyList());
    }
}
