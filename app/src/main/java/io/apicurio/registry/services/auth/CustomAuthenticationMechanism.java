/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.services.auth;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.BasicAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.util.Set;

@Alternative
@Priority(1)
@ApplicationScoped
public class CustomAuthenticationMechanism implements HttpAuthenticationMechanism {

	@Inject
	BasicAuthenticationMechanism basicAuthenticationMechanism;

	@Inject
	OidcAuthenticationMechanism oidcAuthenticationMechanism;

	@Override
	public io.smallrye.mutiny.Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
		// do some custom action and delegate to proper auth mechanism

		return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
	}

	@Override
	public io.smallrye.mutiny.Uni<ChallengeData> getChallenge(RoutingContext context) {

		return oidcAuthenticationMechanism.getChallenge(context);
	}

	@Override
	public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
		return oidcAuthenticationMechanism.getCredentialTypes();
	}

	@Override
	public HttpCredentialTransport getCredentialTransport() {
		return oidcAuthenticationMechanism.getCredentialTransport();
	}
}
