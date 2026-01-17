package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class AuthConfig {

    private static final String DEFAULT_USERNAME_HEADER = "X-Forwarded-User";
    private static final String DEFAULT_EMAIL_HEADER = "X-Forwarded-Email";
    private static final String DEFAULT_GROUPS_HEADER = "X-Forwarded-Groups";

    @Inject
    Logger log;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable auth", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.0.0.Final", studioAvailableSince = "1.0.0")
    boolean oidcAuthEnabled;

    // back to fake auth and use another property
    @Dynamic(label = "HTTP basic authentication", description = "When selected, users are permitted to authenticate using HTTP basic authentication (in addition to OAuth).", requires = "apicurio.authn.enabled=true")
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable basic auth client credentials", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    Supplier<Boolean> basicClientCredentialsAuthEnabled;

    @ConfigProperty(name = "quarkus.http.auth.basic", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable basic auth", availableSince = "1.1.X-SNAPSHOT", registryAvailableSince = "3.X.X.Final", studioAvailableSince = "1.0.0")
    boolean basicAuthEnabled;

    // TODO: Add suffix?
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.cache-expiration", defaultValue = "10")
    @Info(category = CATEGORY_AUTH, description = "Default client credentials token expiration time in minutes.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.2.6.Final", studioAvailableSince = "1.0.0")
    Integer accessTokenExpiration;

    // TODO: Add suffix?
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.cache-expiration-offset", defaultValue = "10")
    @Info(category = CATEGORY_AUTH, description = "Client credentials token expiration offset from JWT expiration, in seconds.", availableSince = "0.2.7", registryAvailableSince = "2.5.9.Final", studioAvailableSince = "1.0.0")
    Integer accessTokenExpirationOffset;

    @ConfigProperty(name = "apicurio.authn.basic.scope")
    @Info(category = CATEGORY_AUTH, description = "Client credentials scope.", availableSince = "0.1.21-SNAPSHOT", registryAvailableSince = "2.5.0.Final", studioAvailableSince = "1.0.0")
    Optional<String> scope;

    @ConfigProperty(name = "apicurio.authn.audit.log.prefix", defaultValue = "audit")
    @Info(category = CATEGORY_AUTH, description = "Prefix used for application audit logging.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.2.6", studioAvailableSince = "1.0.0")
    String auditLogPrefix;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url", defaultValue = "_")
    @Info(category = CATEGORY_AUTH, description = "Authentication server endpoint.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.token-path", defaultValue = "/protocol/openid-connect/token/")
    @Info(category = CATEGORY_AUTH, description = "Authentication server token endpoint.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    String oidcTokenPath;

    @ConfigProperty(name = "quarkus.oidc.client-secret")
    @Info(category = CATEGORY_AUTH, description = "Client secret used by the server for authentication.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    Optional<String> clientSecret;

    @ConfigProperty(name = "quarkus.oidc.client-id", defaultValue = "")
    @Info(category = CATEGORY_AUTH, description = "Client identifier used by the server for authentication.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.0.0.Final", studioAvailableSince = "1.0.0")
    String clientId;

    @ConfigProperty(name = "apicurio.auth.role-based-authorization", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable role based authorization", availableSince = "2.1.0.Final")
    boolean roleBasedAuthorizationEnabled;

    @Dynamic(label = "Artifact owner-only authorization", description = "When selected, Service Registry allows only the artifact owner (creator) to modify an artifact.", requires = "quarkus.oidc.tenant-enabled=true")
    @ConfigProperty(name = "apicurio.auth.owner-only-authorization", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Artifact owner-only authorization", availableSince = "2.0.0.Final")
    Supplier<Boolean> ownerOnlyAuthorizationEnabled;

    @Dynamic(label = "Artifact group owner-only authorization", description = "When selected, Service Registry allows only the artifact group owner (creator) to modify an artifact group.", requires = {
            "quarkus.oidc.tenant-enabled=true", "apicurio.auth.owner-only-authorization=true" })
    @ConfigProperty(name = "apicurio.auth.owner-only-authorization.limit-group-access", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Artifact group owner-only authorization", availableSince = "2.1.0.Final")
    Supplier<Boolean> ownerOnlyAuthorizationLimitGroupAccess;

    @Dynamic(label = "Anonymous read access", description = "When selected, requests from anonymous users (requests without any credentials) are granted read-only access.", requires = "quarkus.oidc.tenant-enabled=true")
    @ConfigProperty(name = "apicurio.auth.anonymous-read-access.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Anonymous read access", availableSince = "2.1.0.Final")
    Supplier<Boolean> anonymousReadAccessEnabled;

    @Dynamic(label = "Authenticated read access", description = "When selected, requests from any authenticated user are granted at least read-only access.", requires = {
            "quarkus.oidc.tenant-enabled=true", "apicurio.auth.role-based-authorization=true" })
    @ConfigProperty(name = "apicurio.auth.authenticated-read-access.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Authenticated read access", availableSince = "2.1.4.Final")
    Supplier<Boolean> authenticatedReadAccessEnabled;

    @ConfigProperty(name = "apicurio.auth.roles.readonly", defaultValue = "sr-readonly")
    @Info(category = CATEGORY_AUTH, description = "Auth roles readonly", availableSince = "2.1.0.Final")
    String readOnlyRole;

    @ConfigProperty(name = "apicurio.auth.roles.developer", defaultValue = "sr-developer")
    @Info(category = CATEGORY_AUTH, description = "Auth roles developer", availableSince = "2.1.0.Final")
    String developerRole;

    @ConfigProperty(name = "apicurio.auth.roles.admin", defaultValue = "sr-admin")
    @Info(category = CATEGORY_AUTH, description = "Auth roles admin", availableSince = "2.0.0.Final")
    String adminRole;

    @ConfigProperty(name = "apicurio.auth.role-source", defaultValue = "token")
    @Info(category = CATEGORY_AUTH, description = "Auth roles source", availableSince = "2.1.0.Final")
    String roleSource;

    @ConfigProperty(name = "apicurio.auth.admin-override.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override enabled", availableSince = "2.1.0.Final")
    boolean adminOverrideEnabled;

    @ConfigProperty(name = "apicurio.auth.admin-override.from", defaultValue = "token")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override from", availableSince = "2.1.0.Final")
    String adminOverrideFrom;

    @ConfigProperty(name = "apicurio.auth.admin-override.type", defaultValue = "role")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override type", availableSince = "2.1.0.Final")
    String adminOverrideType;

    @ConfigProperty(name = "apicurio.auth.admin-override.role", defaultValue = "sr-admin")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override role", availableSince = "2.1.0.Final")
    String adminOverrideRole;

    @ConfigProperty(name = "apicurio.auth.admin-override.claim", defaultValue = "org-admin")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override claim", availableSince = "2.1.0.Final")
    String adminOverrideClaim;

    @ConfigProperty(name = "apicurio.auth.admin-override.claim-value", defaultValue = "true")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override claim value", availableSince = "2.1.0.Final")
    String adminOverrideClaimValue;

    @ConfigProperty(name = "apicurio.auth.admin-override.user", defaultValue = "admin")
    @Info(category = CATEGORY_AUTH, description = "Auth admin override user name", availableSince = "3.0.0")
    String adminOverrideUser;

    @ConfigProperty(name = "apicurio.authn.proxy-header.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable proxy header authentication", availableSince = "3.1.7")
    boolean proxyHeaderAuthEnabled;

    @ConfigProperty(name = "apicurio.authn.proxy-header.username", defaultValue = DEFAULT_USERNAME_HEADER)
    @Info(category = CATEGORY_AUTH, description = "Header name for username", availableSince = "3.1.7")
    String usernameHeader;

    @ConfigProperty(name = "apicurio.authn.proxy-header.email", defaultValue = DEFAULT_EMAIL_HEADER)
    @Info(category = CATEGORY_AUTH, description = "Header name for email", availableSince = "3.1.7")
    String emailHeader;

    @ConfigProperty(name = "apicurio.authn.proxy-header.groups", defaultValue = DEFAULT_GROUPS_HEADER)
    @Info(category = CATEGORY_AUTH, description = "Header name for groups/roles", availableSince = "3.1.7")
    String groupsHeader;

    @ConfigProperty(name = "apicurio.authn.proxy-header.trust-proxy-authorization", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "When enabled, authorization checks are skipped and the proxy is trusted to have performed authorization", availableSince = "3.1.0", registryAvailableSince = "3.1.0.Final", studioAvailableSince = "1.0.0")
    boolean proxyHeaderTrustProxyAuthorization;

    @PostConstruct
    void onConstruct() {
        log.debug("===============================");
        log.debug("OIDC Auth Enabled: " + oidcAuthEnabled);
        log.debug("Basic Auth Enabled: " + basicAuthEnabled);
        log.debug("Proxy Auth Enabled: " + proxyHeaderAuthEnabled);
        log.debug("Anonymous Read Access Enabled: " + anonymousReadAccessEnabled);
        log.debug("Authenticated Read Access Enabled: " + authenticatedReadAccessEnabled);
        log.debug("RBAC Enabled: " + roleBasedAuthorizationEnabled);
        if (roleBasedAuthorizationEnabled) {
            log.debug("   RBAC Roles: " + readOnlyRole + ", " + developerRole + ", " + adminRole);
            log.debug("   Role Source: " + roleSource);
        }
        log.debug("OBAC Enabled: " + ownerOnlyAuthorizationEnabled);
        log.debug("Admin Override Enabled: " + adminOverrideEnabled);
        if (adminOverrideEnabled) {
            log.debug("   Admin Override from: " + adminOverrideFrom);
            log.debug("   Admin Override type: " + adminOverrideType);
            log.debug("   Admin Override role: " + adminOverrideRole);
            log.debug("   Admin Override claim: " + adminOverrideClaim);
            log.debug("   Admin Override claim-value: " + adminOverrideClaimValue);
        }
        log.debug("===============================");
    }

    public boolean isOidcAuthEnabled() {
        return this.oidcAuthEnabled;
    }

    public boolean isBasicAuthEnabled() {
        return this.basicAuthEnabled;
    }

    public boolean isRbacEnabled() {
        return this.roleBasedAuthorizationEnabled;
    }

    public boolean isObacEnabled() {
        return this.ownerOnlyAuthorizationEnabled.get();
    }

    public boolean isAdminOverrideEnabled() {
        return this.adminOverrideEnabled;
    }

    public String getRoleSource() {
        return this.roleSource;
    }

    public boolean isApplicationRbacEnabled() {
        return this.roleBasedAuthorizationEnabled && "application".equals(getRoleSource());
    }

    public boolean isAnonymousReadsEnabled() {
        return anonymousReadAccessEnabled.get();
    }

    public boolean isAuthenticatedReadsEnabled() {
        return authenticatedReadAccessEnabled.get();
    }

}
