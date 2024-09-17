package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

@Singleton
public class CCompatConfig {

    @Dynamic(label = "Legacy ID mode (compatibility API)", description = "When selected, the Schema Registry compatibility API uses global ID instead of content ID for artifact identifiers.")
    @ConfigProperty(name = "apicurio.ccompat.legacy-id-mode.enabled", defaultValue = "false")
    @Info(category = "ccompat", description = "Legacy ID mode (compatibility API)", availableSince = "2.0.2.Final")
    Supplier<Boolean> legacyIdModeEnabled;

    @Dynamic(label = "Canonical hash mode (compatibility API)", description = "When selected, the Schema Registry compatibility API uses the canonical hash instead of the regular hash of the content.")
    @ConfigProperty(name = "apicurio.ccompat.use-canonical-hash", defaultValue = "false")
    @Info(category = "ccompat", description = "Canonical hash mode (compatibility API)", availableSince = "2.3.0.Final")
    Supplier<Boolean> canonicalHashModeEnabled;

    @Dynamic(label = "Maximum number of Subjects returned (compatibility API)", description = "Determines the maximum number of Subjects that will be returned by the ccompat API (for the '/subjects' endpoint).")
    @ConfigProperty(name = "apicurio.ccompat.max-subjects", defaultValue = "1000")
    @Info(category = "ccompat", description = "Maximum number of Subjects returned (compatibility API)", availableSince = "2.4.2.Final")
    Supplier<Integer> maxSubjects;

    @ConfigProperty(name = "apicurio.ccompat.group-concat.enabled", defaultValue = "false")
    @Info(category = "ccompat", description = "Enable group support via concatenation in subject (compatibility API)", availableSince = "2.6.2.Final")
    boolean groupConcatEnabled;

    @ConfigProperty(name = "apicurio.ccompat.group-concat.separator", defaultValue = ":")
    @Info(category = "ccompat", description = "Separator to use when group concatenation is enabled (compatibility API)", availableSince = "2.6.2.Final")
    String groupConcatSeparator;

}
