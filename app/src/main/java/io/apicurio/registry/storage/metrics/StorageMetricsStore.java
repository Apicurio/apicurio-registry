/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.metrics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.multitenancy.TenantContext;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides a set of per-tenant counters. Counters such as "number of artifacts"
 * This counters have to be "distributed" or at least work in a clustered deployment.
 * Currently this implementation uses {@link Cache} for storing the counters,
 * it's "auto-eviction" nature allows to re-initialize the counters with information from the database periodically,
 * making it "useful" for clustered deployments.
 *
 * This implementation is far from perfect, ideally redis or some other externalized cache should be used, but for now
 * this implementation could work, it's extremely simple and it does not require the deployment of external infrastructure.
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class StorageMetricsStore {

    @Inject
    Logger log;

    @Inject
    @ConfigProperty(defaultValue = "30000", name = "registry.storage.metrics.cache.check-period")
    @Info(category = "health", description = "Storage metrics cache check period", availableSince = "2.1.0.Final")
    Long limitsCheckPeriod;

    @Inject
    @ConfigProperty(defaultValue = "1000", name = "registry.storage.metrics.cache.max-size")
    @Info(category = "limits", description = "Storage metrics cache max size.", availableSince = "2.4.1.Final")
    Long cacheMaxSize;

    @Inject
    TenantContext tenantContext;

    @Inject
    @Current
    RegistryStorage storage;

    //NOTE all of this could be changed in the future with a global cache shared between all registry replicas
    private LoadingCache<String, AtomicLong> totalSchemasCounters;
    private LoadingCache<String, AtomicLong> artifactsCounters;
    private LoadingCache<ArtifactVersionKey, AtomicLong> artifactVersionsCounters;

    CacheLoader<String, AtomicLong> totalSchemaCountersLoader;
    CacheLoader<String, AtomicLong> artifactsCountersLoader;
    CacheLoader<ArtifactVersionKey, AtomicLong> artifactVersionsCountersLoader;

    @EqualsAndHashCode
    private static class ArtifactVersionKey {
        String tenantId;
        String groupId;
        String artifactId;
    }

    public void onStart(@Observes StartupEvent ev) {
        createTotalSchemasCache();
        createTotalArtifactsCache();
        createTotalArtifactVersionsCache();
    }

    private void createTotalSchemasCache() {
        totalSchemaCountersLoader = new CacheLoader<>() {
            @Override
            public AtomicLong load(@NotNull String tenantId) {
                log.info("Initializing total schemas counter, tid {}", tenantContext.tenantId());
                long count = storage.countTotalArtifactVersions();
                return new AtomicLong(count); }
        };

        totalSchemasCounters = CacheBuilder
                .newBuilder()
                .expireAfterWrite(limitsCheckPeriod, TimeUnit.MILLISECONDS)
                .maximumSize(cacheMaxSize)
                .build(totalSchemaCountersLoader);
    }

    private void createTotalArtifactsCache() {
        artifactsCountersLoader = new CacheLoader<>() {
            @Override
            public AtomicLong load(@NotNull String tenantId) {
                log.info("Initializing total artifacts counter, tid {}", tenantContext.tenantId());
                long count = storage.countArtifacts();
                return new AtomicLong(count); }
        };

        artifactsCounters = CacheBuilder
                .newBuilder()
                .expireAfterWrite(limitsCheckPeriod, TimeUnit.MILLISECONDS)
                .maximumSize(cacheMaxSize)
                .build(artifactsCountersLoader);
    }

    private void createTotalArtifactVersionsCache() {
        artifactVersionsCountersLoader = new CacheLoader<>() {
            @Override
            public AtomicLong load(@NotNull ArtifactVersionKey artifactVersionKey) {
                log.info("Initializing total artifact versions counter for artifact gid {} ai {}, tid {}", artifactVersionKey.groupId, artifactVersionKey.artifactId, tenantContext.tenantId());
                long count = storage.countArtifactVersions(artifactVersionKey.groupId, artifactVersionKey.artifactId);
                return new AtomicLong(count); }
        };

        artifactVersionsCounters = CacheBuilder
                .newBuilder()
                .expireAfterWrite(limitsCheckPeriod, TimeUnit.MILLISECONDS)
                .maximumSize(cacheMaxSize)
                .build(artifactVersionsCountersLoader);
    }

    public long getOrInitializeTotalSchemasCounter() {
        return totalSchemasCounters.getUnchecked(tenantContext.tenantId()).get();
    }

    public long getOrInitializeArtifactsCounter() {
        return artifactsCounters.getUnchecked(tenantContext.tenantId()).get();
    }

    public long getOrInitializeArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.tenantId = tenantContext.tenantId();
        avk.groupId = groupId;
        avk.artifactId = artifactId;

        return artifactVersionsCounters.getUnchecked(avk).get();
    }

    public void incrementTotalSchemasCounter() {
        log.info("Incrementing total schemas counter, tid {}", tenantContext.tenantId());
        AtomicLong counter = totalSchemasCounters.getUnchecked(tenantContext.tenantId());
        if (counter == null) {
            //cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void incrementArtifactsCounter() {
        AtomicLong counter = artifactsCounters.getUnchecked(tenantContext.tenantId());
        if (counter == null) {
            //cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void incrementArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.tenantId = tenantContext.tenantId();
        avk.groupId = groupId;
        avk.artifactId = artifactId;
        AtomicLong counter = artifactVersionsCounters.getUnchecked(avk);
        if (counter == null) {
            //cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void resetTotalSchemasCounter() {
        totalSchemasCounters.invalidate(tenantContext.tenantId());
    }

    public void resetArtifactsCounter() {
        artifactsCounters.invalidate(tenantContext.tenantId());
    }

    public void resetArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.tenantId = tenantContext.tenantId();
        avk.groupId = groupId;
        avk.artifactId = artifactId;
        artifactVersionsCounters.invalidate(avk);
    }

}
