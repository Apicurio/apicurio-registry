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

package io.apicurio.registry.storage.impl;

import java.util.Map;
import java.util.Set;

/**
 * Simple multimap interface.
 *
 * @author Ales Justin
 */
public interface MultiMap<K, MK, MV> {
    Set<MK> keys(K key);

    /**
     * @return null if there is no previous value, anything otherwise
     */
    MV putIfPresent(K key, MK mk, MV mv);

    /**
     * @return previous value
     */
    MV putIfAbsent(K key, MK mk, MV mv);

    MV get(K key, MK mk);

    /**
     * @return removed value
     */
    MV remove(K key, MK mk);

    void remove(K key);

    // Kafka snapshot only

    void putAll(Map<K, Map<MK, MV>> map);

    Map<K, Map<MK, MV>> asMap();
}
