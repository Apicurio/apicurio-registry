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
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

import java.util.Set;

/**
 * A simple authentication strategy that delegates directly to an underlying
 * {@link HttpAuthenticationMechanism}. Used for mechanisms that require no additional wrapper
 * logic (e.g. basic auth, proxy-header auth).
 */
public class DelegatingAuthenticationStrategy implements AuthenticationStrategy {

    private final String strategyName;
    private final HttpAuthenticationMechanism delegate;

    /**
     * Creates a new delegating strategy.
     *
     * @param strategyName human-readable name for logging
     * @param delegate the underlying authentication mechanism
     */
    public DelegatingAuthenticationStrategy(String strategyName,
            HttpAuthenticationMechanism delegate) {
        this.strategyName = strategyName;
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return strategyName;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        return delegate.authenticate(context, identityProviderManager);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return delegate.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return delegate.getCredentialTypes();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return delegate.getCredentialTransport(context);
    }
}
