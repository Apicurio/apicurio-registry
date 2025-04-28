package io.apicurio.registry.storage.metrics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides a set of counters. Counters such as "number of artifacts" This counters have to be
 * "distributed" or at least work in a clustered deployment. Currently, this implementation uses {@link Cache}
 * for storing the counters, it's "auto-eviction" nature allows to re-initialize the counters with information
 * from the database periodically, making it "useful" for clustered deployments.
 * <p>
 * This implementation is far from perfect, ideally redis or some other externalized cache should be used, but
 * for now this implementation could work, it's extremely simple and it does not require the deployment of
 * external infrastructure.
 */
@ApplicationScoped
public class StorageMetricsStore {

    @Inject
    Logger log;

    @Inject
    @ConfigProperty(defaultValue = "30000", name = "apicurio.storage.metrics.cache.check-period.ms")
    @Info(category = "health", description = "Storage metrics cache check period", availableSince = "2.1.0.Final")
    Long limitsCheckPeriod;

    @Inject
    @ConfigProperty(defaultValue = "1000", name = "apicurio.storage.metrics.cache.max-size")
    @Info(category = "limits", description = "Storage metrics cache max size.", availableSince = "2.4.1.Final")
    Long cacheMaxSize;

    private static final String TOTAL_SCHEMAS_KEY = "TOTAL_SCHEMAS";
    private static final String ARTIFACT_COUNTER = "ARTIFACTS_COUNTER";

    @Inject
    @Current
    RegistryStorage storage;

    // NOTE all of this could be changed in the future with a global cache shared between all registry
    // replicas
    private LoadingCache<String, AtomicLong> countersCache;
    private LoadingCache<ArtifactVersionKey, AtomicLong> artifactVersionsCounters;

    CacheLoader<String, AtomicLong> totalSchemaCountersLoader;
    CacheLoader<ArtifactVersionKey, AtomicLong> artifactVersionsCountersLoader;

    @EqualsAndHashCode
    private static class ArtifactVersionKey {
        String groupId;
        String artifactId;
    }

    public void onStart(@Observes StartupEvent ev) {
        createCountersCache();
        createTotalArtifactVersionsCache();
    }

    private void createCountersCache() {
        totalSchemaCountersLoader = new CacheLoader<>() {
            @Override
            public AtomicLong load(String key) {
                if (key.equals(TOTAL_SCHEMAS_KEY)) {
                    log.info("Initializing total schemas counter");
                    long count = storage.countTotalArtifactVersions();
                    return new AtomicLong(count);
                } else {
                    log.info("Initializing total schemas counter");
                    long count = storage.countArtifacts();
                    return new AtomicLong(count);
                }
            }
        };

        countersCache = CacheBuilder.newBuilder().expireAfterWrite(limitsCheckPeriod, TimeUnit.MILLISECONDS)
                .maximumSize(cacheMaxSize).build(totalSchemaCountersLoader);
    }

    private void createTotalArtifactVersionsCache() {
        artifactVersionsCountersLoader = new CacheLoader<>() {
            @Override
            public AtomicLong load(ArtifactVersionKey artifactVersionKey) {
                log.info("Initializing total artifact versions counter for artifact gid {} ai {}",
                        artifactVersionKey.groupId, artifactVersionKey.artifactId);
                long count = storage.countArtifactVersions(artifactVersionKey.groupId,
                        artifactVersionKey.artifactId);
                return new AtomicLong(count);
            }
        };

        artifactVersionsCounters = CacheBuilder.newBuilder()
                .expireAfterWrite(limitsCheckPeriod, TimeUnit.MILLISECONDS).maximumSize(cacheMaxSize)
                .build(artifactVersionsCountersLoader);
    }

    public long getOrInitializeTotalSchemasCounter() {
        return countersCache.getUnchecked(TOTAL_SCHEMAS_KEY).get();
    }

    public long getOrInitializeArtifactsCounter() {
        return countersCache.getUnchecked(ARTIFACT_COUNTER).get();
    }

    public long getOrInitializeArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.groupId = groupId;
        avk.artifactId = artifactId;

        return artifactVersionsCounters.getUnchecked(avk).get();
    }

    public void incrementTotalSchemasCounter() {
        log.info("Incrementing total schemas counter");
        AtomicLong counter = countersCache.getUnchecked(TOTAL_SCHEMAS_KEY);
        if (counter == null) {
            // cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void incrementArtifactsCounter() {
        AtomicLong counter = countersCache.getUnchecked(ARTIFACT_COUNTER);
        if (counter == null) {
            // cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void incrementArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.groupId = groupId;
        avk.artifactId = artifactId;
        AtomicLong counter = artifactVersionsCounters.getUnchecked(avk);
        if (counter == null) {
            // cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void resetTotalSchemasCounter() {
        countersCache.invalidate(TOTAL_SCHEMAS_KEY);
    }

    public void resetArtifactsCounter() {
        countersCache.invalidate(ARTIFACT_COUNTER);
    }

    public void resetArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.groupId = groupId;
        avk.artifactId = artifactId;
        artifactVersionsCounters.invalidate(avk);
    }
}
