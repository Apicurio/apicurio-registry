package io.apicurio.registry.config;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorBase;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorOrderConstants;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class RegistryStorageConfigCache extends RegistryStorageDecoratorBase implements RegistryStorageDecorator {

    private static final DynamicConfigPropertyDto NULL_DTO = new DynamicConfigPropertyDto();

    @Inject
    Logger log;

    @ConfigProperty(name = "apicurio.config.cache.enabled", defaultValue = "true")
    @Info(category = "cache", description = "Registry cache enabled", availableSince = "2.2.2.Final")
    boolean enabled;

    private Map<String, DynamicConfigPropertyDto> configCache = new ConcurrentHashMap<>();
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
        return RegistryStorageDecoratorOrderConstants.CONFIG_CACHE_DECORATOR;
    }


    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#setConfigProperty(io.apicurio.common.apps.config.DynamicConfigPropertyDto)
     */
    @Override
    public void setConfigProperty(DynamicConfigPropertyDto property) throws RegistryStorageException {
        super.setConfigProperty(property);
        invalidateCache();
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#getConfigProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        DynamicConfigPropertyDto propertyDto = configCache.computeIfAbsent(propertyName, (key) -> {
            DynamicConfigPropertyDto dto = super.getConfigProperty(key);
            if (dto == null) {
                dto = NULL_DTO;
            }
            return dto;
        });
        return propertyDto == NULL_DTO ? null : propertyDto;
    }

    private void invalidateCache() {
        configCache.clear();
    }

    @Scheduled(concurrentExecution = SKIP, every = "{apicurio.config.refresh.every}")
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
            List<DynamicConfigPropertyDto> staleConfigProperties = this.getStaleConfigProperties(lastRefresh);
            if (!staleConfigProperties.isEmpty()) {
                invalidateCache();
            }
        }
        lastRefresh = now;
    }
}
