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

import io.apicurio.registry.storage.impl.MultiMap;
import org.infinispan.Cache;
import org.infinispan.util.function.SerializableBiFunction;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Ales Justin
 */
class CacheMultiMap<K extends Serializable, MK extends Serializable, MV extends Serializable> implements MultiMap<K, MK, MV> {
    private final Cache<K, MapValue<MK, MV>> cache;

    public CacheMultiMap(Cache<K, MapValue<MK, MV>> cache) {
        this.cache = cache;
    }

    private Map<MK, MV> get(K key) {
        MapValue<MK, MV> map = cache.get(key);
        return map != null ? map.getMap() : Collections.emptyMap();
    }

    @Override
    public Set<MK> keys(K key) {
        return get(key).keySet();
    }

    @Override
    public MV get(K key, MK mk) {
        return get(key).get(mk);
    }

    @Override
    public MV putIfPresent(K key, MK mk, MV mv) {
        MapValue<MK, MV> cMap = cache.compute(key, (SerializableBiFunction<K, MapValue<MK, MV>, MapValue<MK, MV>>) (k, value) -> {
            if (value == null) {
                return null;
            }
            Map<MK, MV> map = value.getMap();
            MV pmv = map.get(mk);
            if (pmv != null) {
                map.put(mk, mv);
            }
            value.setValue(pmv);
            return value;
        });
        return cMap != null ? cMap.getValue() : null;
    }

    @Override
    public MV putIfAbsent(K key, MK mk, MV mv) {
        MapValue<MK, MV> cMap = cache.compute(key, (SerializableBiFunction<K, MapValue<MK, MV>, MapValue<MK, MV>>) (k, value) -> {
            if (value == null) {
                value = new MapValue<>(new HashMap<>(), null);
            }
            Map<MK, MV> map = value.getMap();
            value.setValue(map.putIfAbsent(mk, mv));
            return value;
        });
        return cMap != null ? cMap.getValue() : null;
    }

    @Override
    public MV remove(K key, MK mk) {
        MapValue<MK, MV> cMap = cache.compute(key, (SerializableBiFunction<K, MapValue<MK, MV>, MapValue<MK, MV>>) (k, value) -> {
            if (value == null) {
                return null;
            }
            Map<MK, MV> map = value.getMap();
            value.setValue(map.remove(mk));
            return value;
        });
        return cMap != null ? cMap.getValue() : null;
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public void putAll(Map<K, Map<MK, MV>> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<K, Map<MK, MV>> asMap() {
        throw new UnsupportedOperationException();
    }
}
