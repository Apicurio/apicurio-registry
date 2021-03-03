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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.commons.CacheException;
import org.infinispan.manager.ClusterExecutor;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.UserRaisedFunctionalException;
import org.infinispan.util.function.SerializableBiFunction;
import org.infinispan.util.function.SerializableFunction;

import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;
import io.apicurio.registry.storage.impl.ArtifactKey;
import io.apicurio.registry.storage.impl.MetaDataKeys;
import io.apicurio.registry.storage.impl.StorageMap;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.utils.ConcurrentUtil;

/**
 * @author Ales Justin
 */
class CacheStorageMap implements StorageMap {
    private static final Set<Class<? extends Throwable>> INFINISPAN_EXCEPTIONS;

    static {
        INFINISPAN_EXCEPTIONS = new HashSet<>();
        INFINISPAN_EXCEPTIONS.add(CacheException.class);
        INFINISPAN_EXCEPTIONS.add(UserRaisedFunctionalException.class);
    }

    private final Cache<ArtifactKey, Map<Long, Map<String, String>>> cache;

    private CacheStorageMap(Cache<ArtifactKey, Map<Long, Map<String, String>>> cache) {
        this.cache = cache;
    }

    public static StorageMap create(Cache<ArtifactKey, Map<Long, Map<String, String>>> cache) {
        StorageMap delegate = new CacheStorageMap(cache);
        return (StorageMap) Proxy.newProxyInstance(
                CacheStorageMap.class.getClassLoader(),
                new Class[]{StorageMap.class},
                (proxy, method, args) -> {
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        Throwable top = cause;
                        while (isInfinispanException(cause)) {
                            cause = cause.getCause();
                        }
                        throw cause != null ? cause : top;
                    }
                }
        );
    }

    private static boolean isInfinispanException(Throwable t) {
        if (t == null) {
            return false;
        }
        return INFINISPAN_EXCEPTIONS.stream().anyMatch(c -> c.isInstance(t));
    }

    @Override
    public Set<ArtifactKey> keySet() {
        return cache.keySet();
    }

    @Override
    public Map<Long, Map<String, String>> get(ArtifactKey artifactKey) {
        return cache.get(artifactKey);
    }

    @Override
    public Map<Long, Map<String, String>> compute(ArtifactKey artifactKey) {
        return cache.compute(artifactKey, (SerializableBiFunction<ArtifactKey, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>>)
                (k, map) -> (map != null ? map : new ConcurrentHashMap<>())
        );
    }

    @Override
    public void createVersion(ArtifactKey artifactKey, long version, Map<String, String> contents) {
        cache.compute(artifactKey, (SerializableBiFunction<ArtifactKey, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>>) (k, map) -> {
            if (map == null) {
                map = new ConcurrentHashMap<>();
            }
            long iv = version;
            Map<String, String> inner = map.putIfAbsent(iv, contents);
            while (inner != null) {
                iv++;
                contents.put(MetaDataKeys.VERSION, Long.toString(iv));
                inner = map.putIfAbsent(iv, contents);
            }
            return map;
        });
    }

    static SortedSet<Long> versions(SortedSet<Long> versions) {
        versions.remove(Long.MIN_VALUE);
        return versions;
    }

    @Override
    public void put(ArtifactKey artifactKey, String key, String value) {
        cache.compute(artifactKey, (SerializableBiFunction<ArtifactKey, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>>) (s, map) -> {
            if (map == null) {
                throw new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId());
            }
            long version = map.entrySet()
                    .stream()
                    .filter(AbstractMapRegistryStorage.statesFilter(ArtifactStateExt.ACTIVE_STATES))
                    .map(Map.Entry::getKey)
                    .max(Long::compareTo)
                    .orElseThrow(() -> new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId()));

            Map<String, String> content = map.get(version);

            // skip this for states, any other metadata needs active artifact
            if (key.equals(MetaDataKeys.STATE) == false) {
                ArtifactState state = ArtifactStateExt.getState(content);
                ArtifactStateExt.validateState(ArtifactStateExt.ACTIVE_STATES, state, artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
            }

            content.put(key, value);
            return map;
        });
    }

    @Override
    public void put(ArtifactKey artifactKey, long version, String key, String value) {
        cache.compute(artifactKey, (SerializableBiFunction<ArtifactKey, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>>) (s, map) -> {
            if (map == null) {
                throw new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId());
            }

            Map<String, String> content = map.get(version);
            if (content == null) {
                throw new VersionNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
            }

            // skip this for states, any other metadata needs active artifact
            if (key.equals(MetaDataKeys.STATE) == false) {
                ArtifactState state = ArtifactStateExt.getState(content);
                ArtifactStateExt.validateState(ArtifactStateExt.ACTIVE_STATES, state, artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
            }

            content.put(key, value);
            return map;
        });
    }

    @Override
    public Long remove(ArtifactKey artifactKey, long version) {
        Set<Long> result = new HashSet<>();
        ClusterExecutor ce = cache.getCacheManager().executor();
        CompletableFuture<Void> cf = ce.allNodeSubmission().submitConsumer(
                (SerializableFunction<EmbeddedCacheManager, Long>) manager -> {
                    Cache<ArtifactKey, Map<Long, Map<String, String>>> c = manager.getCache(InfinispanRegistryStorage.STORAGE_CACHE);
                    Map<Long, Map<String, String>> iMap = c.get(artifactKey);
                    if (iMap != null) {
                        Map<String, String> remove = iMap.remove(version);
                        if (remove == null) {
                            throw new VersionNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
                        }
                        return Long.parseLong(remove.get(MetaDataKeys.GLOBAL_ID));
                    } else {
                        throw new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId());
                    }
                },
                (address, globalId, throwable) -> result.add(globalId)
        );
        ConcurrentUtil.get(cf, 30, TimeUnit.SECONDS); // worst case 30sec ... ?!
        if (result.isEmpty()) {
            throw new VersionNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
        }
        return result.iterator().next();
    }

    @Override
    public void remove(ArtifactKey artifactKey, long version, String key) {
        cache.compute(artifactKey, (SerializableBiFunction<ArtifactKey, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>>) (k, iMap) -> {
            if (iMap != null) {
                Map<String, String> vMap = iMap.get(version);
                if (vMap != null) {
                    vMap.remove(key);
                } else {
                    throw new VersionNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
                }
            } else {
                throw new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId());
            }
            return iMap;
        });
    }

    @Override
    public Map<Long, Map<String, String>> remove(ArtifactKey artifactKey) {
        return cache.remove(artifactKey);
    }
}
