package io.apicurio.registry.types.provider;

import java.util.Map;

/**
 * Service-provider interface that lets an optional module contribute built-in artifact types to
 * {@link StandardArtifactTypeProviderRegistry} without the registry depending on that module.
 * <p>
 * Implementations are discovered with {@link java.util.ServiceLoader}, so a module registers one by
 * listing its implementation class in
 * {@code META-INF/services/io.apicurio.registry.types.provider.ArtifactTypeProviderContributor}.
 * If the module's jar is not on the classpath, its artifact types are simply not available.
 * </p>
 */
public interface ArtifactTypeProviderContributor {

    /**
     * Returns the provider configurations contributed by this module, keyed by artifact type.
     * <p>
     * Each artifact type must be contributed exactly once across the core registry and all
     * contributors; a duplicate is treated as a configuration error.
     * </p>
     *
     * @return the contributed provider configurations, keyed by artifact type
     */
    Map<String, ProviderConfig> getProviderConfigs();
}
