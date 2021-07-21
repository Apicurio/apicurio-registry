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

package io.apicurio.registry.mt;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class OidcConfigResolver implements TenantConfigResolver {

    @Inject
    Logger log;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    TenantMetadataService tenantMetadataService;

    String multitenancyBasePath;

    private static final int TENANT_ID_POSITION = 2;

    void init(@Observes StartupEvent ev) {
        if (mtProperties.isMultitenancyEnabled()) {
            log.info("Registry running with multitenancy enabled");
        }
        multitenancyBasePath = "/" + mtProperties.getNameMultitenancyBasePath() + "/";
    }

    @Override
    public OidcTenantConfig resolve(RoutingContext context) {
        if (!mtProperties.isMultitenancyEnabled()) {
            log.debug("Multitenancy is disabled, falling back to default oidc config");
            return null;
        } else {
            final String uri = context.request().uri();

            if (uri.startsWith(multitenancyBasePath)) {
                String[] tokens = uri.split("/");
                // 0 is empty
                // 1 is t
                // 2 is the tenantId
                String tenantId = tokens[TENANT_ID_POSITION];
                final RegistryTenant tenant = tenantMetadataService.getTenant(tenantId);
                final OidcTenantConfig tenantOidcConfig = new OidcTenantConfig();
                tenantOidcConfig.setTenantId(tenant.getTenantId());
                tenantOidcConfig.setAuthServerUrl(tenant.getAuthServerUrl());
                tenantOidcConfig.setClientId(tenant.getAuthClientId());

                return tenantOidcConfig;
            }
        }
        log.debug("Multitenantcy is disabled, falling back to default oidc config");
        return null;
    }
}
