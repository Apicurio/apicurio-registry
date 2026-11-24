package io.apicurio.registry.config;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_SYSTEM;

/**
 * How this registry instance names itself to discovery clients. The A2A Agent Card and the AI
 * Catalog both use it unless their own property is set (see {@code application.properties}).
 */
@Singleton
public class InstanceConfig {

    public static final String DEFAULT_NAME = "Apicurio Registry";

    // No defaultValue: it must be possible to tell a configured name from the default, because a
    // configured name takes precedence over the deprecated per-document names.
    @ConfigProperty(name = "apicurio.instance.name")
    @Info(category = CATEGORY_SYSTEM, description = "Display name of this registry instance in discovery documents, such as the A2A Agent Card and the AI Catalog. Defaults to Apicurio Registry. Replaces apicurio.a2a.agent.name and apicurio.ai-catalog.host-name, and takes precedence over them", availableSince = "3.4.0")
    Optional<String> name;

    /**
     * @param legacy the value of a deprecated per-document name property, if set
     * @return the configured instance name, else the legacy value, else the default
     */
    public String nameOr(Optional<String> legacy) {
        return name.or(() -> legacy).orElse(DEFAULT_NAME);
    }
}
