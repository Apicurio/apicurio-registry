package io.apicurio.registry.ui;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_UI;

@Singleton
public class UserInterfaceConfigProperties {

    @ConfigProperty(name = "apicurio.ui.contextPath", defaultValue = "/")
    @Info(category = CATEGORY_UI, description = "Context path of the UI", availableSince = "3.0.0")
    public String contextPath;
    @ConfigProperty(name = "apicurio.ui.navPrefixPath", defaultValue = "/")
    @Info(category = CATEGORY_UI, description = "Navigation prefix for all UI paths", availableSince = "3.0.0")
    public String navPrefixPath;
    @ConfigProperty(name = "apicurio.ui.docsUrl", defaultValue = "/docs/")
    @Info(category = CATEGORY_UI, description = "URL of the Documentation component", availableSince = "3.0.0")
    public String docsUrl;
    @ConfigProperty(name = "apicurio.ui.editorsUrl", defaultValue = "/editors/")
    @Info(category = CATEGORY_UI, description = "URL of the Editors component", availableSince = "3.1.0")
    public String editorsUrl;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url", defaultValue = "_")
    public String authOidcUrl;
    @ConfigProperty(name = "apicurio.ui.auth.oidc.redirect-uri")
    @Info(category = CATEGORY_UI, description = "The OIDC redirectUri", availableSince = "3.0.0")
    public Optional<String> authOidcRedirectUri;
    @ConfigProperty(name = "apicurio.ui.auth.oidc.client-id", defaultValue = "apicurio-registry-ui")
    @Info(category = CATEGORY_UI, description = "The OIDC clientId", availableSince = "3.0.0")
    public String authOidcClientId;
    @ConfigProperty(name = "apicurio.ui.auth.oidc.logout-url", defaultValue = "f5")
    @Info(category = CATEGORY_UI, description = "The OIDC logout URL", availableSince = "3.0.0")
    public String authOidcLogoutUrl;

    @ConfigProperty(name = "apicurio.ui.features.read-only.enabled", defaultValue = "false")
    @Info(category = CATEGORY_UI, description = "Enabled to set the UI to read-only mode", availableSince = "3.0.0")
    public String featureReadOnly;
    @ConfigProperty(name = "apicurio.ui.features.breadcrumbs", defaultValue = "true")
    @Info(category = CATEGORY_UI, description = "Enabled to show breadcrumbs in the UI", availableSince = "3.0.0")
    public String featureBreadcrumbs;
    @ConfigProperty(name = "apicurio.ui.features.settings", defaultValue = "true")
    @Info(category = CATEGORY_UI, description = "Enabled to show the Settings tab in the UI", availableSince = "3.0.0")
    public String featureSettings;
    @Dynamic(label = "Agents tab", description = "When enabled, the Agents tab will be visible in the UI for A2A Agent discovery.")
    @ConfigProperty(name = "apicurio.ui.features.agents.enabled", defaultValue = "false")
    @Info(category = CATEGORY_UI, description = "Enabled to show the Agents tab in the UI", availableSince = "3.2.0", experimental = true)
    public Supplier<Boolean> featureAgents;

    @ConfigProperty(name = "apicurio.ui.auth.oidc.scope", defaultValue = "openid profile email")
    @Info(category = CATEGORY_UI, description = "UI auth OIDC scope value", availableSince = "3.0.8")
    public String scope;

    @ConfigProperty(name = "apicurio.ui.auth.oidc.load-user-info")
    @Info(category = CATEGORY_UI, description = "Whether to load user info from the OIDC userinfo endpoint. Defaults to true if not specified. Set to false for OIDC providers like Azure Entra ID where the userinfo endpoint has incompatible audience requirements.", availableSince = "3.1.6")
    public Optional<Boolean> loadUserInfo;

}
