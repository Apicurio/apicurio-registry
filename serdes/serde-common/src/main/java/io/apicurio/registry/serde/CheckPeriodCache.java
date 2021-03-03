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

package io.apicurio.registry.serde;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author Fabian Martinez
 */
public class CheckPeriodCache<K, V> {

    private Map<K, CheckValue<V>> cache = new ConcurrentHashMap<>();
    private long checkPeriod = 0;

    public CheckPeriodCache(long checkPeriod) {
        this.checkPeriod = checkPeriod;
    }

    public V compute(K k, Function<K, V> remappingFunction) {
        CheckValue<V> returnValue = cache.compute(k, (key, checkedValue) -> {
            long now = System.currentTimeMillis();
            if (checkedValue == null) {
                V value = remappingFunction.apply(key);
                return new CheckValue<>(now, value);
            } else {
                if (checkedValue.lastUpdate + checkPeriod < now) {
                    V value = remappingFunction.apply(key);
                    checkedValue.lastUpdate = now;
                    checkedValue.value = value;
                }
                return checkedValue;
            }
        });
        return returnValue.value;
    }

    public void put(K k, V v) {
        cache.put(k, new CheckValue<>(System.currentTimeMillis(), v));
    }

    public V get(K k) {
        CheckValue<V> value = cache.compute(k, (key, checkedValue) -> {
            if (checkedValue == null) {
                return null;
            } else {
                long now = System.currentTimeMillis();
                if (checkedValue.lastUpdate + checkPeriod < now) {
                    //value expired
                    return null;
                } else {
                    return checkedValue;
                }
            }
        });
        return value == null ? null : value.value;
    }

    public void clear() {
        cache.clear();
    }

    private static class CheckValue<V> {

        CheckValue(long ts, V value) {
            this.lastUpdate = ts;
            this.value = value;
        }

        long lastUpdate;
        V value;
    }

}
