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

package io.apicurio.registry.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Fabian Martinez
 * @author Jakub Senko <m@jsenko.net>
 */
public class CheckPeriodCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(CheckPeriodCache.class);

    private final ConcurrentHashMap<K, CheckValue<V>> cache = new ConcurrentHashMap<>();

    private final Duration checkPeriod;

    private final AtomicBoolean evicting = new AtomicBoolean(false);
    private final int evictionThreshold;

    public CheckPeriodCache(Duration checkPeriod, int evictionThreshold) {
        this.checkPeriod = checkPeriod;
        this.evictionThreshold = evictionThreshold;
    }

    public CheckPeriodCache(Duration checkPeriod) {
        this.checkPeriod = checkPeriod;
        this.evictionThreshold = 1000;
    }

    private boolean isExpired(CheckValue<?> checkedValue) {
        return Instant.now().isAfter(checkedValue.lastUpdate.plus(checkPeriod));
    }

    private void checkEviction() {
        final int currentSize = cache.size();
        if (currentSize > evictionThreshold) {
            if (evicting.compareAndSet(false, true)) {
                try {
                    // This thread gets to evict
                    log.debug("Thread {} is evicting the cache. Current size is {}, threshold is {}.",
                            Thread.currentThread().getName(), currentSize, evictionThreshold);
                    var toEvict = cache.entrySet().stream()
                            .filter(entry -> isExpired(entry.getValue()))
                            .map(Entry::getKey)
                            .collect(Collectors.toList());

                    log.debug("Thread {} is evicting the cache. Found {} candidates for eviction.",
                            Thread.currentThread().getName(), toEvict.size());
                    toEvict.forEach(k -> {
                        cache.compute(k, (key, value) -> {
                            if (value == null || isExpired(value)) {
                                return null;
                            } else {
                                return value;
                            }
                        });
                    });

                    log.debug("Thread {} has finished evicting the cache. The new size is {}.",
                            Thread.currentThread().getName(), cache.size());
                } finally {
                    evicting.set(false);
                }
            }
        }
    }

    public V compute(K k, Function<K, V> remappingFunction) {
        checkEviction();
        CheckValue<V> returnValue = cache.compute(k, (key, checkedValue) -> {
            if (checkedValue == null || isExpired(checkedValue)) {
                // Only execute if expired, but do it first in case an exception is thrown
                V value = remappingFunction.apply(key);
                return new CheckValue<>(Instant.now(), value);
            } else {
                return checkedValue;
            }
        });
        return returnValue.value;
    }

    public void put(K k, V v) {
        checkEviction();
        cache.put(k, new CheckValue<>(Instant.now(), v));
    }

    public V get(K k) {
        CheckValue<V> value = cache.compute(k, (key, checkedValue) -> {
            if (checkedValue == null || isExpired(checkedValue)) {
                return null;
            } else {
                return checkedValue;
            }
        });
        return value == null ? null : value.value;
    }

    public void remove(K k) {
        cache.remove(k);
    }

    public void clear() {
        cache.clear();
    }

    /**
     * USE FOR TEST ONLY
     */
    Map<K, CheckValue<V>> getInternal() {
        return cache;
    }

    int size() {
        return cache.size();
    }

    static class CheckValue<V> {

        CheckValue(Instant lastUpdate, V value) {
            this.lastUpdate = lastUpdate;
            this.value = value;
        }

        Instant lastUpdate;
        V value;
    }
}
