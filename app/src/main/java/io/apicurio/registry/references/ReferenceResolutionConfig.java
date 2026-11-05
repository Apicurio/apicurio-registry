package io.apicurio.registry.references;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The config property that bounds every reference-graph walk, shared by the places that read it: the
 * injected {@code @ConfigProperty} field in {@code AbstractSqlRegistryStorage} (used by the older,
 * not-yet-migrated walker 4, {@code SqlContentRepository.resolveReferencesRaw}, which still truncates with
 * a warning instead of throwing), and {@link #maxDepth()}'s direct {@link ConfigProvider} reads, used by
 * {@link ReferenceResolution}, {@code AvroCanonicalHashUpgrader} and
 * {@code io.apicurio.registry.rules.app.integrity.IntegrityRuleExecutor}.
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

    /**
     * Reads {@link #MAX_DEPTH_PROPERTY} directly via {@link ConfigProvider} rather than injecting it,
     * since every caller of this method is either a static utility called from many places, not all of
     * them CDI beans with that property already available (see {@code HttpCompressionWriterInterceptor}
     * and {@code GitOpsConfig} for the same pattern elsewhere in this codebase), or, for
     * {@code AvroCanonicalHashUpgrader}, constructed with {@code new} outside CDI entirely. The value is
     * re-read on every call rather than cached, matching how {@code @ConfigProperty}-injected fields pick
     * up config changes only on redeploy anyway — this is not on a hot enough path for that to matter.
     */
    public static int maxDepth() {
        return ConfigProvider.getConfig().getOptionalValue(MAX_DEPTH_PROPERTY, Integer.class)
                .orElse(DEFAULT_MAX_DEPTH);
    }
}
