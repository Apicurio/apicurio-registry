/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public static class DeprecatedPropertyDef {
        private final String oldName;
        private final String replacementName;
        private final String deprecatedSince;
        private final String removeInVersion;
        private final boolean removed;

        public DeprecatedPropertyDef(String oldName, String replacementName, String deprecatedSince, String removeInVersion, boolean removed) {
            this.oldName = oldName;
            this.replacementName = replacementName;
            this.deprecatedSince = deprecatedSince;
            this.removeInVersion = removeInVersion;
            this.removed = removed;
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
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.truststore.password", "apicurio.kafkasql.security.ssl.truststore.password", "3.1.0", "4.0.0", false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.keystore.location", "apicurio.kafkasql.security.ssl.keystore.location", "3.1.0", "4.0.0", false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.keystore.type", "apicurio.kafkasql.security.ssl.keystore.type", "3.1.0", "4.0.0", false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.keystore.password", "apicurio.kafkasql.security.ssl.keystore.password", "3.1.0", "4.0.0", false),
                new DeprecatedPropertyDef("apicurio.kafkasql.ssl.key.password", "apicurio.kafkasql.security.ssl.key.password", "3.1.0", "4.0.0", false)
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
                    String msg = def.getReplacementName() != null
                            ? String.format("Property '%s' was removed in %s. Use '%s' instead.",
                                    def.getOldName(), def.getRemoveInVersion(), def.getReplacementName())
                            : String.format("Property '%s' was removed in %s.",
                                    def.getOldName(), def.getRemoveInVersion());
                    log.error(msg);
                    violations.add(msg);
                } else {
                    if (isReplacementPresent) {
                        log.warn("Both '{}' (deprecated since {}) and '{}' are configured. '{}' will take precedence.",
                                def.getOldName(), def.getDeprecatedSince(), def.getReplacementName(), def.getReplacementName());
                    } else {
                        log.warn("Property '{}' is deprecated since {} and will be removed in {}. Use '{}' instead.",
                                def.getOldName(), def.getDeprecatedSince(), def.getRemoveInVersion(), def.getReplacementName());
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            String combinedViolations = String.join(", ", violations);
            throw new IllegalStateException("The following configuration properties were removed and are no longer supported: " + combinedViolations);
        }
    }
}
