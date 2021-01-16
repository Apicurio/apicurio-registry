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

package io.apicurio.registry.services.tenant;

import io.apicurio.registry.mt.TenantContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.vertx.ext.web.RoutingContext;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class CustomTenantConfigResolver implements TenantConfigResolver {

	private static final String TENANT_ID_KEY = "tenant-id";

	@Inject
	TenantContext tenantContext;

	@Override
	public OidcTenantConfig resolve(RoutingContext context) {

		final String tenantId = context.request().getHeader(TENANT_ID_KEY);

		if (null == tenantId) {
			// resolve to default tenant configuration
			return null;
		}

		tenantContext.tenantId(tenantId);

		//TODO fetch oidc config from the database

		if ("tenant-a".equals(tenantId)) {
			final OidcTenantConfig config = new OidcTenantConfig();

			config.setTenantId(tenantId);
			config.setAuthServerUrl("http://localhost:8090/auth/realms/registry");
			config.setClientId("tenant-a-api-client");
			OidcTenantConfig.Credentials credentials = new OidcTenantConfig.Credentials();
			config.setCredentials(credentials);

			return config;

		} else if ("tenant-b".equals(tenantId)) {

			final OidcTenantConfig config = new OidcTenantConfig();

			config.setTenantId(tenantId);
			config.setAuthServerUrl("http://localhost:8090/auth/realms/registry");
			config.setClientId("tenant-b-api-client");
			OidcTenantConfig.Credentials credentials = new OidcTenantConfig.Credentials();
			config.setCredentials(credentials);

			return config;
		}

		return null;
	}
}
