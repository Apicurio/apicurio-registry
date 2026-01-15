/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

/**
 * HTTP authentication mechanism that trusts headers injected by a reverse proxy.
 *
 * This mechanism extracts user information from HTTP headers that are injected by a
 * trusted reverse proxy (such as OAuth2 Proxy, Authelia, or Pomerium). The proxy
 * is responsible for performing authentication and then forwarding requests with
 * headers containing user identity information.
 *
 * Headers processed:
 * - X-Forwarded-User: Username/principal ID (configurable)
 * - X-Forwarded-Email: User email address (configurable)
 * - X-Forwarded-Groups: Comma-separated list of user groups/roles (configurable)
 *
 * Security Considerations:
 * ----------------------
 * CRITICAL: This mechanism should ONLY be enabled when ALL of the following are true:
 *
 * 1. The application is deployed behind a trusted reverse proxy
 * 2. The proxy strips all X-Forwarded-* headers from client requests
 * 3. The proxy re-injects these headers after successful authentication
 * 4. Direct access to the application is blocked (network isolation)
 * 5. The quarkus.http.proxy.trusted-proxies setting is configured correctly
 *
 * Failure to meet these requirements will result in CRITICAL SECURITY VULNERABILITIES
 * where clients can spoof authentication by setting headers directly.
 *
 * Configuration:
 * -------------
 * Enable this mechanism with: apicurio.authn.proxy-header.enabled=true
 *
 * Strict vs Permissive Mode:
 * - apicurio.authn.proxy-header.strict=true (default): Authentication FAILS if headers are missing.
 *   Use this when the proxy is the ONLY authentication method. Provides security by preventing
 *   accidental anonymous access.
 *
 * - apicurio.authn.proxy-header.strict=false: Falls back to other authentication mechanisms if
 *   headers are missing. Use this when you want to support both proxy auth and direct auth
 *   (e.g., during migration or for testing).
 *
 * Customize header names:
 * - apicurio.authn.proxy-header.username (default: X-Forwarded-User)
 * - apicurio.authn.proxy-header.email (default: X-Forwarded-Email)
 * - apicurio.authn.proxy-header.groups (default: X-Forwarded-Groups)
 *
 * This mechanism has priority 2, which is higher than AppAuthenticationMechanism (priority 1),
 * so it will be tried first when enabled.
 */
