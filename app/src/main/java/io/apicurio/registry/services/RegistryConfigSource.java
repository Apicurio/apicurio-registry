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

package io.apicurio.registry.services;

import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ConfigSource that turns env vars into plain properties.
 * <p>
 *
 * @author Ales Justin
 */
public class RegistryConfigSource implements ConfigSource {
    private Map<String, String> properties;

    @Override
    public synchronized Map<String, String> getProperties() {
        if (properties == null) {
            properties = new HashMap<>();
            String prefix = System.getenv("REGISTRY_PROPERTIES_PREFIX");
            if (prefix != null) {
                String profile = ProfileManager.getActiveProfile();
                String profilePrefix = "%" + profile + ".";
                Map<String, String> envMap = System.getenv();
                for (Map.Entry<String, String> entry : envMap.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith(prefix)) {
                        String newKey = profilePrefix + key.replace("_", ".").toLowerCase();
                        properties.put(newKey, entry.getValue());
                    }
                }
            }
        }
        return properties;
    }

    @Override
    public Set<String> getPropertyNames() {
        return new HashSet<>(properties.values());
    }

    @Override
    public String getValue(String key) {
        return getProperties().get(key);
    }

    @Override
    public String getName() {
        return "Registry properties";
    }
}
