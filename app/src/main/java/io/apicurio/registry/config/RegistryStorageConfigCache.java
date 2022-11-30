/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.config;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.quarkus.scheduler.Scheduled;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RegistryStorageConfigCache extends RegistryStorageDecorator {

    private static final DynamicConfigPropertyDto NULL_DTO = new DynamicConfigPropertyDto();

    @Inject
    Logger log;

    @Inject
    TenantContext tenantContext;

    @ConfigProperty(name = "registry.config.cache.enabled", defaultValue = "true")
    @Info(category = "cache", description = "Registry cache enabled", availableSince = "2.2.2.Final")
    boolean enabled;

    private Map<String, Map<String, DynamicConfigPropertyDto>> configCache = new ConcurrentHashMap<>();
    private Instant lastRefresh = null;

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#order()
     */
    @Override
    public int order() {
        return 5;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#setConfigProperty(io.apicurio.common.apps.config.DynamicConfigPropertyDto)
     */
    @Override
    public void setConfigProperty(DynamicConfigPropertyDto property) throws RegistryStorageException {
        super.setConfigProperty(property);
        invalidateCache(tenantContext.tenantId());
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#getConfigProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        Map<String, DynamicConfigPropertyDto> tenantCache = getTenantCache();
        DynamicConfigPropertyDto propertyDto = tenantCache.computeIfAbsent(propertyName, (key) -> {
            DynamicConfigPropertyDto dto = super.getConfigProperty(key);
            if (dto == null) {
                dto = NULL_DTO;
            }
            return dto;
        });
        return propertyDto == NULL_DTO ? null : propertyDto;
    }

    /**
     * Gets a tenant-specific cache.
     */
    private Map<String, DynamicConfigPropertyDto> getTenantCache() {
        return configCache.computeIfAbsent(tenantContext.tenantId(), (tenantId) -> new ConcurrentHashMap<>());
    }

    private void invalidateCache(String tenantId) {
        configCache.remove(tenantId);
    }

    @Scheduled(concurrentExecution = SKIP, every = "{registry.config.refresh.every}")
    void run() {
        if (!enabled) {
            return;
        }

        try {
            log.debug("Running config property refresh job at {}", Instant.now());
            refresh();
        } catch (Exception ex) {
            log.error("Exception thrown when running config property refresh job.", ex);
        }
    }

    private void refresh() {
        Instant now = Instant.now();
        if (lastRefresh != null) {
            List<String> tenantIds = this.getTenantsWithStaleConfigProperties(lastRefresh);
            tenantIds.forEach(tenantId -> invalidateCache(tenantId));
        }
        lastRefresh = now;
    }
}