@Alternative
@Priority(2)
@ApplicationScoped
@Unremovable
public class ProxyHeaderAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger log = LoggerFactory.getLogger(ProxyHeaderAuthenticationMechanism.class);

    private static final String DEFAULT_USERNAME_HEADER = "X-Forwarded-User";
    private static final String DEFAULT_EMAIL_HEADER = "X-Forwarded-Email";
    private static final String DEFAULT_GROUPS_HEADER = "X-Forwarded-Groups";

    @Dynamic(label = "Enable proxy header authentication", description = "When selected, the application trusts user identity from HTTP headers injected by a reverse proxy. SECURITY WARNING: Only enable when behind a trusted proxy with proper network isolation.")
    @ConfigProperty(name = "apicurio.authn.proxy-header.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable proxy header authentication", availableSince = "3.1.0", registryAvailableSince = "3.1.0.Final", studioAvailableSince = "1.0.0")
    Supplier<Boolean> proxyHeaderAuthEnabled;

    @Dynamic(label = "Strict mode for proxy header authentication", description = "When enabled, authentication fails if proxy headers are missing (strict mode). When disabled, the system falls back to other authentication mechanisms if headers are missing (permissive mode).")
    @ConfigProperty(name = "apicurio.authn.proxy-header.strict", defaultValue = "true")
    @Info(category = CATEGORY_AUTH, description = "Require proxy headers when proxy authentication is enabled", availableSince = "3.1.0", registryAvailableSince = "3.1.0.Final", studioAvailableSince = "1.0.0")
    Supplier<Boolean> proxyHeaderAuthStrict;

    @ConfigProperty(name = "apicurio.authn.proxy-header.username", defaultValue = DEFAULT_USERNAME_HEADER)
    @Info(category = CATEGORY_AUTH, description = "Header name for username", availableSince = "3.1.0", registryAvailableSince = "3.1.0.Final", studioAvailableSince = "1.0.0")
    String usernameHeader;

    @ConfigProperty(name = "apicurio.authn.proxy-header.email", defaultValue = DEFAULT_EMAIL_HEADER)
    @Info(category = CATEGORY_AUTH, description = "Header name for email", availableSince = "3.1.0", registryAvailableSince = "3.1.0.Final", studioAvailableSince = "1.0.0")
    String emailHeader;

    @ConfigProperty(name = "apicurio.authn.proxy-header.groups", defaultValue = DEFAULT_GROUPS_HEADER)
    @Info(category = CATEGORY_AUTH, description = "Header name for groups/roles", availableSince = "3.1.0", registryAvailableSince = "3.1.0.Final", studioAvailableSince = "1.0.0")
    String groupsHeader;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {

        // If proxy header auth is not enabled, return null to allow other mechanisms to try
        if (!proxyHeaderAuthEnabled.get()) {
            return Uni.createFrom().nullItem();
        }

        // Extract username from header
        String username = context.request().getHeader(usernameHeader);

        // If username header is not present or empty, handle based on strict mode
        if (username == null || username.trim().isEmpty()) {
            if (proxyHeaderAuthStrict.get()) {
                // STRICT MODE: Fail authentication with 401
                log.warn(
                        "Proxy header authentication failed (strict mode): {} header is missing or empty. "
                                + "Request will be rejected. Ensure the proxy is injecting required headers, "
                                + "or set apicurio.authn.proxy-header.strict=false to allow fallback authentication.",
                        usernameHeader);
                // Return a failed authentication (will trigger 401 response)
                return Uni.createFrom().failure(
                        new io.quarkus.security.AuthenticationFailedException(
                                "Proxy authentication required: " + usernameHeader
                                        + " header not found"));
            } else {
                // PERMISSIVE MODE: Allow fallback to other authentication mechanisms
                log.debug(
                        "Proxy header authentication skipped (permissive mode): {} header is missing or empty. "
                                + "Falling back to other authentication mechanisms.",
                        usernameHeader);
                // Return empty to allow other authentication mechanisms to try
                return Uni.createFrom().nullItem();
            }
        }

        // Extract email and groups (optional)
        String email = context.request().getHeader(emailHeader);
        String groupsValue = context.request().getHeader(groupsHeader);

        // Parse groups from comma-separated string
        Set<String> groups = parseGroups(groupsValue);

        log.debug("Proxy header authentication: username={}, email={}, groups={}", username, email,
                groups);

        // Create authentication request
        ProxyHeaderAuthenticationRequest authRequest = new ProxyHeaderAuthenticationRequest(username,
                email, groups);

        // Delegate to identity provider manager
        return identityProviderManager.authenticate(authRequest);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if (proxyHeaderAuthEnabled.get()) {
            // Return 401 Unauthorized
            // The proxy should intercept this and redirect to the identity provider
            return Uni.createFrom()
                    .item(new ChallengeData(401, "WWW-Authenticate", "Proxy authentication required"));
        }
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        if (proxyHeaderAuthEnabled.get()) {
            return Collections.singleton(ProxyHeaderAuthenticationRequest.class);
        }
        return Collections.emptySet();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        if (proxyHeaderAuthEnabled.get()) {
            return Uni.createFrom().item(new HttpCredentialTransport(
                    HttpCredentialTransport.Type.OTHER_HEADER, usernameHeader));
        }
        return Uni.createFrom().nullItem();
    }

    /**
     * Parses a comma-separated list of groups from a header value.
     *
     * @param groupsValue the header value containing comma-separated groups
     * @return a set of group names, or empty set if null/empty
     */
    private Set<String> parseGroups(String groupsValue) {
        if (groupsValue == null || groupsValue.trim().isEmpty()) {
            return Collections.emptySet();
        }

        return Arrays.stream(groupsValue.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
