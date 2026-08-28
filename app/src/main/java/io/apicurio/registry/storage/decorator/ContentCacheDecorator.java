package io.apicurio.registry.storage.decorator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

/**
 * Caches immutable content reads (content bytes, artifact type, and parsed references) keyed by the
 * immutable content identifiers that {@link io.apicurio.registry.storage.RegistryStorage} exposes for them:
 * the numeric content ID and the SHA-256 content hash.
 * <p>
 * Only methods whose result depends solely on an immutable content identifier are cached here. Methods that
 * resolve content via a mutable coordinate (e.g. {@code getContentByReference}, which resolves a
 * group/artifact/version GAV to content) are intentionally NOT cached by this decorator, since the GAV to
 * content mapping can change over time (e.g. a version's content can be replaced, or the reference target
 * re-pointed) - caching that mapping without a dedicated, invalidation-aware layer could serve stale data.
 * <p>
 * The cache is a Caffeine cache, weighed by the size (in bytes) of the cached content, bounded by
 * {@link #maxWeightBytes}. Concurrent cache misses for the same key are automatically coalesced into a
 * single call to the delegate storage (a guarantee provided by {@link Cache#get(Object, java.util.function.Function)}).
 * Failed loads (e.g. {@link ContentNotFoundException}) are never cached.
 * <p>
 * Cache hit/miss/load/eviction/size/weight metrics are exposed via Micrometer using the standard Caffeine
 * cache metrics binder ({@code cache.gets}, {@code cache.puts}, {@code cache.evictions}, {@code cache.size},
 * {@code cache.eviction.weight}, tagged with {@code cache=contentById} or {@code cache=contentByHash}).
 */
@ApplicationScoped
public class ContentCacheDecorator extends RegistryStorageDecoratorBase {

    private static final String CACHE_NAME_BY_ID = "contentById";
    private static final String CACHE_NAME_BY_HASH = "contentByHash";

    @ConfigProperty(name = "apicurio.storage.content-cache.enabled", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "Enable an in-memory, byte-weighted cache of immutable "
            + "artifact content (keyed by content ID and content hash) in front of the storage layer", availableSince = "3.3.3")
    boolean enabled;

    @ConfigProperty(name = "apicurio.storage.content-cache.max-size", defaultValue = "67108864")
    @Info(category = CATEGORY_STORAGE, description = "Maximum total weight (approximate size in bytes) of "
            + "the content cache described by apicurio.storage.content-cache.enabled", availableSince = "3.3.3")
    long maxWeightBytes;

    @Inject
    MeterRegistry meterRegistry;

    private Cache<Long, ContentWrapperDto> byIdCache;
    private Cache<String, ContentWrapperDto> byHashCache;

    @PostConstruct
    void init() {
        if (!enabled) {
            return;
        }
        byIdCache = Caffeine.newBuilder().maximumWeight(maxWeightBytes)
                .<Long, ContentWrapperDto> weigher((key, value) -> weigh(value)).recordStats().build();
        byHashCache = Caffeine.newBuilder().maximumWeight(maxWeightBytes)
                .<String, ContentWrapperDto> weigher((key, value) -> weigh(value)).recordStats().build();
        if (meterRegistry != null) {
            CaffeineCacheMetrics.monitor(meterRegistry, byIdCache, CACHE_NAME_BY_ID);
            CaffeineCacheMetrics.monitor(meterRegistry, byHashCache, CACHE_NAME_BY_HASH);
        }
    }

    private static int weigh(ContentWrapperDto value) {
        int size = 64; // rough per-entry overhead
        if (value.getContent() != null) {
            size += value.getContent().bytes().length;
        }
        if (value.getReferences() != null) {
            size += value.getReferences().size() * 128;
        }
        // Clamp to Integer.MAX_VALUE - Caffeine weights are ints.
        return size < 0 ? Integer.MAX_VALUE : size;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.CONTENT_CACHE_DECORATOR;
    }

    public ContentWrapperDto getContentById(long contentId) {
        if (!enabled) {
            return delegate.getContentById(contentId);
        }
        return byIdCache.get(contentId, id -> delegate.getContentById(id));
    }

    public ContentWrapperDto getContentByHash(String contentHash) {
        if (!enabled) {
            return delegate.getContentByHash(contentHash);
        }
        return byHashCache.get(contentHash, hash -> delegate.getContentByHash(hash));
    }
}
