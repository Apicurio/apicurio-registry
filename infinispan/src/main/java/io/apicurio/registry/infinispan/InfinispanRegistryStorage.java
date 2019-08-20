package io.apicurio.registry.infinispan;

import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.function.SerializableBiFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class InfinispanRegistryStorage extends AbstractMapRegistryStorage {

    private static String KEY = "_ck";
    private static String COUNTER_CACHE = "counter-cache";
    private static String STORAGE_CACHE = "storage-cache";
    private static String GLOBAL_CACHE = "global-cache";
    private static String GLOBAL_RULES_CACHE = "global-rules-cache";

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
    protected Map<String, Map<Long, Map<String, String>>> createStorageMap() {
        manager.defineConfiguration(
            STORAGE_CACHE,
            new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.REPL_SYNC)
                .build()
        );

        return manager.getCache(STORAGE_CACHE, true);
    }

    @Override
    protected Map<Long, Map<String, String>> createGlobalMap() {
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

    @Override // make it serializable
    protected BiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> lookupFn() {
        //noinspection unchecked
        return (SerializableBiFunction) ((id, m) -> (m == null) ? new ConcurrentHashMap<>() : m);
    }
}
