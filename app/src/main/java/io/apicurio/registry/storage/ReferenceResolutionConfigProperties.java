package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

@Singleton
public class ReferenceResolutionConfigProperties {

    public static final String MAX_DEPTH_PROPERTY = "apicurio.storage.references.max-depth";
    public static final String DEFAULT_MAX_DEPTH_VALUE = "100";
    public static final int DEFAULT_MAX_DEPTH = 100;

    @ConfigProperty(name = MAX_DEPTH_PROPERTY, defaultValue = DEFAULT_MAX_DEPTH_VALUE)
    @Info(category = CATEGORY_STORAGE, description = "Maximum recursion depth for resolving schema references. "
            + "Prevents stack overflow from deeply nested schemas.", availableSince = "3.0.6")
    public int maxDepth;
}
