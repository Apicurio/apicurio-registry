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

package io.apicurio.registry.ui.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.utils.RegistryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds/accesses all configuration settings for the UI.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class UiConfigProperties {

    private static final Logger logger = LoggerFactory.getLogger(UiConfigProperties.class);
    
    @Inject
    @ConfigProperty(name = "registry.ui.features.readOnly", defaultValue = "false")
    boolean featureReadOnly;

    @Inject
    @ConfigProperty(name = "registry.ui.config.uiUrl")
    String uiUrl;

    @Inject
    @ConfigProperty(name = "registry.ui.config.apiUrl")
    String apiUrl;

    @Inject
    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    boolean tenantEnabled;

    private final Map<String, Object> keycloakConfig;
    
    /**
     * Constructor.
     * @param kcProperties
     */
    public UiConfigProperties(@RegistryProperties(value = {"registry.ui.config.auth.keycloak"}) Properties kcProperties) {
        this.keycloakConfig = new HashMap<>();
        kcProperties.stringPropertyNames().forEach(key -> keycloakConfig.put(key, kcProperties.get(key)));
    }
    
    @PostConstruct
    void onConstruct() {
        logger.debug("============> kcProperties  " + keycloakConfig);
        logger.debug("============> tenantEnabled  " + tenantEnabled);
        logger.debug("============> featureReadOnly  " + featureReadOnly);
        logger.debug("============> uiUrl  " + uiUrl);
        logger.debug("============> apiUrl  " + apiUrl);
    }

    public Map<String, Object> getKeycloakProperties() {
        return keycloakConfig;
    }
    
    public boolean isFeatureReadOnly() {
        return featureReadOnly;
    }
    
    public String getUiUrl() {
        return uiUrl;
    }
    
    public String getApiUrl() {
        return apiUrl;
    }
    
    public boolean isKeycloakAuthEnabled() {
        return tenantEnabled;
    }

}
