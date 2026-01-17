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

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

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
 * --------------------------
 * Default Mode (Permissive - apicurio.authn.proxy-header.strict=false):
 * - Missing headers: Falls back to other authentication mechanisms or allows anonymous access
 * - Use when you want to support both proxy auth and other auth methods
 * - IMPORTANT: You MUST configure authorization policies to prevent unintended anonymous access:
 *   * Use apicurio.auth.role-based-authorization=true with role checks, OR
 *   * Configure Quarkus HTTP auth permission policies, OR
 *   * Use apicurio.authn.public-paths to explicitly define public endpoints
 *   Without proper authorization policies, missing headers will result in anonymous access!
 *
 * Strict Mode (apicurio.authn.proxy-header.strict=true):
 * - Missing headers: Authentication FAILS with 401 Unauthorized
 * - Use when the proxy is the ONLY authentication method
 * - Provides security by preventing accidental anonymous access
 * - No additional authorization policies required (all requests must have headers)
 *
 * Customize header names:
 * - apicurio.authn.proxy-header.username (default: X-Forwarded-User)
 * - apicurio.authn.proxy-header.email (default: X-Forwarded-Email)
 * - apicurio.authn.proxy-header.groups (default: X-Forwarded-Groups)
 *
 * Proxy-Based Authorization:
 * -------------------------
 * When apicurio.authn.proxy-header.trust-proxy-authorization=true:
 * - Registry assumes the proxy has already performed authorization
 * - All local authorization checks in AuthorizedInterceptor are SKIPPED for proxy-authenticated requests
 * - The proxy is fully responsible for access control decisions
 * - This applies ONLY to requests authenticated via proxy headers (verified by ProxyHeaderCredential)
 *
 * SECURITY CRITICAL: Only enable this when ALL of these conditions are met:
 * 1. The proxy performs comprehensive authorization checks before forwarding requests
 * 2. Direct access to Registry is completely blocked via network isolation
 * 3. The proxy cannot be bypassed by clients
 * 4. You trust the proxy to make all authorization decisions
 *
 * Without these safeguards, this setting creates CRITICAL SECURITY VULNERABILITIES
 * where unauthorized users can access protected resources.
 *
 * Use Cases:
 * - OAuth2 Proxy with policy-based access control
 * - API Gateways performing fine-grained authorization
 * - Enterprise proxies with centralized access management
 */
@ApplicationScoped
public class ProxyHeaderAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger log = LoggerFactory.getLogger(ProxyHeaderAuthenticationMechanism.class);

    @Inject
    AuthConfig authConfig;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {

        // If proxy header auth is not enabled, return null to allow other mechanisms to try
        if (!authConfig.proxyHeaderAuthEnabled) {
            return Uni.createFrom().nullItem();
        }

        // Extract username from header
        String username = context.request().getHeader(authConfig.usernameHeader);

        // If username header is not present or empty, handle based on strict mode
        if (username == null || username.trim().isEmpty()) {
            log.trace(
                    "Proxy header authentication skipped: {} header is missing or empty. "
                            + "Falling back to other authentication mechanisms.",
                    authConfig.usernameHeader);
            // Return empty to allow other authentication mechanisms to try
            return Uni.createFrom().nullItem();
        }

        // Extract email and groups (optional)
        String email = context.request().getHeader(authConfig.emailHeader);
        String groupsValue = context.request().getHeader(authConfig.groupsHeader);

        // Parse groups from comma-separated string
        Set<String> groups = parseGroups(groupsValue);

        log.trace("Proxy header authentication: username={}, email={}, groups={}", username, email,
                groups);

        // Create authentication request
        ProxyHeaderAuthenticationRequest authRequest = new ProxyHeaderAuthenticationRequest(username,
                email, groups);

        // Delegate to identity provider manager
        return identityProviderManager.authenticate(authRequest);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if (authConfig.proxyHeaderAuthEnabled) {
            // Return 401 Unauthorized
            // The proxy should intercept this and redirect to the identity provider
            return Uni.createFrom()
                    .item(new ChallengeData(401, "WWW-Authenticate", "Proxy authentication required"));
        }
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        if (authConfig.proxyHeaderAuthEnabled) {
            return Collections.singleton(ProxyHeaderAuthenticationRequest.class);
        }
        return Collections.emptySet();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        if (authConfig.proxyHeaderAuthEnabled) {
            return Uni.createFrom().item(new HttpCredentialTransport(
                    HttpCredentialTransport.Type.OTHER_HEADER, authConfig.usernameHeader));
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
