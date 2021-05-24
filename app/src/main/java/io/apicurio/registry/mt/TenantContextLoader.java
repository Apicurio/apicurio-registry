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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.mt.limits.TenantLimitsConfigurationService;
import io.apicurio.registry.utils.CheckPeriodCache;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;

/**
 * Component responsible for creating instances of {@link RegistryTenantContext} so they can be set with {@link TenantContext}
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantContextLoader {

    //NOTE for now we are just storing per tenant configurations in the context, this allows us to cache the instances
    //but if in the future we store session scoped or request scoped information the caching strategy should change
    private CheckPeriodCache<String, RegistryTenantContext> contextsCache;

    private RegistryTenantContext defaultTenantContext;

    @Inject
    @ConfigProperty(defaultValue = "60000", name = "registry.tenants.context.cache.check-period")
    Long cacheCheckPeriod;

    @Inject
    TenantMetadataService tenantMetadataService;

    @Inject
    TenantLimitsConfigurationService limitsConfigurationService;

    @Inject
    Instance<JsonWebToken> jsonWebToken;

    @ConfigProperty(name = "registry.organization-id.claim-name")
    String organizationIdClaimName;

    public void onStart(@Observes StartupEvent ev) {
        contextsCache = new CheckPeriodCache<>(cacheCheckPeriod);
    }

    public RegistryTenantContext loadContext(String tenantId) {

        checkTenantAuthorization(tenantId);

        RegistryTenantContext context = contextsCache.compute(tenantId, k -> {
            return new RegistryTenantContext(tenantId, limitsConfigurationService.defaultConfigurationTenant());
          //TODO uncomment when tenant-manager is updated
//            RegistryTenantContext ctx;
//            if (tenantId.equals(TenantContext.DEFAULT_TENANT_ID)) {
//                if (defaultTenantContext == null) {
//                    defaultTenantContext = new RegistryTenantContext(tenantId, limitsConfigurationService.defaultConfigurationTenant());
//                }
//                ctx = defaultTenantContext;
//            } else {
//                RegistryTenant tenantMetadata = tenantMetadataService.getTenant(tenantId);
//                TenantLimitsConfiguration limitsConfiguration = limitsConfigurationService.fromTenantMetadata(tenantMetadata);
//                ctx = new RegistryTenantContext(tenantId, limitsConfiguration);
//            }
//            return ctx;
        });
        return context;
    }

    public RegistryTenantContext defaultTenantContext() {
        if (defaultTenantContext == null) {
            defaultTenantContext = new RegistryTenantContext(TenantContext.DEFAULT_TENANT_ID, limitsConfigurationService.defaultConfigurationTenant());
        }
        return defaultTenantContext;
    }

    private void checkTenantAuthorization(String tenantId) {
        if (jsonWebToken.isResolvable()) {
            final RegistryTenant tenant = tenantMetadataService.getTenant(tenantId);
            final Optional<Object> accessedOrganizationId = jsonWebToken.get().claim(organizationIdClaimName);

            if (accessedOrganizationId.isPresent() && !tenantCanAccessOrganization(tenant, (String) accessedOrganizationId.get())) {
                throw new TenantNotAuthorizedException(String.format("Tenant %s not authorized to access organization %s", tenantId, accessedOrganizationId));
            }
        }
    }

    private boolean tenantCanAccessOrganization(RegistryTenant tenant, String accessedOrganizationId) {
        return tenant == null || accessedOrganizationId.equals(tenant.getOrganizationId());
    }
}
