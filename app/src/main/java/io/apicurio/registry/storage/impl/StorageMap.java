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
 * Storage map
 *
 * @author Ales Justin
 */
public interface StorageMap {
    Map<String, Map<Long, Map<String, String>>> asMap();

    void putAll(Map<String, Map<Long, Map<String, String>>> map);

    //

    Set<String> keySet();

    Map<Long, Map<String, String>> get(String artifactId);

    Map<Long, Map<String, String>> compute(String artifactId);

    void createVersion(String artifactId, long version, Map<String, String> contents);

    void put(String artifactId, String key, String value);

    void put(String artifactId, long version, String key, String value);

    Long remove(String artifactId, long version);

    void remove(String artifactId, long version, String key);

    Map<Long, Map<String, String>> remove(String artifactId);
}
