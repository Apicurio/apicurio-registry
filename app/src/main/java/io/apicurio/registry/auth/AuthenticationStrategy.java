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
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

import java.util.Set;

/**
 * Abstraction for an authentication strategy in the authentication chain. Each strategy wraps a
 * specific authentication mechanism and encapsulates all logic needed to authenticate requests of
 * that type.
 *
 * <p>Strategies are tried in priority order. If a strategy cannot handle a request (e.g. the
 * expected headers or credentials are not present), it returns a {@code Uni} emitting {@code null}
 * to signal that the next strategy in the chain should be tried.</p>
 */
public interface AuthenticationStrategy {

    /**
     * Returns a human-readable name for this strategy, used in logging (e.g. "basic",
     * "proxy-header", "oidc").
     */
    String name();

    /**
     * Attempts to authenticate the request. Returns a {@code Uni} emitting a
     * {@link SecurityIdentity} if this strategy handles the request, or a {@code Uni} emitting
     * {@code null} if it cannot handle it (allowing the next strategy in the chain to try).
     */
    Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager);

    /**
     * Returns challenge data for 401 responses.
     */
    Uni<ChallengeData> getChallenge(RoutingContext context);

    /**
     * Returns the set of credential types this strategy supports.
     */
    Set<Class<? extends AuthenticationRequest>> getCredentialTypes();

    /**
     * Returns credential transport information.
     */
    Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context);
}
