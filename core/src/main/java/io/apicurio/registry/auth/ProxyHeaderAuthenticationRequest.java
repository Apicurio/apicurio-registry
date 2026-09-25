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

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import java.util.Collections;
import java.util.Set;

/**
 * Authentication request for proxy header based authentication.
 *
 * This request contains user information extracted from HTTP headers that were
 * injected by a trusted reverse proxy (such as OAuth2 Proxy or Authelia).
 *
 * Typical headers used:
 * - X-Forwarded-User: Username/principal ID
 * - X-Forwarded-Email: User email address
 * - X-Forwarded-Groups: Comma-separated list of user groups/roles
 *
 * Security: This authentication mechanism should ONLY be enabled when the application
 * is deployed behind a trusted proxy that properly validates and injects these headers.
 */
public class ProxyHeaderAuthenticationRequest extends BaseAuthenticationRequest {

    private final String username;
    private final String email;
    private final Set<String> groups;

    /**
     * Creates a new proxy header authentication request.
     *
     * @param username the username from the proxy header
     * @param email the email address from the proxy header
     * @param groups the set of groups/roles from the proxy header
     */
    public ProxyHeaderAuthenticationRequest(String username, String email, Set<String> groups) {
        this.username = username;
        this.email = email;
        this.groups = groups != null ? Collections.unmodifiableSet(groups) : Collections.emptySet();
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the email address.
     *
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Gets the set of groups/roles.
     *
     * @return the unmodifiable set of groups/roles
     */
    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public String toString() {
        return "ProxyHeaderAuthenticationRequest{" + "username='" + username + '\'' + ", email='"
                + email + '\'' + ", groups=" + groups + '}';
    }
}
