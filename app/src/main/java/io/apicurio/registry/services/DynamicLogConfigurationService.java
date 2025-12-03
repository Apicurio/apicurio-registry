package io.apicurio.registry.services;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.function.Supplier;
import java.util.logging.Level;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_LOG;

/**
 * Service responsible for applying dynamic log level configuration at runtime.
 * Monitors the dynamic configuration for changes to the apicurio.log.level property
 * and applies them to the io.apicurio logger category.
 */
@ApplicationScoped
public class DynamicLogConfigurationService {

    private static final String APICURIO_LOGGER_NAME = "io.apicurio";

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Dynamic(label = "Log level", description = "Set the log level for Apicurio Registry. Valid values: TRACE, DEBUG, INFO, WARN, ERROR, OFF, ALL")
    @ConfigProperty(name = "apicurio.log.level", defaultValue = "WARN")
    @Info(category = CATEGORY_LOG, description = "Dynamic log level for Apicurio Registry", availableSince = "3.1.0")
    Supplier<String> logLevel;

    private String currentAppliedLevel = null;

    /**
     * Applies the initial log level configuration at application startup.
     * This ensures that the configured log level is applied early enough to capture
     * initialization and startup log messages.
     *
     * @param event the startup event
     */
    void onStartup(@Observes StartupEvent event) {
        try {
            String targetLevel = logLevel.get();
            applyLogLevel(targetLevel);
            currentAppliedLevel = targetLevel;
            log.info("Applied initial log level configuration at startup: {} = {}", APICURIO_LOGGER_NAME, targetLevel);
        } catch (Exception ex) {
            log.error("Exception thrown when applying initial log level configuration", ex);
        }
    }

    /**
     * Scheduled job that checks for dynamic log level configuration changes
     * and applies them to the Apicurio logger category.
     * Runs at the interval specified by apicurio.logconfigjob.every property.
     */
    @Scheduled(concurrentExecution = ConcurrentExecution.SKIP, delayed = "{apicurio.logconfigjob.delayed}", every = "{apicurio.logconfigjob.every}")
    public void applyDynamicLogLevel() {
        if (!storage.isAlive() || !storage.isReady()) {
            return;
        }

        try {
            log.trace("Running periodic dynamic log configuration check");

            String targetLevel = logLevel.get();

            // Only update if the level has changed
            if (!targetLevel.equals(currentAppliedLevel)) {
                applyLogLevel(targetLevel);
                currentAppliedLevel = targetLevel;
                log.info("Applied dynamic log level configuration: {} = {}", APICURIO_LOGGER_NAME, targetLevel);
            }
        } catch (Exception ex) {
            log.error("Exception thrown when applying dynamic log level configuration", ex);
        }
    }

    /**
     * Applies the specified log level to the Apicurio logger.
     *
     * @param logLevel the log level to apply (e.g., "DEBUG", "INFO", "WARN", "ERROR")
     */
    private void applyLogLevel(String logLevel) {
        try {
            Level level = Level.parse(logLevel);
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(APICURIO_LOGGER_NAME);
            logger.setLevel(level);
            log.debug("Set log level for {} to {}", APICURIO_LOGGER_NAME, logLevel);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid log level value: {}. Must be one of: TRACE, DEBUG, INFO, WARN, ERROR, OFF, ALL", logLevel, ex);
        }
    }
}
