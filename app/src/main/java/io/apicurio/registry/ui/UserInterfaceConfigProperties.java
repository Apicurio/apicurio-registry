package io.apicurio.registry.ui;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;

@Singleton
public class UserInterfaceConfigProperties {

    @ConfigProperty(name = "registry.ui.contextPath", defaultValue = "/")
    @Info(category = "ui", description = "Context path of the UI", availableSince = "3.0.0")
    public String contextPath;
    @ConfigProperty(name = "registry.ui.navPrefixPath", defaultValue = "/")
    @Info(category = "ui", description = "Navigation prefix for all UI paths", availableSince = "3.0.0")
    public String navPrefixPath;
    @ConfigProperty(name = "registry.ui.docsUrl", defaultValue = "/docs/")
    @Info(category = "ui", description = "URL of the Documentation component", availableSince = "3.0.0")
    public String docsUrl;
    
    
    @ConfigProperty(name = "registry.auth.url.configured")
    public String authOidcUrl;
    @ConfigProperty(name = "registry.ui.auth.oidc.redirectUri", defaultValue = "/")
    @Info(category = "ui", description = "The OIDC redirectUri", availableSince = "3.0.0")
    public String authOidcRedirectUri;
    @ConfigProperty(name = "registry.ui.auth.oidc.clientId", defaultValue = "apicurio-registry-ui")
    @Info(category = "ui", description = "The OIDC clientId", availableSince = "3.0.0")
    public String authOidcClientId;

    
    @ConfigProperty(name = "registry.ui.features.readOnly", defaultValue = "false")
    @Info(category = "ui", description = "Enabled to set the UI to read-only mode", availableSince = "3.0.0")
    public String featureReadOnly;
    @ConfigProperty(name = "registry.ui.features.breadcrumbs", defaultValue = "true")
    @Info(category = "ui", description = "Enabled to show breadcrumbs in the UI", availableSince = "3.0.0")
    public String featureBreadcrumbs;
    @ConfigProperty(name = "registry.ui.features.settings", defaultValue = "true")
    @Info(category = "ui", description = "Enabled to show the Settings tab in the UI", availableSince = "3.0.0")
    public String featureSettings;

}
