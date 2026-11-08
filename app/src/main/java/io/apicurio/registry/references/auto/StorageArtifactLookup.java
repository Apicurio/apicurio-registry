package io.apicurio.registry.references.auto;

import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.BranchNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link ArtifactLookup} backed by the registry storage, honoring per-resource read access.
 */
@ApplicationScoped
public class StorageArtifactLookup implements ArtifactLookup {

    private static final int MAX_SEARCH_RESULTS = 50;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ResourceAccessGuard accessGuard;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> latestVersion(String groupId, String artifactId) {
        return resolveLatest(groupId, artifactId).map(GAV::getRawVersionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> searchByStructure(String groupId, List<String> structureFilters) {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()));
        structureFilters.forEach(f -> filters.add(SearchFilter.ofStructure(f)));
        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, OrderBy.artifactId,
                OrderDirection.asc, 0, MAX_SEARCH_RESULTS, true);
        return results.getArtifacts().stream()
                .map(SearchedArtifactDto::getArtifactId)
                .filter(artifactId -> accessGuard.canRead(groupId, artifactId))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ContentHandle> latestContent(String groupId, String artifactId) {
        return resolveLatest(groupId, artifactId).flatMap(gav -> {
            try {
                return Optional.of(storage.getArtifactVersionContent(gav.getRawGroupIdWithNull(),
                        gav.getRawArtifactId(), gav.getRawVersionId()).getContent());
            } catch (ArtifactNotFoundException | VersionNotFoundException e) {
                return Optional.empty();
            }
        });
    }

    private Optional<GAV> resolveLatest(String groupId, String artifactId) {
        if (!accessGuard.canRead(groupId, artifactId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(VersionExpressionParser.parse(new GA(groupId, artifactId), "branch=latest",
                    (ga, branchId) -> storage.getBranchTip(ga, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST)));
        } catch (ArtifactNotFoundException | VersionNotFoundException | BranchNotFoundException e) {
            return Optional.empty();
        }
    }
}
