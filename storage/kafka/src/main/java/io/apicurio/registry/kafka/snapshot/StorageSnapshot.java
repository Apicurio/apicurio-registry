/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.kafka.snapshot;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ales Justin
 */
public class StorageSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Map<Long, Map<String, String>>> storage;
    private Map<Long, Map<String, String>> global;
    private Map<String, Map<String, String>> artifactRules;
    private Map<String, String> globalRules;

    private long offset;

    public StorageSnapshot(
        Map<String, Map<Long, Map<String, String>>> storage,
        Map<Long, Map<String, String>> global,
        Map<String, Map<String, String>> artifactRules,
        Map<String, String> globalRules, long offset
    ) {
        // deep copy
        this.storage = new HashMap<>(storage);
        this.global = new HashMap<>(global);
        this.artifactRules = new HashMap<>(artifactRules);
        this.globalRules = new HashMap<>(globalRules);
        this.offset = offset;
    }

    public Map<String, Map<Long, Map<String, String>>> getStorage() {
        return storage;
    }

    public Map<Long, Map<String, String>> getGlobal() {
        return global;
    }

    public Map<String, Map<String, String>> getArtifactRules() {
        return artifactRules;
    }

    public Map<String, String> getGlobalRules() {
        return globalRules;
    }

    public long getOffset() {
        return offset;
    }
}
