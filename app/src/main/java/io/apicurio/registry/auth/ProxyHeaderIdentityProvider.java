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

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.Principal;

/**
 * Identity provider that creates a SecurityIdentity from proxy header authentication.
 *
 * This provider converts a ProxyHeaderAuthenticationRequest (containing user information
 * from proxy headers) into a Quarkus SecurityIdentity that can be used for authorization
 * decisions throughout the application.
 *
 * The SecurityIdentity will contain:
 * - Principal (username)
 * - Roles (from groups header)
 * - Credential (ProxyHeaderCredential with username and email)
 */
@ApplicationScoped
public class ProxyHeaderIdentityProvider
        implements IdentityProvider<ProxyHeaderAuthenticationRequest> {

    @Override
    public Class<ProxyHeaderAuthenticationRequest> getRequestType() {
        return ProxyHeaderAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(ProxyHeaderAuthenticationRequest request,
            AuthenticationRequestContext context) {

        // Create the principal from the username
        Principal principal = new Principal() {
            @Override
            public String getName() {
                return request.getUsername();
            }

            @Override
            public String toString() {
                return "ProxyHeaderPrincipal{name='" + getName() + "'}";
            }
        };

        // Build the security identity with principal, roles, and credential
        SecurityIdentity identity = QuarkusSecurityIdentity.builder().setPrincipal(principal)
                .addRoles(request.getGroups())
                .addCredential(new ProxyHeaderCredential(request.getUsername(), request.getEmail()))
                .build();

        return Uni.createFrom().item(identity);
    }
}
