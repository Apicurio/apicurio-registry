package io.apicurio.registry.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.error.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

/**
 * Cross-request cache of a global ID's coordinates (group, artifact, version) — see
 * {@link GlobalIdCoordinates} for why it holds nothing else.
 * <p>
 * A global ID is assigned once, from a monotonic sequence that is never rewound below the highest
 * ID already issued (see {@code SqlSequenceRepository.resetGlobalId}), so a resolved entry is never
 * invalidated: deleting the version it names does not change what the ID once named, and nothing
 * else can ever be assigned that same ID. A global ID that does not resolve (not yet assigned, or
 * never will be) is not cached, since nothing here guarantees it never will.
 * <p>
 * Bounded by entry count ({@link #maxSize}), not by a TTL: unlike {@link
 * io.apicurio.registry.storage.decorator.ContentCacheDecorator}'s hot-path cache, there is no
 * staleness window to bound, because the cached value cannot become stale.
 */
@ApplicationScoped
public class GlobalIdCoordinatesCache {

    private static final String CACHE_NAME = "globalIdCoordinates";

    @ConfigProperty(name = "apicurio.storage.global-id-cache.enabled", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "Enable an in-memory, cross-request cache of the "
            + "(group, artifact, version) coordinates a global ID resolves to. The cache is never "
            + "invalidated: a global ID is assigned once and never reassigned, so its coordinates "
            + "never change", availableSince = "3.4.0")
    boolean enabled;

    @ConfigProperty(name = "apicurio.storage.global-id-cache.max-size", defaultValue = "100000")
    @Info(category = CATEGORY_STORAGE, description = "Maximum number of entries in the global ID "
            + "coordinates cache described by apicurio.storage.global-id-cache.enabled", availableSince = "3.4.0")
    long maxSize;

    @Inject
    MeterRegistry meterRegistry;

    private Cache<Long, GlobalIdCoordinates> cache;

    @PostConstruct
    void init() {
        if (!enabled) {
            return;
        }
        cache = Caffeine.newBuilder().maximumSize(maxSize).recordStats().build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, CACHE_NAME);
    }

    /** @return the coordinates the global ID names, or empty if it does not currently resolve */
    public Optional<GlobalIdCoordinates> resolve(RegistryStorage storage, long globalId) {
        if (!enabled) {
            return lookup(storage, globalId);
        }
        GlobalIdCoordinates cached = cache.getIfPresent(globalId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<GlobalIdCoordinates> found = lookup(storage, globalId);
        // Never cache "not found": unlike a resolved ID, there is no guarantee it stays not found.
        found.ifPresent(coordinates -> cache.put(globalId, coordinates));
        return found;
    }

    private static Optional<GlobalIdCoordinates> lookup(RegistryStorage storage, long globalId) {
        try {
            ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(globalId);
            return Optional.of(new GlobalIdCoordinates(vmd.getGroupId(), vmd.getArtifactId(), vmd.getVersion()));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }
}
