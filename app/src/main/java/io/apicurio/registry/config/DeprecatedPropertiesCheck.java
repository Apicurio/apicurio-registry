package io.apicurio.registry.config;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks for deprecated and removed configuration properties at startup.
 * Follows a warning-first two-phase deprecation lifecycle:
 * <ul>
 *   <li>Phase 1 (Deprecated): Produces a WARN log at startup informing users of the deprecation,
 *       the replacement property name, and the version where removal will happen. Both old and
 *       new properties work, but if both are set, the new property takes precedence with a warning.</li>
 *   <li>Phase 2 (Removed): Produces an ERROR log and hard-fails application startup with a clear
 *       {@link IllegalStateException} if a removed property is still configured.</li>
 * </ul>
 */
@Startup
@Singleton
public class DeprecatedPropertiesCheck {

    private static final String VERSION_3_1_0 = "3.1.0";
    private static final String VERSION_3_3_0 = "3.3.0";
    private static final String VERSION_4_0_0 = "4.0.0";

    public static class DeprecatedPropertyDef {
        private final String oldName;
        private final String replacementName;
        private final String deprecatedSince;
        private final String removeInVersion;
        private final boolean removed;
        private final String note;

        public DeprecatedPropertyDef(String oldName, String replacementName, String deprecatedSince, String removeInVersion, boolean removed) {
            this(oldName, replacementName, deprecatedSince, removeInVersion, removed, null);
        }

        /**
         * @param note added to the deprecation warning when there is no replacement, to say what
         *             becomes of the property in the meantime, and to the error for a removed
         *             property, to say what to do instead; may be {@code null}
         */
        public DeprecatedPropertyDef(String oldName, String replacementName, String deprecatedSince, String removeInVersion, boolean removed,
                String note) {
            this.oldName = oldName;
            this.replacementName = replacementName;
            this.deprecatedSince = deprecatedSince;
            this.removeInVersion = removeInVersion;
            this.removed = removed;
            this.note = note;
        }

        public String getNote() {
            return note;
        }

        public String getOldName() {
            return oldName;
        }

        public String getReplacementName() {
            return replacementName;
        }

        public String getDeprecatedSince() {
            return deprecatedSince;
        }

        public String getRemoveInVersion() {
            return removeInVersion;
        }

        public boolean isRemoved() {
            return removed;
        }
    }

    @Inject
    Logger log;

    @Inject
    Config config;

    private final List<DeprecatedPropertyDef> registry;

    @Inject
    public DeprecatedPropertiesCheck() {
        this(List.of(
                new DeprecatedPropertyDef("apicurio.compat.json-schema.use-apitomy", null, "3.4.0", VERSION_4_0_0, false,
                        "It no longer has any effect: JSON Schema compatibility is always checked with Apitomy Data Models,"
                                + " because the legacy everit-based checker was removed."),
                new DeprecatedPropertyDef("apicurio.usage.dead-threshold-days", null, "3.4.0", VERSION_4_0_0, false,
                        "It has no effect: a schema version whose last fetch is older than the stale threshold"
                                + " (apicurio.usage.stale-threshold-days) is classified as DEAD."),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.truststore.password", "apicurio.kafkasql.security.ssl.truststore.password", VERSION_3_1_0, VERSION_4_0_0, false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.keystore.location", "apicurio.kafkasql.security.ssl.keystore.location", VERSION_3_1_0, VERSION_4_0_0, false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.keystore.type", "apicurio.kafkasql.security.ssl.keystore.type", VERSION_3_1_0, VERSION_4_0_0, false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.keystore.password", "apicurio.kafkasql.security.ssl.keystore.password", VERSION_3_1_0, VERSION_4_0_0, false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.key.password", "apicurio.kafkasql.security.ssl.key.password", VERSION_3_1_0, VERSION_4_0_0, false),
                // Removed from GitOps and KubernetesOps storage in 3.3.0, while both were experimental,
                // without a deprecation period. They have had no effect since, so a 3.2 configuration
                // started without its registry ID and never became ready. Failing startup says why.
                new DeprecatedPropertyDef("apicurio.gitops.id", "apicurio.polling-storage.id", VERSION_3_3_0, VERSION_3_3_0, true),
                new DeprecatedPropertyDef("apicurio.kubernetesops.id", "apicurio.polling-storage.id", VERSION_3_3_0, VERSION_3_3_0, true),
                new DeprecatedPropertyDef("apicurio.gitops.repo.origin.branch", "apicurio.gitops.repo.branch", VERSION_3_3_0, VERSION_3_3_0, true),
                new DeprecatedPropertyDef("apicurio.gitops.refresh.every", "apicurio.polling-storage.poll-period", VERSION_3_3_0, VERSION_3_3_0, true,
                        "It takes an ISO-8601 duration, for example PT30S."),
                new DeprecatedPropertyDef("apicurio.gitops.repo.origin.uri", null, VERSION_3_3_0, VERSION_3_3_0, true,
                        "The registry no longer clones the repository: run the GitOps sync container and set its"
                                + " APICURIO_GITOPS_REPO_URL variable."),
                new DeprecatedPropertyDef("apicurio.gitops.workdir", null, VERSION_3_3_0, VERSION_3_3_0, true,
                        "The registry reads repositories from apicurio.gitops.workspace, where the GitOps sync"
                                + " container keeps them.")
        ));
    }

