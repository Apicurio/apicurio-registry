package io.apicurio.registry.references;

/**
 * The config property that bounds every reference-graph walk, shared by the three places that read it:
 * the injected {@code @ConfigProperty} field in {@code AbstractSqlRegistryStorage} (used by the older,
 * not-yet-migrated walker 4, {@code SqlContentRepository.resolveReferencesRaw}, which still truncates with
 * a warning instead of throwing), and the direct {@link org.eclipse.microprofile.config.ConfigProvider}
 * reads in {@link ReferenceResolution} and {@code AvroCanonicalHashUpgrader}.
 * <p>
 * One shared constant for the property name and default, instead of three separately maintained literals,
 * so they cannot drift apart (review of #10716 at 79061fc, optional finding O3).
 */
public final class ReferenceResolutionConfig {

    public static final String MAX_DEPTH_PROPERTY = "apicurio.storage.references.max-depth";

    public static final int DEFAULT_MAX_DEPTH = 100;

    /**
     * String form of {@link #DEFAULT_MAX_DEPTH}, needed only because {@code @ConfigProperty}'s
     * {@code defaultValue} must be a compile-time {@code String} constant. Keep in sync with
     * {@link #DEFAULT_MAX_DEPTH} — nothing enforces the two agreeing automatically.
     */
    public static final String DEFAULT_MAX_DEPTH_STRING = "100";

    private ReferenceResolutionConfig() {
    }
}
