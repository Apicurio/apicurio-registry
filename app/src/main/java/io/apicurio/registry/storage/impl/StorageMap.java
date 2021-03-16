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

    Set<ArtifactKey> keySet();

    Map<String, Map<String, String>> get(ArtifactKey artifactKey);

    Map<String, Map<String, String>> compute(ArtifactKey artifactKey);

    void createVersion(ArtifactKey artifactKey, String version, Map<String, String> contents);

    void put(ArtifactKey artifactKey, String key, String value);

    void put(ArtifactKey artifactKey, String version, String key, String value);

    Long remove(ArtifactKey artifactKey, String version);

    void remove(ArtifactKey artifactKey, String version, String key);

    Map<String, Map<String, String>> remove(ArtifactKey artifactKey);

}
