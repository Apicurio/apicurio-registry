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

package io.apicurio.registry.infinispan;

import org.infinispan.util.function.SerializableBiFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Add function.
 *
 * @author Ales Justin
 */
public class MapFn implements SerializableBiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> {

    private static final long serialVersionUID = 1L;

    @Override
    public Map<Long, Map<String, String>> apply(String k, Map<Long, Map<String, String>> m) {
        return (m == null) ? new ConcurrentHashMap<>() : m;
    }
}