    public DeprecatedPropertiesCheck(List<DeprecatedPropertyDef> customRegistry) {
        this.registry = List.copyOf(customRegistry);
    }

    public List<DeprecatedPropertyDef> getRegistry() {
        return registry;
    }

    private boolean isConfigured(String propertyName) {
        if (propertyName == null) {
            return false;
        }
        Optional<String> val = config.getOptionalValue(propertyName, String.class);
        return val.isPresent() && !val.get().isBlank();
    }

    @PostConstruct
    public void validate() {
        List<String> violations = new ArrayList<>();

        for (DeprecatedPropertyDef def : registry) {
            boolean isOldPresent = isConfigured(def.getOldName());
            boolean isReplacementPresent = isConfigured(def.getReplacementName());

            if (isOldPresent) {
                if (def.isRemoved()) {
                    reportRemoved(def, violations);
                } else {
                    warnDeprecated(def, isReplacementPresent);
                }
            }
        }

        if (!violations.isEmpty()) {
            String combinedViolations = String.join(", ", violations);
            throw new IllegalStateException("The following configuration properties were removed and are no longer supported: " + combinedViolations);
        }
    }

    private void reportRemoved(DeprecatedPropertyDef def, List<String> violations) {
        if (def.getNote() != null) {
            String message = def.getReplacementName() != null
                    ? String.format("Property '%s' was removed in %s. Use '%s' instead. %s",
                            def.getOldName(), def.getRemoveInVersion(), def.getReplacementName(), def.getNote())
                    : String.format("Property '%s' was removed in %s. %s",
                            def.getOldName(), def.getRemoveInVersion(), def.getNote());
            log.error("{}", message);
            violations.add(message);
        } else if (def.getReplacementName() != null) {
            log.error("Property '{}' was removed in {}. Use '{}' instead.",
                    def.getOldName(), def.getRemoveInVersion(), def.getReplacementName());
            violations.add(String.format("Property '%s' was removed in %s. Use '%s' instead.",
                    def.getOldName(), def.getRemoveInVersion(), def.getReplacementName()));
        } else {
            log.error("Property '{}' was removed in {}.", def.getOldName(), def.getRemoveInVersion());
            violations.add(String.format("Property '%s' was removed in %s.",
                    def.getOldName(), def.getRemoveInVersion()));
        }
    }

    private void warnDeprecated(DeprecatedPropertyDef def, boolean isReplacementPresent) {
        if (isReplacementPresent) {
            log.warn("Both '{}' (deprecated since {}) and '{}' are configured. '{}' will take precedence.",
                    def.getOldName(), def.getDeprecatedSince(), def.getReplacementName(), def.getReplacementName());
        } else if (def.getReplacementName() != null) {
            log.warn("Property '{}' is deprecated since {} and will be removed in {}. Use '{}' instead.",
                    def.getOldName(), def.getDeprecatedSince(), def.getRemoveInVersion(), def.getReplacementName());
        } else {
            log.warn("Property '{}' is deprecated since {} and will be removed in {}.{}",
                    def.getOldName(), def.getDeprecatedSince(), def.getRemoveInVersion(),
                    def.getNote() != null ? " " + def.getNote() : "");
        }
    }
}
