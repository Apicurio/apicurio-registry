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

import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.vertx.ext.web.RoutingContext;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomTenantConfigResolver implements TenantConfigResolver {

	@Override
	public OidcTenantConfig resolve(RoutingContext context) {

		//TODO Fetch tenantId either from url, header, or any other expected source

		//TODO set config according to fetched tenantId, if possible.

		OidcTenantConfig config = new OidcTenantConfig();

		//TODO any other setting support by the quarkus-oidc extension

		//TODO If we cannot find any tenant from the expected sources we can return null and the application will fall back to the default tenant configuration
		return null;
	}
}
