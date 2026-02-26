package io.apicurio.registry.config;

import io.apicurio.common.apps.config.Info;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_SYSTEM;

/**
 * Centralized configuration for the global experimental features gate. When disabled (default), any
 * experimental feature that is individually enabled will cause the application to fail at startup with a
 * clear error message.
 */
@Singleton
public class ExperimentalFeaturesConfig {

    @Inject
    Logger log;

    @Inject
    Config config;

    @ConfigProperty(name = "apicurio.features.experimental.enabled", defaultValue = "false")
    @Info(category = CATEGORY_SYSTEM, description = "Enable experimental features. When disabled, any experimental feature that is individually enabled will prevent the application from starting.", availableSince = "3.2.0")
    boolean experimentalFeaturesEnabled;

    @PostConstruct
    void validate() {
        if (experimentalFeaturesEnabled) {
            log.info("Experimental features gate is enabled.");
            return;
        }

        List<String> violations = new ArrayList<>();

        // Check known experimental boolean toggle properties.
        // Each experimental feature must have a single boolean toggle marked @Info(experimental=true).
        checkBooleanToggle("apicurio.a2a.enabled", "A2A protocol support", violations);
        checkBooleanToggle("apicurio.ui.features.agents.enabled", "UI Agents tab", violations);

        // Check experimental storage variant (gitops)
        String storageKind = config.getOptionalValue("apicurio.storage.kind", String.class).orElse("sql");
        if ("gitops".equals(storageKind)) {
            violations.add("apicurio.storage.kind=gitops (GitOps storage)");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "The following experimental features are enabled but the experimental features gate "
                            + "('apicurio.features.experimental.enabled') is not enabled. Either enable the "
                            + "experimental features gate or disable the following properties: "
                            + String.join(", ", violations));
        }
    }

    private void checkBooleanToggle(String propertyName, String description, List<String> violations) {
        boolean value = config.getOptionalValue(propertyName, Boolean.class).orElse(false);
        if (value) {
            violations.add(propertyName + " (" + description + ")");
        }
    }

    public boolean isExperimentalFeaturesEnabled() {
        return experimentalFeaturesEnabled;
    }
}
