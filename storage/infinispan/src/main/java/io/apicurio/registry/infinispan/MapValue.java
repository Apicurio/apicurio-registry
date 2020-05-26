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

import java.io.Serializable;
import java.util.Map;

/**
 * Map with previous value.
 *
 * @author Ales Justin
 */
public class MapValue<K extends Serializable, V extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<K, V> map;
    private V value;

    public MapValue(Map<K, V> map, V value) {
        this.map = map;
        this.value = value;
    }

    public Map<K, V> getMap() {
        return map;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V pmv) {
        this.value = pmv;
    }
}
