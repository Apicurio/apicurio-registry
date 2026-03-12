package io.apicurio.registry.storage.impl.sql;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

/**
 * Configuration for database connection retry behavior.
 * Enables resilience against transient connection failures such as DNS resolution issues
 * during application startup or network disruptions.
 */
@ApplicationScoped
public class ConnectionRetryConfig {

    @ConfigProperty(name = "apicurio.storage.connection.retry.enabled", defaultValue = "true")
    @Info(category = CATEGORY_STORAGE, description = "Enable connection retry with exponential backoff for transient database connection failures", registryAvailableSince = "3.1.4")
    @Getter
    boolean enabled;

    @ConfigProperty(name = "apicurio.storage.connection.retry.max-attempts", defaultValue = "10")
    @Info(category = CATEGORY_STORAGE, description = "Maximum number of connection retry attempts", registryAvailableSince = "3.1.4")
    @Getter
    int maxAttempts;

    @ConfigProperty(name = "apicurio.storage.connection.retry.initial-delay-ms", defaultValue = "1000")
    @Info(category = CATEGORY_STORAGE, description = "Initial delay in milliseconds before first retry", registryAvailableSince = "3.1.4")
    @Getter
    long initialDelayMs;

    @ConfigProperty(name = "apicurio.storage.connection.retry.max-delay-ms", defaultValue = "30000")
    @Info(category = CATEGORY_STORAGE, description = "Maximum delay in milliseconds between retry attempts", registryAvailableSince = "3.1.4")
    @Getter
    long maxDelayMs;

    @ConfigProperty(name = "apicurio.storage.connection.retry.backoff-multiplier", defaultValue = "2.0")
    @Info(category = CATEGORY_STORAGE, description = "Multiplier for exponential backoff between retry attempts", registryAvailableSince = "3.1.4")
    @Getter
    double backoffMultiplier;
}
