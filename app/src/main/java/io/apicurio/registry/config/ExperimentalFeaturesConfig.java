package io.apicurio.registry.config;

import io.apicurio.common.apps.config.ExperimentalConfigPropertyDef;
import io.apicurio.common.apps.config.ExperimentalConfigPropertyList;
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
 *
 * <p>Boolean experimental toggle properties are auto-discovered at build time by scanning for
 * {@code @Info(experimental=true)} annotations on {@code @ConfigProperty} fields. Non-boolean experimental
 * features (e.g., GitOps storage variant) require special checks below.</p>
 */
@Singleton
public class ExperimentalFeaturesConfig {

    @Inject
    Logger log;

    @Inject
    Config config;

    @Inject
    ExperimentalConfigPropertyList experimentalProperties;

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

        // Auto-check all boolean experimental toggle properties discovered at build time
        for (ExperimentalConfigPropertyDef prop : experimentalProperties.getExperimentalConfigProperties()) {
            boolean value = config.getOptionalValue(prop.getName(), Boolean.class).orElse(false);
            if (value) {
                violations.add(prop.getName() + " (" + prop.getDescription() + ")");
            }
        }

        // Special checks for non-boolean experimental features (e.g., storage variants)
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

    public boolean isExperimentalFeaturesEnabled() {
        return experimentalFeaturesEnabled;
    }
}
