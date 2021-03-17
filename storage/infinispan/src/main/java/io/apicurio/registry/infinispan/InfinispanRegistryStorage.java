/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.infinispan;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;
import io.apicurio.registry.storage.impl.ArtifactKey;
import io.apicurio.registry.storage.impl.MultiMap;
import io.apicurio.registry.storage.impl.StorageMap;
import io.apicurio.registry.storage.impl.StoredContent;
import io.apicurio.registry.storage.impl.TupleId;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.health.ClusterHealth;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * @author Ales Justin
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT + "_InfinispanRegistryStorage", description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT}, reusable = true)
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT + "_InfinispanRegistryStorage", description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT}, reusable = true)
@Timed(name = STORAGE_OPERATION_TIME + "_InfinispanRegistryStorage", description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS, reusable = true)
@Logged
public class InfinispanRegistryStorage extends AbstractMapRegistryStorage implements StorageHandle {

    static String KEY = "_ck";
    static String COUNTER_CACHE = "counter-cache";
    static String CONTENT_CACHE = "content-cache";
    static String CONTENT_HASH_CACHE = "content-hash-cache";
    static String STORAGE_CACHE = "storage-cache";
    static String ARTIFACT_RULES_CACHE = "artifact-rules-cache";
    static String GLOBAL_CACHE = "global-cache";
    static String GLOBAL_RULES_CACHE = "global-rules-cache";
    static String LOG_CONFIGURATION_CACHE = "log-configuration-cache";
    static String GROUPS_CACHE = "groups-cache";

    @Inject
    EmbeddedCacheManager manager;

    private Map<String, Long> counter;

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return "infinispan";
    }

    @Override
    protected void afterInit() {
        manager.defineConfiguration(
                COUNTER_CACHE,
                new ConfigurationBuilder()
                        .clustering().cacheMode(CacheMode.REPL_SYNC)
                        .build()
        );

        counter = manager.getCache(COUNTER_CACHE, true);
    }

    @Override
    protected long nextGlobalId() {
        return counter.compute(KEY, new Add());
    }

    @Override
    public long nextContentId() {
        return nextGlobalId();
    }

    @Override
    protected Map<Long, String> createContentHashMap() {
        manager.defineConfiguration(
            CONTENT_HASH_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        return manager.getCache(CONTENT_HASH_CACHE, true);
    }

    @Override
    protected Map<String, StoredContent> createContentMap() {
        manager.defineConfiguration(
            CONTENT_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        return manager.getCache(CONTENT_CACHE, true);
    }

    @Override
    protected StorageMap createStorageMap() {
        manager.defineConfiguration(
            STORAGE_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        Cache<ArtifactKey, Map<Long, Map<String, String>>> cache = manager.getCache(STORAGE_CACHE, true);
        return CacheStorageMap.create(cache);
    }

    @Override
    protected Map<Long, TupleId> createGlobalMap() {
        manager.defineConfiguration(
                GLOBAL_CACHE,
                new ConfigurationBuilder()
                        .clustering().cacheMode(CacheMode.REPL_SYNC)
                        .build()
        );

        return manager.getCache(GLOBAL_CACHE, true);
    }

    @Override
    protected Map<String, String> createLogConfigurationMap() {
        manager.defineConfiguration(
                LOG_CONFIGURATION_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        return manager.getCache(LOG_CONFIGURATION_CACHE, true);
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createGlobalRulesMap()
     */
    @Override
    protected Map<String, String> createGlobalRulesMap() {
        manager.defineConfiguration(
            GLOBAL_RULES_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        return manager.getCache(GLOBAL_RULES_CACHE, true);
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createArtifactRulesMap()
     */
    @Override
    protected MultiMap<ArtifactKey, String, String> createArtifactRulesMap() {
        manager.defineConfiguration(
                ARTIFACT_RULES_CACHE,
                new ConfigurationBuilder()
                        .clustering().cacheMode(CacheMode.REPL_SYNC)
                        .build()
        );

        Cache<ArtifactKey, MapValue<String, String>> cache = manager.getCache(ARTIFACT_RULES_CACHE, true);
        return new CacheMultiMap<>(cache);
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createGroupsMap()
     */
    @Override
    protected Map<String, GroupMetaDataDto> createGroupsMap() {
        manager.defineConfiguration(
            GROUPS_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        return manager.getCache(GROUPS_CACHE, true);
    }

    @Override
    protected Function<String, StoredContent> contentFn(String contentHash, ArtifactType artifactType, byte[] bytes) {
        return new ContentFn(this, contentHash, artifactType, bytes);
    }

    @Override // make it serializable
    protected BiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> lookupFn() {
        return new MapFn();
    }

    @Override
    public boolean isAlive() {
        ClusterHealth health = manager.getHealth().getClusterHealth();
        return (health.getHealthStatus() != HealthStatus.DEGRADED);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        ConcurrentUtil.get(manager.getCache(GLOBAL_RULES_CACHE, true).clearAsync());
    }

}
