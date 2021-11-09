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

package io.apicurio.registry.config;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;

import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ConfigPropertyDto;
import io.apicurio.registry.types.Current;
import io.quarkus.scheduler.Scheduled;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RegistryConfigServiceImpl implements RegistryConfigService {

    private static final Object CACHED_NULL_VALUE = new Object();

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    TenantContext tenantContext;

    private Map<RegistryConfigProperty, Object> globalPropertyCache = new HashMap<>();
    private Map<String, Map<RegistryConfigProperty, Object>> tenantPropertyCaches = new HashMap<>();
    private Instant lastRefresh = null;

    /**
     * @see io.apicurio.registry.config.RegistryConfigService#get(io.apicurio.registry.config.RegistryConfigProperty)
     */
    @Override
    public String get(RegistryConfigProperty property) {
        return get(property, String.class);
    }

    /**
     * @see io.apicurio.registry.config.RegistryConfigService#getOptional(io.apicurio.registry.config.RegistryConfigProperty)
     */
    @Override
    public Optional<String> getOptional(RegistryConfigProperty property) {
        return getOptional(property, String.class);
    }

    /**
     * @see io.apicurio.registry.config.RegistryConfigService#get(io.apicurio.registry.config.RegistryConfigProperty, java.lang.Class)
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(RegistryConfigProperty property, Class<T> propertyType) {
        // Possibly check the tenant property cache for a value.  If found return it.
        T rval = property.isEditable() ? (T) tenantPropertyCache().get(property) : null;

        // If not found, check for a global property.
        if (rval == null) {
            rval = (T) globalPropertyCache.computeIfAbsent(property, (key) -> {
                Optional<T> value = ConfigProvider.getConfig().getOptionalValue(property.propertyName(), propertyType);
                T newMappedValue = value.orElse((T) property.defaultValue());
                // Can't cache a null value, so convert to the cached null value.
                if (newMappedValue == null) {
                    newMappedValue = (T) CACHED_NULL_VALUE;
                }
                return newMappedValue;
            });
        }

        // Revert the cached null value to actual null.
        if (rval == CACHED_NULL_VALUE) {
            rval = null;
        }

        return rval;
    }

    /**
     * @see io.apicurio.registry.config.RegistryConfigService#getOptional(io.apicurio.registry.config.RegistryConfigProperty, java.lang.Class)
     */
    @Override
    public <T> Optional<T> getOptional(RegistryConfigProperty property, Class<T> propertyType) {
        T rval = get(property, propertyType);
        return Optional.ofNullable(rval);
    }

    public <T> T set(RegistryConfigProperty property, T newValue) {
        ConfigPropertyDto propertyDto = ConfigPropertyDto.create(property.propertyName(), newValue);
        storage.setConfigProperty(propertyDto);
        return newValue;
    }

    private Map<RegistryConfigProperty, Object> tenantPropertyCache() {
        String tenantId = tenantContext.tenantId();
        return tenantPropertyCaches.computeIfAbsent(tenantId, key -> {
            Map<String, Object> tenantProperties = loadTenantProperties();
            Map<RegistryConfigProperty, Object> cache = new HashMap<>();
            tenantProperties.forEach((k,v) -> {
                RegistryConfigProperty property = RegistryConfigProperty.fromPropertyName(k);
                if (property != null) {
                    cache.put(property, v);
                }
            });
            return cache;
        });
    }

    private Map<String, Object> loadTenantProperties() {
        List<ConfigPropertyDto> configProperties = storage.getConfigProperties();
        Map<String, Object> rval = new HashMap<>();
        configProperties.forEach(dto -> {
            rval.put(dto.getName(), convertValue(dto));
        });
        return rval;
    }

    /**
     * Scheduled job to reload configuration properties that might have been changed.
     */
    @Scheduled(concurrentExecution = SKIP, every = "{registry.config.refresh.every}")
    void run() {
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
            List<String> tenantIds = storage.getTenantsWithStaleConfigProperties(lastRefresh);
            tenantIds.forEach(tenantId -> invalidateTenantCache(tenantId));
        }
        lastRefresh = now;
    }

    /**
     * @param tenantId
     */
    private void invalidateTenantCache(String tenantId) {
        tenantPropertyCaches.remove(tenantId);
    }

    private static Object convertValue(ConfigPropertyDto property) {
        String name = property.getName();
        String type = property.getType();
        String value = property.getValue();

        if (value == null) {
            return null;
        }

        if ("java.lang.String".equals(type)) {
            return value;
        }
        if ("java.lang.Boolean".equals(type)) {
            return "true".equals(value);
        }
        if ("java.lang.Integer".equals(type)) {
            return Integer.valueOf(value);
        }
        if ("java.lang.Long".equals(type)) {
            return Long.valueOf(value);
        }
        throw new UnsupportedOperationException("Configuration property type not supported: " + type + " for property with name: " + name);
    }
}
