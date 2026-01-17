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

import io.quarkus.security.credential.Credential;

/**
 * Credential object that holds user information extracted from proxy headers.
 *
 * This credential is used in the proxy header authentication flow where an external
 * authentication proxy (like OAuth2 Proxy or Authelia) handles authentication and
 * injects trusted headers with user information.
 *
 * Security Warning: This credential should ONLY be used when the application is deployed
 * behind a trusted reverse proxy that strips and re-injects these headers. Direct
 * exposure of the application would allow header spoofing attacks.
 */
public class ProxyHeaderCredential implements Credential {

    private final String username;
    private final String email;

    /**
     * Creates a new proxy header credential.
     *
     * @param username the username from the X-Forwarded-User header
     * @param email the email address from the X-Forwarded-Email header
     */
    public ProxyHeaderCredential(String username, String email) {
        this.username = username;
        this.email = email;
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

    @Override
    public String toString() {
        return "ProxyHeaderCredential{" + "username='" + username + '\'' + ", email='" + email + '\''
                + '}';
    }
}
