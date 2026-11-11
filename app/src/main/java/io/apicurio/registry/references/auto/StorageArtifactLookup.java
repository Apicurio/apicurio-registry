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
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.BranchNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@link ArtifactLookup} backed by the registry storage, honoring per-resource read access.
 */
@ApplicationScoped
public class StorageArtifactLookup implements ArtifactLookup {

    /** Size of each storage search page. */
    public static final int PAGE_SIZE = 50;
    /** Readable candidates beyond this count make the result truncated. */
    public static final int MAX_CANDIDATES = 50;
    /** Total search hits (readable or not) scanned before giving up and reporting truncation. */
    public static final int MAX_SCANNED = 1000;

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
    public StructureSearchResult searchByStructure(String groupId, List<String> structureFilters) {
        Set<SearchFilter> filters = new HashSet<>();
        String rawGroupId = new GroupId(groupId).getRawGroupIdWithNull();
        filters.add(SearchFilter.ofGroupId(rawGroupId));
        structureFilters.forEach(f -> filters.add(SearchFilter.ofStructure(f)));
        // Page through every hit: the groupId filter is a wildcard match ('*' is legal in group ids) and
        // some hits may be unreadable, so a single page could hide a readable candidate and turn an
        // ambiguous match into a seemingly unique one. Stop (and report truncation) once there are more
        // readable candidates than can be confirmed, or too many hits to scan.
        List<String> readable = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<SearchedArtifactDto> page = storage.searchArtifacts(filters, OrderBy.artifactId,
                    OrderDirection.asc, offset, PAGE_SIZE, true).getArtifacts();
            for (SearchedArtifactDto artifact : page) {
                if (Objects.equals(rawGroupId, artifact.getGroupId())
                        && accessGuard.canRead(groupId, artifact.getArtifactId())) {
                    readable.add(artifact.getArtifactId());
                    if (readable.size() > MAX_CANDIDATES) {
                        return new StructureSearchResult(List.copyOf(readable), true);
                    }
                }
            }
            offset += page.size();
            if (page.size() < PAGE_SIZE) {
                return StructureSearchResult.complete(List.copyOf(readable));
            }
            if (offset >= MAX_SCANNED) {
                return new StructureSearchResult(List.copyOf(readable), true);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ContentHandle> content(String groupId, String artifactId, String version) {
        if (!accessGuard.canRead(groupId, artifactId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(storage.getArtifactVersionContent(new GroupId(groupId).getRawGroupIdWithNull(),
                    artifactId, version).getContent());
        } catch (ArtifactNotFoundException | VersionNotFoundException e) {
            return Optional.empty();
        }
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
