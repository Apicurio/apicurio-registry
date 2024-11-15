/*
 * Copyright 2022 Red Hat
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

package io.apicurio.common.apps.config.impl;

import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyIndex;
import io.apicurio.common.apps.config.DynamicConfigPropertyList;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class DynamicConfigPropertyIndexImpl implements DynamicConfigPropertyIndex {

    private Map<String, DynamicConfigPropertyDef> propertyIndex;

    @Inject
    DynamicConfigPropertyList properties;
    @Inject
    Config config;

    /**
     * Constructor.
     */
    public DynamicConfigPropertyIndexImpl() {
    }

    @PostConstruct
    void onInit() {
        indexProperties(properties.getDynamicConfigProperties());
    }

    private Map<String, DynamicConfigPropertyDef> getPropertyIndex() {
        return this.propertyIndex;
    }

    private void indexProperties(List<DynamicConfigPropertyDef> dynamicConfigProperties) {
        this.propertyIndex = new HashMap<>(dynamicConfigProperties.size());
        for (DynamicConfigPropertyDef def : dynamicConfigProperties) {
            this.propertyIndex.put(def.getName(), def);
        }
    }

    private boolean accept(DynamicConfigPropertyDef def) {
        List<String> requires = def.getRequires() == null ? new ArrayList<>(1)
            : new ArrayList<>(Arrays.asList(def.getRequires()));
        requires.add(def.getName() + ".dynamic.allow=true");
        for (String require : requires) {
            String requiredPropertyName = require;
            String requiredPropertyValue = null;
            if (require.contains("=")) {
                requiredPropertyName = require.substring(0, require.indexOf("=")).trim();
                requiredPropertyValue = require.substring(require.indexOf("=") + 1).trim();
            }
            Optional<String> actualPropertyValue = config.getOptionalValue(requiredPropertyName,
                    String.class);
            if (requiredPropertyValue != null && (actualPropertyValue.isEmpty()
                    || !requiredPropertyValue.equals(actualPropertyValue.get()))) {
                return false;
            }
            if (requiredPropertyValue == null && actualPropertyValue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigPropertyIndex#getProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDef getProperty(String name) {
        return getPropertyIndex().get(name);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigPropertyIndex#hasProperty(java.lang.String)
     */
    @Override
    public boolean hasProperty(String name) {
        return getPropertyIndex().containsKey(name);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigPropertyIndex#getPropertyNames()
     */
    @Override
    public Set<String> getPropertyNames() {
        return getPropertyIndex().keySet();
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigPropertyIndex#isAccepted(java.lang.String)
     */
    @Override
    public boolean isAccepted(String propertyName) {
        return this.getAcceptedPropertyNames().contains(propertyName);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigPropertyIndex#getAcceptedPropertyNames()
     */
    @Override
    public Set<String> getAcceptedPropertyNames() {
        return this.propertyIndex.entrySet().stream().filter(entry -> accept(entry.getValue()))
                .map(entry -> entry.getKey()).collect(Collectors.toSet());
    }

}
