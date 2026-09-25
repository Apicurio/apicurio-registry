package io.apicurio.registry.semver;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_SEMVER;

@Singleton
public class SemVerConfigProperties {

    @Dynamic(label = "Ensure all version numbers are 'semver' compatible", description = "When enabled, validate that all artifact versions conform to Semantic Versioning 2 format (https://semver.org).")
    @ConfigProperty(name = "apicurio.semver.validation.enabled", defaultValue = "false")
    @Info(category = CATEGORY_SEMVER, description = "Validate that all artifact versions conform to Semantic Versioning 2 format (https://semver.org).", availableSince = "3.0.0")
    public Supplier<Boolean> validationEnabled;

    @Dynamic(label = "Automatically create semver branches", description = "When enabled, automatically create or update branches for major ('A.x') and minor ('A.B.x') artifact versions.")
    @ConfigProperty(name = "apicurio.semver.branching.enabled", defaultValue = "false")
    @Info(category = CATEGORY_SEMVER, description = "Automatically create or update branches for major ('A.x') and minor ('A.B.x') artifact versions.", availableSince = "3.0.0")
    public Supplier<Boolean> branchingEnabled;

    @Dynamic(label = "Coerce invalid semver versions", description = "When enabled and automatically creating semver branches, invalid versions will be coerced to Semantic Versioning 2 format (https://semver.org) if possible.", requires = "apicurio.semver.branching.enabled=true")
    @ConfigProperty(name = "apicurio.semver.branching.coerce", defaultValue = "false")
    @Info(category = CATEGORY_SEMVER, description = "If true, invalid versions will be coerced to Semantic Versioning 2 format (https://semver.org) if possible.", availableSince = "3.0.0")
    public Supplier<Boolean> coerceInvalidVersions;

}
