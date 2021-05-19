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
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.registry.mt.limits.TenantLimitsConfiguration;
import io.apicurio.registry.mt.limits.TenantLimitsConfigurationService;
import io.apicurio.registry.utils.CheckPeriodCache;
import io.quarkus.runtime.StartupEvent;

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

    public void onStart(@Observes StartupEvent ev) {
        contextsCache = new CheckPeriodCache<>(cacheCheckPeriod);
    }

    public RegistryTenantContext loadContext(String tenantId) {
        if (tenantId.equals(TenantContext.DEFAULT_TENANT_ID)) {
            return defaultTenantContext();
        }
        RegistryTenantContext context = contextsCache.compute(tenantId, k -> {
//            return new RegistryTenantContext(tenantId, limitsConfigurationService.defaultConfigurationTenant());
          //TODO uncomment when tenant-manager is updated
            RegistryTenant tenantMetadata = tenantMetadataService.getTenant(tenantId);
            TenantLimitsConfiguration limitsConfiguration = limitsConfigurationService.fromTenantMetadata(tenantMetadata);
            return new RegistryTenantContext(tenantId, limitsConfiguration);
        });
        return context;
    }

    public RegistryTenantContext defaultTenantContext() {
        if (defaultTenantContext == null) {
            defaultTenantContext = new RegistryTenantContext(TenantContext.DEFAULT_TENANT_ID, limitsConfigurationService.defaultConfigurationTenant());
        }
        return defaultTenantContext;
    }

}
