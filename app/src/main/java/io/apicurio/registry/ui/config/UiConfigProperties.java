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

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;
import io.apicurio.registry.utils.RegistryProperties;
import org.slf4j.Logger;

/**
 * Holds/accesses all configuration settings for the UI.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class UiConfigProperties {

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    boolean featureReadOnly;
    String uiContextPath;
    String apiUrl;
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
        featureReadOnly = configService.get(RegistryConfigProperty.REGISTRY_UI_FEATURES_READONLY, Boolean.class);
        uiContextPath = configService.get(RegistryConfigProperty.REGISTRY_UI_CONFIG_UI_CONTEXT_PATH);
        apiUrl = configService.get(RegistryConfigProperty.REGISTRY_UI_CONFIG_API_URL);
        tenantEnabled = configService.get(RegistryConfigProperty.REGISTRY_OIDC_TENANT_ENABLED, Boolean.class);

        log.debug("============> kcProperties  " + keycloakConfig);
        log.debug("============> tenantEnabled  " + tenantEnabled);
        log.debug("============> featureReadOnly  " + featureReadOnly);
        log.debug("============> uiContextPath  " + uiContextPath);
        log.debug("============> apiUrl  " + apiUrl);
    }

    public Map<String, Object> getKeycloakProperties() {
        return keycloakConfig;
    }

    public boolean isFeatureReadOnly() {
        return featureReadOnly;
    }

    public String getUiContextPath() {
        return uiContextPath;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public boolean isKeycloakAuthEnabled() {
        return tenantEnabled;
    }

}
