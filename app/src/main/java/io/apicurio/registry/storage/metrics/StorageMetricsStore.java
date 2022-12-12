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

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.CheckPeriodCache;
import io.quarkus.runtime.StartupEvent;
import lombok.EqualsAndHashCode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

/**
 * This class provides a set of per-tenant counters. Counters such as "number of artifacts"
 * This counters have to be "distributed" or at least work in a clustered deployment.
 * Currently this implementation uses {@link CheckPeriodCache} for storing the counters,
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
    TenantContext tenantContext;

    @Inject
    @Current
    RegistryStorage storage;

    //NOTE all of this could be changed in the future with a global cache shared between all registry replicas
    private CheckPeriodCache<String, AtomicLong> totalSchemasCounters;
    private CheckPeriodCache<String, AtomicLong> artifactsCounters;
    private CheckPeriodCache<ArtifactVersionKey, AtomicLong> artifactVersionsCounters;

    @EqualsAndHashCode
    private static class ArtifactVersionKey {
        String tenantId;
        String groupId;
        String artifactId;
    }

    public void onStart(@Observes StartupEvent ev) {
        totalSchemasCounters = new CheckPeriodCache<>(Duration.ofMillis(limitsCheckPeriod));
        artifactsCounters = new CheckPeriodCache<>(Duration.ofMillis(limitsCheckPeriod));
        artifactVersionsCounters = new CheckPeriodCache<>(Duration.ofMillis(limitsCheckPeriod));
    }

    public long getOrInitializeTotalSchemasCounter() {
        return totalSchemasCounters.compute(tenantContext.tenantId(), k -> {
            log.info("Initializing total schemas counter, tid {}", tenantContext.tenantId());
            long count = storage.countTotalArtifactVersions();
            return new AtomicLong(count);
        }).get();
    }

    public long getOrInitializeArtifactsCounter() {
        return artifactsCounters.compute(tenantContext.tenantId(), k -> {
            long count = storage.countArtifacts();
            return new AtomicLong(count);
        }).get();
    }

    public long getOrInitializeArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.tenantId = tenantContext.tenantId();
        avk.groupId = groupId;
        avk.artifactId = artifactId;
        return artifactVersionsCounters.compute(avk, k -> {
            long count = storage.countArtifactVersions(groupId, artifactId);
            return new AtomicLong(count);
        }).get();
    }

    public void incrementTotalSchemasCounter() {
        log.info("Incrementing total schemas counter, tid {}", tenantContext.tenantId());
        AtomicLong counter = totalSchemasCounters.get(tenantContext.tenantId());
        if (counter == null) {
            //cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void incrementArtifactsCounter() {
        AtomicLong counter = artifactsCounters.get(tenantContext.tenantId());
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
        AtomicLong counter = artifactVersionsCounters.get(avk);
        if (counter == null) {
            //cached counter expired, do nothing, it will be reloaded from DB on the next read
            return;
        } else {
            counter.incrementAndGet();
        }
    }

    public void resetTotalSchemasCounter() {
        totalSchemasCounters.remove(tenantContext.tenantId());
    }

    public void resetArtifactsCounter() {
        artifactsCounters.remove(tenantContext.tenantId());
    }

    public void resetArtifactVersionsCounter(String groupId, String artifactId) {
        ArtifactVersionKey avk = new ArtifactVersionKey();
        avk.tenantId = tenantContext.tenantId();
        avk.groupId = groupId;
        avk.artifactId = artifactId;
        artifactVersionsCounters.remove(avk);
    }

}
