package io.apicurio.registry.rest;

import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.registry.config.ExperimentalFeaturesConfig;
import io.apicurio.registry.services.LogLevelValidator;
import io.apicurio.registry.storage.error.InvalidPropertyValueException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

/**
 * Validates a new value for a dynamic configuration property. Shared by the v2 and v3 admin APIs so that
 * both apply the same checks.
 */
@ApplicationScoped
public class DynamicConfigPropertyValidator {

    @Inject
    ExperimentalFeaturesConfig experimentalFeatures;

    @Inject
    LogLevelValidator logLevelValidator;

    @Inject
    Config config;

    /**
     * Ensure that the value is valid for the property type (or the log level property), and that an
     * experimental feature is not enabled while experimental features are disabled.
     *
     * @param propertyDef the dynamic config property definition
     * @param value the config property value
     */
    public void validate(DynamicConfigPropertyDef propertyDef, String value) {
        if ("apicurio.log.level".equals(propertyDef.getName())) {
            if (!logLevelValidator.isValidLogLevel(value)) {
                throw new InvalidPropertyValueException(
                        "Invalid log level value: " + value + ". Must be one of: TRACE, DEBUG, INFO, WARN, ERROR, OFF, ALL");
            }
        } else if (!propertyDef.isValidValue(value)) {
            throw new InvalidPropertyValueException(
                    "Invalid dynamic configuration property value for: " + propertyDef.getName());
        }
        if (isTrue(value) && !experimentalFeatures.canEnable(propertyDef.getName())) {
            throw new ConflictException("'" + propertyDef.getName() + "' is an experimental feature. Enable "
                    + "'apicurio.features.experimental.enabled' to use it.");
        }
    }

    /**
     * Reads the value the same way the property itself is read back, through the MicroProfile Config
     * {@code Boolean} converter. It accepts more than the literal "true" (for example "1", "yes" and "on"), so
     * {@link Boolean#parseBoolean(String)} would let those values past the experimental feature gate.
     */
    private boolean isTrue(String value) {
        return config.getConverter(Boolean.class).map(converter -> converter.convert(value))
                .orElseGet(() -> Boolean.parseBoolean(value));
    }
}
