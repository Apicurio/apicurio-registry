package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Singleton
public class AuthConfig {

    @Inject
    Logger log;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    @Info(category = "auth", description = "Enable auth", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.0.0.Final", studioAvailableSince = "1.0.0")
    boolean oidcAuthEnabled;

    @Dynamic(label = "HTTP basic authentication", description = "When selected, users are permitted to authenticate using HTTP basic authentication (in addition to OAuth).", requires = "apicurio.authn.enabled=true")
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Enable basic auth client credentials", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    Supplier<Boolean> basicClientCredentialsAuthEnabled;

    @ConfigProperty(name = "quarkus.http.auth.basic", defaultValue = "false")
    @Info(category = "auth", description = "Enable basic auth", availableSince = "1.1.X-SNAPSHOT", registryAvailableSince = "3.X.X.Final", studioAvailableSince = "1.0.0")
    boolean basicAuthEnabled;

    @ConfigProperty(name = "apicurio.auth.role-based-authorization", defaultValue = "false")
    @Info(category = "auth", description = "Enable role based authorization", availableSince = "2.1.0.Final")
    boolean roleBasedAuthorizationEnabled;

    @Dynamic(label = "Artifact owner-only authorization", description = "When selected, Service Registry allows only the artifact owner (creator) to modify an artifact.", requires = "quarkus.oidc.tenant-enabled=true")
    @ConfigProperty(name = "apicurio.auth.owner-only-authorization", defaultValue = "false")
    @Info(category = "auth", description = "Artifact owner-only authorization", availableSince = "2.0.0.Final")
    Supplier<Boolean> ownerOnlyAuthorizationEnabled;

    @Dynamic(label = "Artifact group owner-only authorization", description = "When selected, Service Registry allows only the artifact group owner (creator) to modify an artifact group.", requires = {
            "quarkus.oidc.tenant-enabled=true", "apicurio.auth.owner-only-authorization=true" })
    @ConfigProperty(name = "apicurio.auth.owner-only-authorization.limit-group-access", defaultValue = "false")
    @Info(category = "auth", description = "Artifact group owner-only authorization", availableSince = "2.1.0.Final")
    Supplier<Boolean> ownerOnlyAuthorizationLimitGroupAccess;

    @Dynamic(label = "Anonymous read access", description = "When selected, requests from anonymous users (requests without any credentials) are granted read-only access.", requires = "quarkus.oidc.tenant-enabled=true")
    @ConfigProperty(name = "apicurio.auth.anonymous-read-access.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Anonymous read access", availableSince = "2.1.0.Final")
    Supplier<Boolean> anonymousReadAccessEnabled;

    @Dynamic(label = "Authenticated read access", description = "When selected, requests from any authenticated user are granted at least read-only access.", requires = {
            "quarkus.oidc.tenant-enabled=true", "apicurio.auth.role-based-authorization=true" })
    @ConfigProperty(name = "apicurio.auth.authenticated-read-access.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Authenticated read access", availableSince = "2.1.4.Final")
    Supplier<Boolean> authenticatedReadAccessEnabled;

    @ConfigProperty(name = "apicurio.auth.roles.readonly", defaultValue = "sr-readonly")
    @Info(category = "auth", description = "Auth roles readonly", availableSince = "2.1.0.Final")
    String readOnlyRole;

    @ConfigProperty(name = "apicurio.auth.roles.developer", defaultValue = "sr-developer")
    @Info(category = "auth", description = "Auth roles developer", availableSince = "2.1.0.Final")
    String developerRole;

    @ConfigProperty(name = "apicurio.auth.roles.admin", defaultValue = "sr-admin")
    @Info(category = "auth", description = "Auth roles admin", availableSince = "2.0.0.Final")
    String adminRole;

    @ConfigProperty(name = "apicurio.auth.role-source", defaultValue = "token")
    @Info(category = "auth", description = "Auth roles source", availableSince = "2.1.0.Final")
    String roleSource;

    @ConfigProperty(name = "apicurio.auth.admin-override.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Auth admin override enabled", availableSince = "2.1.0.Final")
    boolean adminOverrideEnabled;

    @ConfigProperty(name = "apicurio.auth.admin-override.from", defaultValue = "token")
    @Info(category = "auth", description = "Auth admin override from", availableSince = "2.1.0.Final")
    String adminOverrideFrom;

    @ConfigProperty(name = "apicurio.auth.admin-override.type", defaultValue = "role")
    @Info(category = "auth", description = "Auth admin override type", availableSince = "2.1.0.Final")
    String adminOverrideType;

    @ConfigProperty(name = "apicurio.auth.admin-override.role", defaultValue = "sr-admin")
    @Info(category = "auth", description = "Auth admin override role", availableSince = "2.1.0.Final")
    String adminOverrideRole;

    @ConfigProperty(name = "apicurio.auth.admin-override.claim", defaultValue = "org-admin")
    @Info(category = "auth", description = "Auth admin override claim", availableSince = "2.1.0.Final")
    String adminOverrideClaim;

    @ConfigProperty(name = "apicurio.auth.admin-override.claim-value", defaultValue = "true")
    @Info(category = "auth", description = "Auth admin override claim value", availableSince = "2.1.0.Final")
    String adminOverrideClaimValue;

    @ConfigProperty(name = "apicurio.auth.admin-override.user", defaultValue = "admin")
    @Info(category = "auth", description = "Auth admin override user name", availableSince = "3.0.0.Final")
    String adminOverrideUser;

    @PostConstruct
    void onConstruct() {
        log.debug("===============================");
        log.debug("OIDC Auth Enabled: " + oidcAuthEnabled);
        log.debug("Basic Auth Enabled: " + basicAuthEnabled);
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
