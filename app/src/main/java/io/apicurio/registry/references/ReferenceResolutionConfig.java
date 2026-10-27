package io.apicurio.registry.references;

/**
 * The config property that bounds every reference-graph walk, shared by the three places that read it:
 * the injected {@code @ConfigProperty} field in {@code AbstractSqlRegistryStorage} (used by the older,
 * not-yet-migrated walker 4, {@code SqlContentRepository.resolveReferencesRaw}, which still truncates with
 * a warning instead of throwing), and the direct {@link org.eclipse.microprofile.config.ConfigProvider}
 * reads in {@link ReferenceResolution} and {@code AvroCanonicalHashUpgrader}.
 * <p>
 * One shared constant for the property name and default, instead of three separately maintained literals
 * that could drift apart.
 */
public final class ReferenceResolutionConfig {

    public static final String MAX_DEPTH_PROPERTY = "apicurio.storage.references.max-depth";

    /**
     * {@code defaultValue} on a {@code @ConfigProperty} field must be a compile-time {@code String}
     * constant, so the default is defined here and parsed into {@link #DEFAULT_MAX_DEPTH} rather than the
     * other way around — the two cannot drift apart.
     */
    public static final String DEFAULT_MAX_DEPTH_STRING = "100";

    public static final int DEFAULT_MAX_DEPTH = Integer.parseInt(DEFAULT_MAX_DEPTH_STRING);

    private ReferenceResolutionConfig() {
    }
}
