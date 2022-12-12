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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.mt.limits.TenantLimitsConfiguration;
import io.apicurio.registry.mt.limits.TenantLimitsConfigurationService;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Component responsible for creating instances of {@link RegistryTenantContext} so they can be set with {@link TenantContext}
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantContextLoader {

    //NOTE for now we are just storing per tenant configurations in the context, this allows us to cache the instances
    //but if in the future we store session scoped or request scoped information the caching strategy should change
    private LoadingCache<String, RegistryTenantContext> contextsCache;
    private RegistryTenantContext defaultTenantContext;

    @Inject
    TenantMetadataService tenantMetadataService;

    @Inject
    TenantLimitsConfigurationService limitsConfigurationService;

    @Inject
    Logger log;

    @Inject
    @ConfigProperty(defaultValue = "60000", name = "registry.tenants.context.cache.check-period")
    @Info(category = "mt", description = "Tenants context cache check period", availableSince = "2.1.0.Final")
    Long cacheCheckPeriod;

    public void onStart(@Observes StartupEvent ev) {

        CacheLoader<String, RegistryTenantContext> cacheLoader = new CacheLoader<>() {
            @Override
            public RegistryTenantContext load(@NotNull String tenantId) {
                ApicurioTenant tenantMetadata = tenantMetadataService.getTenant(tenantId);
                TenantLimitsConfiguration limitsConfiguration = limitsConfigurationService.fromTenantMetadata(tenantMetadata);
                return new RegistryTenantContext(tenantId, tenantMetadata.getCreatedBy(), limitsConfiguration, tenantMetadata.getStatus(), String.valueOf(tenantMetadata.getOrganizationId()));
            }
        };

        contextsCache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(cacheCheckPeriod, TimeUnit.MILLISECONDS)
                .maximumSize(1000)
                .build(cacheLoader);
    }

    /**
     * Used for internal stuff where there isn't a JWT token from the user request available
     * This won't perform any authorization check.
     *
     * @param tenantId
     * @return
     */
    public RegistryTenantContext loadBatchJobContext(String tenantId) {
        return loadRequestContext(tenantId);
    }

    /**
     * Loads the tenant context from the cache or computes it
     *
     * @param tenantId
     */
    public RegistryTenantContext loadRequestContext(String tenantId) {
        if (tenantId.equals(TenantContext.DEFAULT_TENANT_ID)) {
            return defaultTenantContext();
        }

        try {
            return contextsCache.get(tenantId);
        } catch (ExecutionException | UncheckedExecutionException e) {
            if (e.getCause() instanceof TenantNotFoundException) {
                throw (TenantNotFoundException) e.getCause();
            } else {
                log.warn("Error trying to load the tenant context for tenant id: {}. Falling back to default tenant context.", tenantId, e);
                return defaultTenantContext();
            }
        }
    }

    public RegistryTenantContext defaultTenantContext() {
        if (defaultTenantContext == null) {
            defaultTenantContext = new RegistryTenantContext(TenantContext.DEFAULT_TENANT_ID, null, limitsConfigurationService.defaultConfigurationTenant(), TenantStatusValue.READY, null);
        }
        return defaultTenantContext;
    }

    public void invalidateTenantInCache(String tenantId) {
        contextsCache.invalidate(tenantId);
    }

    public void invalidateTenantCache() {
        contextsCache.invalidateAll();
    }
}
