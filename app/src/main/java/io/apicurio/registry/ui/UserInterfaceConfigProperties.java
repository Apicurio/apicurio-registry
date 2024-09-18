package io.apicurio.registry.ui;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class UserInterfaceConfigProperties {

    @ConfigProperty(name = "apicurio.ui.contextPath", defaultValue = "/")
    @Info(category = "ui", description = "Context path of the UI", availableSince = "3.0.0")
    public String contextPath;
    @ConfigProperty(name = "apicurio.ui.navPrefixPath", defaultValue = "/")
    @Info(category = "ui", description = "Navigation prefix for all UI paths", availableSince = "3.0.0")
    public String navPrefixPath;
    @ConfigProperty(name = "apicurio.ui.docsUrl", defaultValue = "/docs/")
    @Info(category = "ui", description = "URL of the Documentation component", availableSince = "3.0.0")
    public String docsUrl;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    public String authOidcUrl;
    @ConfigProperty(name = "apicurio.ui.auth.oidc.redirect-uri", defaultValue = "/")
    @Info(category = "ui", description = "The OIDC redirectUri", availableSince = "3.0.0")
    public String authOidcRedirectUri;
    @ConfigProperty(name = "apicurio.ui.auth.oidc.client-id", defaultValue = "apicurio-registry-ui")
    @Info(category = "ui", description = "The OIDC clientId", availableSince = "3.0.0")
    public String authOidcClientId;
    @ConfigProperty(name = "apicurio.ui.auth.oidc.logout-url", defaultValue = "f5")
    @Info(category = "ui", description = "The OIDC logout URL", availableSince = "3.0.0")
    public String authOidcLogoutUrl;

    @ConfigProperty(name = "apicurio.ui.features.read-only.enabled", defaultValue = "false")
    @Info(category = "ui", description = "Enabled to set the UI to read-only mode", availableSince = "3.0.0")
    public String featureReadOnly;
    @ConfigProperty(name = "apicurio.ui.features.breadcrumbs", defaultValue = "true")
    @Info(category = "ui", description = "Enabled to show breadcrumbs in the UI", availableSince = "3.0.0")
    public String featureBreadcrumbs;
    @ConfigProperty(name = "apicurio.ui.features.settings", defaultValue = "true")
    @Info(category = "ui", description = "Enabled to show the Settings tab in the UI", availableSince = "3.0.0")
    public String featureSettings;

}
