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
import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;
import io.apicurio.registry.storage.impl.MultiMap;
import io.apicurio.registry.storage.impl.StorageMap;
import io.apicurio.registry.storage.impl.TupleId;
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
import org.infinispan.util.function.SerializableBiFunction;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static io.apicurio.registry.metrics.MetricIDs.*;
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
public class InfinispanRegistryStorage extends AbstractMapRegistryStorage {

    static String KEY = "_ck";
    static String COUNTER_CACHE = "counter-cache";
    static String STORAGE_CACHE = "storage-cache";
    static String ARTIFACT_RULES_CACHE = "artifact-rules-cache";
    static String GLOBAL_CACHE = "global-cache";
    static String GLOBAL_RULES_CACHE = "global-rules-cache";

    @Inject
    EmbeddedCacheManager manager;

    private Map<String, Long> counter;

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
        return counter.compute(KEY, (SerializableBiFunction<? super String, ? super Long, ? extends Long>) (k, v) -> (v == null ? 1 : v + 1));
    }

    @Override
    protected StorageMap createStorageMap() {
        manager.defineConfiguration(
            STORAGE_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        Cache<String, Map<Long, Map<String, String>>> cache = manager.getCache(STORAGE_CACHE, true);
        return CacheStorageMap.create(cache);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected MultiMap<String, String, String> createArtifactRulesMap() {
        manager.defineConfiguration(
                ARTIFACT_RULES_CACHE,
                new ConfigurationBuilder()
                        .clustering().cacheMode(CacheMode.REPL_SYNC)
                        .build()
        );

        Cache<String, MapValue<String, String>> cache = manager.getCache(ARTIFACT_RULES_CACHE, true);
        return new CacheMultiMap<>(cache);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override // make it serializable
    protected BiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> lookupFn() {
        //noinspection unchecked
        return (SerializableBiFunction) ((id, m) -> (m == null) ? new ConcurrentHashMap<>() : m);
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
