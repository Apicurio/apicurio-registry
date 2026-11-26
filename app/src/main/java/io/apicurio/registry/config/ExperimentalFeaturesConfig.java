package io.apicurio.registry.config;

import io.apicurio.common.apps.config.ExperimentalConfigPropertyDef;
import io.apicurio.common.apps.config.ExperimentalConfigPropertyList;
import io.apicurio.common.apps.config.Info;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
// Run the gate before default-priority @Startup beans (2500) can initialize storage.
@Startup(Interceptor.Priority.PLATFORM_BEFORE)
public class ExperimentalFeaturesConfig {

    // A value that is exactly one property reference, with an optional default: ${name} or ${name:default}
    private static final Pattern SINGLE_REFERENCE = Pattern.compile("\\$\\{([^:}$]+)(?::[^}$]*)?}");

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

        final List<String> violations = new ArrayList<>();

        // Auto-check all boolean experimental toggle properties discovered at build time
        final List<ExperimentalConfigPropertyDef> experimental = experimentalProperties.getExperimentalConfigProperties();
        for (final ExperimentalConfigPropertyDef prop : experimental) {
            final boolean value = config.getOptionalValue(prop.getName(), Boolean.class).orElse(false);
            if (value && !followsReportedToggle(prop.getName(), experimental)) {
                violations.add(prop.getName() + " (" + prop.getDescription() + ")");
            }
        }

        // Special checks for non-boolean experimental features (e.g., storage variants)
        final String storageKind = config.getOptionalValue("apicurio.storage.kind", String.class).orElse("sql");
        if ("gitops".equals(storageKind)) {
            violations.add("apicurio.storage.kind=gitops (GitOps storage)");
        }
        if ("kubernetesops".equals(storageKind)) {
            violations.add("apicurio.storage.kind=kubernetesops (KubernetesOps storage)");
        }
        final String signatureMode = config.getOptionalValue("apicurio.a2a.signature-verification.mode", String.class)
                .orElse("off").trim();
        if (!"off".equalsIgnoreCase(signatureMode)) {
            violations.add("apicurio.a2a.signature-verification.mode=" + signatureMode
                    + " (A2A Agent Card signature policy)");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "The following experimental features are enabled but the experimental features gate "
                            + "('apicurio.features.experimental.enabled') is not enabled. Either enable the "
                            + "experimental features gate or disable the following properties: "
                            + String.join(", ", violations));
        }
    }

    /**
     * Whether the property's value is nothing but a reference to another experimental toggle that is
     * itself enabled, such as {@code apicurio.a2a.enabled=${apicurio.agents.enabled:false}} with the
     * umbrella on. That toggle is reported instead, so the error names what was actually configured.
     * Any other expression, for example one reading an environment variable, is reported as is.
     */
    private boolean followsReportedToggle(String name, List<ExperimentalConfigPropertyDef> experimental) {
        final ConfigValue value = config.getConfigValue(name);
        final String raw = value == null ? null : value.getRawValue();
        if (raw == null) {
            return false;
        }
        final Matcher reference = SINGLE_REFERENCE.matcher(raw.trim());
        if (!reference.matches()) {
            return false;
        }
        final String referenced = reference.group(1);
        return !referenced.equals(name)
                && experimental.stream().anyMatch(prop -> prop.getName().equals(referenced))
                && config.getOptionalValue(referenced, Boolean.class).orElse(false);
    }

    public boolean isExperimentalFeaturesEnabled() {
        return experimentalFeaturesEnabled;
    }

    /**
     * Whether the given property can be set to {@code true}: always when the gate is enabled, otherwise only
     * if the property is not an experimental feature toggle.
     */
    public boolean canEnable(String propertyName) {
        return experimentalFeaturesEnabled || experimentalProperties.getExperimentalConfigProperties().stream()
                .noneMatch(prop -> prop.getName().equals(propertyName));
    }
}
