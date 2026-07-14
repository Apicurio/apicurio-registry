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

@ApplicationScoped
public class KubernetesIdentityProvider
        implements IdentityProvider<KubernetesAuthenticationRequest> {

    @Override
    public Class<KubernetesAuthenticationRequest> getRequestType() {
        return KubernetesAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(KubernetesAuthenticationRequest request,
            AuthenticationRequestContext context) {

        Principal principal = new Principal() {
            @Override
            public String getName() {
                return request.getUsername();
            }

            @Override
            public String toString() {
                return "KubernetesPrincipal{name='" + getName() + "'}";
            }
        };

        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRoles(request.getGroups())
                .addCredential(new KubernetesCredential(request.getUsername(), request.getUid()))
                .build();

        return Uni.createFrom().item(identity);
    }
}
