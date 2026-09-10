package io.apicurio.registry.storage.decorator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

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
 * {@link #getContentById(long)} and {@link #getContentByHash(String)} are cached with no expiry (only
 * weight-based eviction), since their result never changes for a given key.
 * <p>
 * {@link #getContentAndArtifactTypeById(long)} - the method used by the ccompat "get schema by ID" hot path
 * (see #9897) - is different: it throws {@link ContentNotFoundException} when the content is orphaned (no
 * artifact version currently references it), and orphan status can change after a successful load (e.g. the
 * last referencing version is later deleted). Caching it therefore trades a small, bounded staleness window
 * for a significant reduction in storage round-trips on the hot path, using the same approach already used
 * by this project's HTTP/reverse-proxy caching feature for "moderate" cacheability content (see
 * {@code app/src/main/java/io/apicurio/registry/rest/cache/README.md}): a short {@code expireAfterWrite} TTL
 * ({@link #hotPathTtlSeconds}, disabled by setting it to {@code 0}), plus best-effort invalidation on the
 * single-version delete path ({@link #deleteArtifactVersion}), which is the most common way content becomes
 * orphaned. Other, rarer paths that can also orphan content (bulk artifact/group deletes, data
 * import/upgrade, snapshot restore, etc.) are not actively invalidated and instead rely on the TTL as a
 * safety net.
 * <p>
 * The caches are Caffeine caches, weighed by the size (in bytes) of the cached content, bounded by
 * {@link #maxWeightBytes}. Concurrent cache misses for the same key are automatically coalesced into a
 * single call to the delegate storage (a guarantee provided by {@link Cache#get(Object, java.util.function.Function)}).
 * Failed loads (e.g. {@link ContentNotFoundException}) are never cached.
 * <p>
 * Cache hit/miss/load/eviction/size/weight metrics are exposed via Micrometer using the standard Caffeine
 * cache metrics binder ({@code cache.gets}, {@code cache.puts}, {@code cache.evictions}, {@code cache.size},
 * {@code cache.eviction.weight}, tagged with {@code cache=contentById}, {@code cache=contentByHash}, or
 * {@code cache=contentAndTypeById}).
 */
@ApplicationScoped
public class ContentCacheDecorator extends RegistryStorageDecoratorBase {

    private static final Logger log = LoggerFactory.getLogger(ContentCacheDecorator.class);

    private static final String CACHE_NAME_BY_ID = "contentById";
    private static final String CACHE_NAME_BY_HASH = "contentByHash";
    private static final String CACHE_NAME_AND_TYPE_BY_ID = "contentAndTypeById";

    @ConfigProperty(name = "apicurio.storage.content-cache.enabled", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "Enable an in-memory, byte-weighted cache of immutable "
            + "artifact content (keyed by content ID and content hash) in front of the storage layer", availableSince = "3.3.3")
    boolean enabled;

    @ConfigProperty(name = "apicurio.storage.content-cache.max-size", defaultValue = "67108864")
    @Info(category = CATEGORY_STORAGE, description = "Maximum total weight (approximate size in bytes) of "
            + "the content cache described by apicurio.storage.content-cache.enabled", availableSince = "3.3.3")
    long maxWeightBytes;

    @ConfigProperty(name = "apicurio.storage.content-cache.hot-path.ttl-seconds", defaultValue = "30")
    @Info(category = CATEGORY_STORAGE, description = "Bounded staleness window, in seconds, for the content "
            + "cache entry used by the ccompat 'get schema by ID' hot path (content + artifact type keyed by "
            + "content ID). This entry can go stale if the content becomes orphaned (or un-orphaned) within "
            + "the TTL window; set to 0 to disable caching this entry entirely", availableSince = "3.3.3")
    long hotPathTtlSeconds;

    @Inject
    MeterRegistry meterRegistry;

    private Cache<Long, ContentWrapperDto> byIdCache;
    private Cache<String, ContentWrapperDto> byHashCache;
    private Cache<Long, ContentWrapperDto> contentAndTypeByIdCache;

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
        if (hotPathTtlSeconds > 0) {
            contentAndTypeByIdCache = Caffeine.newBuilder().maximumWeight(maxWeightBytes)
                    .<Long, ContentWrapperDto> weigher((key, value) -> weigh(value))
                    .expireAfterWrite(hotPathTtlSeconds, TimeUnit.SECONDS).recordStats().build();
            if (meterRegistry != null) {
                CaffeineCacheMetrics.monitor(meterRegistry, contentAndTypeByIdCache, CACHE_NAME_AND_TYPE_BY_ID);
            }
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

    public ContentWrapperDto getContentAndArtifactTypeById(long contentId) {
        if (!enabled || contentAndTypeByIdCache == null) {
            return delegate.getContentAndArtifactTypeById(contentId);
        }
        return contentAndTypeByIdCache.get(contentId, id -> delegate.getContentAndArtifactTypeById(id));
    }

    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        Long contentId = null;
        if (enabled && contentAndTypeByIdCache != null) {
            try {
                ArtifactVersionMetaDataDto metaData = delegate.getArtifactVersionMetaData(groupId, artifactId,
                        version);
                contentId = metaData.getContentId();
            } catch (RegistryStorageException e) {
                // Best-effort only: if we can't resolve the content ID up front, the TTL still bounds
                // staleness, so just skip proactive invalidation.
                log.debug("Could not resolve content ID for {}/{}/{} prior to deletion; "
                        + "content+type cache entry will expire via TTL instead", groupId, artifactId, version, e);
            }
        }
        delegate.deleteArtifactVersion(groupId, artifactId, version);
        if (contentId != null) {
            contentAndTypeByIdCache.invalidate(contentId);
        }
    }
}
