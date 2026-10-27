package io.apicurio.registry.auth;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.NotFoundException;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Storage lookups made by authorization, remembered for the rest of the current request. A single
 * request is authorized by several checks (owner-only authorization, the owner bypass, grants on
 * the stored IDs, the interceptor and {@link ResourceAccessGuard}), which would otherwise read the
 * same metadata repeatedly.
 *
 * <p>Results are kept only for one request, so they never outlive a change made by another
 * request. Within a request, a lookup reflects the state when it was first made: authorization
 * runs before the operation, so this is the state the operation is authorized against. Without an
 * active request context (e.g. background jobs), every lookup reads storage.</p>
 *
 * <p><b>A missing resource stays missing for the rest of the request</b>, even if the request
 * creates it afterwards. Owner-only authorization allows a missing resource, so code that creates a
 * resource and then authorizes an operation on it within the same request must not rely on these
 * lookups: it would be authorized as if the resource, and its owner, did not exist. Authorize
 * everything before writing, as the existing write paths do.</p>
 */
@ApplicationScoped
public class AuthorizationLookups {

    @Inject
    RequestAuthorizationLookups perRequest;

    /** @return the artifact's metadata, or empty if it does not exist */
    public Optional<ArtifactMetaDataDto> artifact(RegistryStorage storage, String groupId, String artifactId) {
        return remember(RequestAuthorizationLookups::artifacts, Arrays.asList(groupKey(groupId), artifactId),
                () -> find(() -> storage.getArtifactMetaData(groupId, artifactId)));
    }

    /** @return the group's metadata, or empty if it does not exist */
    public Optional<GroupMetaDataDto> group(RegistryStorage storage, String groupId) {
        return remember(RequestAuthorizationLookups::groups, groupKey(groupId),
                () -> find(() -> storage.getGroupMetaData(groupId)));
    }

    /** @return the metadata of the version with this global ID, or empty if there is none */
    public Optional<ArtifactVersionMetaDataDto> version(RegistryStorage storage, long globalId) {
        return remember(RequestAuthorizationLookups::versions, globalId,
                () -> find(() -> storage.getArtifactVersionMetaData(globalId)));
    }

    /** @return the versions using this content, or empty if the content does not exist */
    public Optional<List<ArtifactVersionMetaDataDto>> versionsUsingContent(RegistryStorage storage,
            long contentId) {
        return remember(RequestAuthorizationLookups::contentUsers, contentId,
                () -> find(() -> List.copyOf(storage.getArtifactVersionsByContentId(contentId))));
    }

    /**
     * Storage treats a null group and {@code default} as the default group; IDs are otherwise
     * kept as requested, so different spellings are separate lookups.
     */
    private static String groupKey(String groupId) {
        return groupId == null || "default".equals(groupId) ? "\0default" : groupId;
    }

    private <K, V> Optional<V> remember(Function<RequestAuthorizationLookups, Map<K, Optional<V>>> cache,
            K key, Supplier<Optional<V>> loader) {
        ManagedContext requestContext = Arc.container() == null ? null : Arc.container().requestContext();
        if (perRequest == null || requestContext == null || !requestContext.isActive()) {
            return loader.get();
        }
        Map<K, Optional<V>> map = cache.apply(perRequest);
        if (!map.containsKey(key)) {
            // Not computeIfAbsent: the storage read must not run while holding a map lock
            map.putIfAbsent(key, loader.get());
        }
        return map.get(key);
    }

    private static <V> Optional<V> find(Supplier<V> lookup) {
        try {
            return Optional.ofNullable(lookup.get());
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }
}
