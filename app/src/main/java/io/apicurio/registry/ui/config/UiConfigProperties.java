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
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.utils.RegistryProperties;

/**
 * Holds/accesses all configuration settings for the UI.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class UiConfigProperties {

    @Inject
    Logger log;

    @Inject
    @Dynamic(label = "UI read-only mode", description = "When selected, the Service Registry web console is set to read-only, preventing create, read, update, or delete operations.")
    @ConfigProperty(name = "registry.ui.features.readOnly", defaultValue = "false")
    @Info(category = "ui", description = "UI read-only mode", availableSince = "1.2.0.Final")
    Supplier<Boolean> featureReadOnly;

    @Inject
    @ConfigProperty(name = "registry.ui.features.settings", defaultValue = "false")
    @Info(category = "ui", description = "UI features settings", availableSince = "2.2.2.Final")
    boolean featureSettings;

    @Inject
    @ConfigProperty(name = "registry.ui.config.uiContextPath", defaultValue = "/ui/")
    @Info(category = "ui", description = "UI context path", availableSince = "2.1.0.Final")
    String uiContextPath;

    @Inject
    @ConfigProperty(name = "registry.ui.config.apiUrl")
    @Info(category = "ui", description = "UI APIs URL", availableSince = "1.3.0.Final")
    String apiUrl;

    @Inject
    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    @Info(category = "ui", description = "UI OIDC tenant enabled", availableSince = "2.0.0.Final")
    boolean tenantEnabled;

    @Inject
    @ConfigProperty(name = "registry.ui.config.auth.type", defaultValue = "none")
    @Info(category = "ui", description = "UI auth type", availableSince = "2.2.6.Final")
    String uiAuthType;

    @Inject
    @ConfigProperty(name = "registry.ui.config.auth.oidc.url", defaultValue = "none")
    @Info(category = "ui", description = "UI auth OIDC URL", availableSince = "2.2.6.Final")
    String oidcUrl;

    @Inject
    @ConfigProperty(name = "registry.ui.config.auth.oidc.client-id", defaultValue = "none")
    @Info(category = "ui", description = "UI auth OIDC client ID", availableSince = "2.2.6.Final")
    String oidcClientId;

    @Inject
    @ConfigProperty(name = "registry.ui.config.auth.oidc.redirect-url", defaultValue = "none")
    @Info(category = "ui", description = "UI auth OIDC redirect URL", availableSince = "2.2.6.Final")
    String oidcRedirectUri;

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
        log.debug("============> kcProperties  " + keycloakConfig);
        log.debug("============> tenantEnabled  " + tenantEnabled);
        log.debug("============> featureReadOnly  " + featureReadOnly);
        log.debug("============> featureSettings  " + featureSettings);
        log.debug("============> uiContextPath  " + uiContextPath);
        log.debug("============> apiUrl  " + apiUrl);
    }

    public Map<String, Object> getKeycloakProperties() {
        return keycloakConfig;
    }

    public boolean isFeatureReadOnly() {
        return featureReadOnly.get();
    }

    public boolean isFeatureSettings() {
        return featureSettings;
    }

    public String getUiContextPath() {
        return uiContextPath;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public boolean isAuthenticationEnabled() {
        return tenantEnabled;
    }

    public String getUiAuthType() {
        return uiAuthType;
    }

    public String getOidcUrl() {
        return oidcUrl;
    }

    public String getOidcClientId() {
        return oidcClientId;
    }

    public String getOidcRedirectUrl() {
        return oidcRedirectUri;
    }
}