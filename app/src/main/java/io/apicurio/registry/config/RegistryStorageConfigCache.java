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
import java.util.concurrent.atomic.AtomicLong;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_CACHE;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class RegistryStorageConfigCache extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

    private static final DynamicConfigPropertyDto NULL_DTO = new DynamicConfigPropertyDto();

    @Inject
    Logger log;

    @ConfigProperty(name = "apicurio.config.cache.enabled", defaultValue = "true")
    @Info(category = CATEGORY_CACHE, description = "Registry cache enabled", availableSince = "2.2.2.Final")
    boolean enabled;

    private Map<String, DynamicConfigPropertyDto> configCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheGeneration = new AtomicLong();
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
    public void setConfigProperty(DynamicConfigPropertyDto property) throws RegistryStorageException {
        delegate.setConfigProperty(property);
        invalidateCache();
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#getConfigProperty(java.lang.String)
     */
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        DynamicConfigPropertyDto cached = configCache.get(propertyName);
        if (cached != null) {
            return cached == NULL_DTO ? null : cached;
        }
        // Load outside the map, so no bin is locked while storage is queried. The insert goes
        // through compute() because it takes the same bin that clear() needs: an invalidation
        // either bumps the generation before this check, or clears the entry after it. Checking
        // the generation and then putting separately would leave a window between the two.
        long generation = cacheGeneration.get();
        DynamicConfigPropertyDto loaded = delegate.getConfigProperty(propertyName);
        DynamicConfigPropertyDto toCache = loaded == null ? NULL_DTO : loaded;
        configCache.compute(propertyName, (key, existing) -> {
            if (existing != null) {
                return existing;
            }
            return cacheGeneration.get() == generation ? toCache : null;
        });
        return loaded;
    }

    private void invalidateCache() {
        cacheGeneration.incrementAndGet();
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
        if (lastRefresh != null && this.delegate != null && this.delegate.isReady()) {
            List<DynamicConfigPropertyDto> staleConfigProperties = this.delegate.getStaleConfigProperties(lastRefresh);
            if (!staleConfigProperties.isEmpty()) {
                invalidateCache();
            }
        }
        lastRefresh = now;
    }
}
