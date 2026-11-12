package io.apicurio.registry.client.refs;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.microsoft.kiota.ApiException;
import io.apicurio.registry.content.refs.resolve.ExistingArtifact;
import io.apicurio.registry.content.refs.resolve.PlanOptions;
import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.content.refs.resolve.PlannedReference;
import io.apicurio.registry.content.refs.resolve.ReferenceResolver;
import io.apicurio.registry.content.refs.resolve.RegistrationPlan;
import io.apicurio.registry.content.refs.resolve.RootArtifact;
import io.apicurio.registry.content.refs.resolve.SchemaSourceSet;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.versions.item.WithVersionExpressionItemRequestBuilder;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Registers the artifacts of a {@link RegistrationPlan} in dependency order, wiring each artifact's
 * references to the versions registered (or found) for its dependencies.
 *
 * <p>Example:</p>
 * <pre>{@code
 * SchemaSourceSet sources = SchemaSourceSet.fromDirectories(
 *         List.of(Path.of("src/main/resources/schemas")), SchemaSourceSet.DEFAULT_FILTER);
 * RootArtifact root = RootArtifact.of("order.json", "my-group", "Order");
 * PlanOptions options = PlanOptions.builder().build();
 *
 * RegistrationResult result = ReferenceRegistrar.builder(client)
 *         .ifExists(IfArtifactExists.FIND_OR_CREATE_VERSION)
 *         .build()
 *         .execute(ReferenceResolver.plan(List.of(root), sources, options));
 *
 * // or, with default registrar settings:
 * RegistrationResult same = ReferenceRegistrar.register(client, sources, root, options);
 * }</pre>
 *
 * <p>If registration fails, a {@link RegistrationException} is thrown that carries the artifacts registered
 * so far in {@link RegistrationException#partialResult()}.</p>
 */
public final class ReferenceRegistrar {

    private static final String DEFAULT_GROUP = "default";
    private static final String LATEST = "latest";
    private static final ObjectMapper MAPPER = new JsonMapper();

    private final RegistryClient client;
    private final IfArtifactExists ifExists;
    private final Boolean canonical;
    private final boolean dryRun;
    private final boolean minify;
    private final VersionStrategy versionStrategy;
    private final RegistrationListener listener;

    private ReferenceRegistrar(Builder builder) {
        this.client = builder.client;
        this.ifExists = builder.ifExists;
        this.canonical = builder.canonical;
        this.dryRun = builder.dryRun;
        this.minify = builder.minify;
        this.versionStrategy = builder.versionStrategy;
        this.listener = builder.listener;
    }

    /**
     * Creates a builder.
     *
     * @param client the registry client
     * @return the builder
     */
    public static Builder builder(RegistryClient client) {
        return new Builder(client);
    }

    /**
     * Plans and registers a single root with default registrar settings.
     *
     * @param client the registry client
     * @param sources the schema sources
     * @param root the root artifact
     * @param options the planning options
     * @return the registration result
     */
    public static RegistrationResult register(RegistryClient client, SchemaSourceSet sources, RootArtifact root,
            PlanOptions options) {
        RegistrationPlan plan = ReferenceResolver.plan(List.of(root), sources, options);
        return builder(client).build().execute(plan);
    }

    /**
     * Executes a plan.
     *
     * @param plan the plan
     * @return the registration result
     */
    public RegistrationResult execute(RegistrationPlan plan) {
        plan.warnings().forEach(listener::onWarning);
        Map<PlannedArtifact, VersionMetaData> registered = new LinkedHashMap<>();
        List<PlannedArtifact> skipped = new ArrayList<>();
        Map<LatestKey, VersionMetaData> latestCache = new HashMap<>();
        for (PlannedArtifact artifact : plan.artifacts()) {
            if (dryRun) {
                String reason = dryRunSkipReason(artifact);
                if (reason != null) {
                    skipped.add(artifact);
                    listener.onSkipped(artifact, reason);
                    continue;
                }
            }
            VersionMetaData vmd;
            try {
                List<ArtifactReference> references = new ArrayList<>();
                for (PlannedReference ref : artifact.references()) {
                    references.add(toReference(ref, registered, latestCache));
                }
                vmd = registerOne(artifact, references);
            } catch (RegistrationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw failure(artifact, e, registered, skipped);
            }
            registered.put(artifact, vmd);
            listener.onRegistered(artifact, vmd);
        }
        return new RegistrationResult(registered, skipped, dryRun);
    }

    /**
     * In a dry run nothing is persisted, so an artifact referencing another planned artifact (which was
     * itself only dry-run or skipped) cannot be validated by the registry and is skipped instead.
     */
    private static String dryRunSkipReason(PlannedArtifact artifact) {
        List<String> missing = artifact.references().stream()
                .map(PlannedReference::target)
                .filter(PlannedArtifact.class::isInstance)
                .map(PlannedArtifact.class::cast)
                .map(t -> group(t.coordinates().groupId()) + "/" + t.coordinates().artifactId())
                .distinct()
                .toList();
        if (missing.isEmpty()) {
            return null;
        }
        return "Skipped in dry run: depends on artifacts that are not registered yet: "
                + String.join(", ", missing);
    }

    private ArtifactReference toReference(PlannedReference ref, Map<PlannedArtifact, VersionMetaData> registered,
            Map<LatestKey, VersionMetaData> latestCache) {
        ArtifactReference out = new ArtifactReference();
        out.setName(ref.name());
        if (ref.target() instanceof PlannedArtifact target) {
            VersionMetaData vmd = registered.get(target);
            if (vmd == null) {
                throw new IllegalStateException("Plan is not in dependency order: " + target);
            }
            out.setGroupId(vmd.getGroupId() != null ? vmd.getGroupId() : group(target.coordinates().groupId()));
            out.setArtifactId(vmd.getArtifactId() != null ? vmd.getArtifactId()
                    : target.coordinates().artifactId());
            out.setVersion(vmd.getVersion());
        } else if (ref.target() instanceof ExistingArtifact existing) {
            String version = existing.version();
            if (version == null || LATEST.equals(version.toLowerCase(Locale.ROOT))) {
                String groupId = group(existing.groupId());
                version = latestCache.computeIfAbsent(new LatestKey(groupId, existing.artifactId()),
                        k -> client.groups().byGroupId(groupId).artifacts().byArtifactId(existing.artifactId())
                                .versions().byVersionExpression("branch=latest").get())
                        .getVersion();
            }
            out.setGroupId(group(existing.groupId()));
            out.setArtifactId(existing.artifactId());
            out.setVersion(version);
        } else {
            throw new IllegalStateException("Unsupported reference target: " + ref.target());
        }
        return out;
    }

    private VersionMetaData registerOne(PlannedArtifact artifact, List<ArtifactReference> references) {
        String content = content(artifact);
        ResolvedVersion version = artifact.isRoot() ? versionStrategy.resolve(artifact, content)
                : new ResolvedVersion(null, null, false, false);

        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(artifact.contentType());
        versionContent.setReferences(references);

        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version.version());
        createVersion.setIsDraft(version.draft());
        createVersion.setContent(versionContent);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifact.coordinates().artifactId());
        createArtifact.setArtifactType(artifact.artifactType());
        createArtifact.setFirstVersion(createVersion);

        String groupId = group(artifact.coordinates().groupId());
        String artifactId = artifact.coordinates().artifactId();
        try {
            return client.groups().byGroupId(groupId).artifacts()
                    .post(createArtifact, config -> {
                        config.queryParameters.ifExists = ifExists;
                        config.queryParameters.canonical = canonical;
                        if (dryRun) {
                            config.queryParameters.dryRun = true;
                        }
                    }).getVersion();
        } catch (ApiException e) {
            if (dryRun || !isProblem(e) || e.getResponseStatusCode() != 409) {
                throw e;
            }
            if (shouldUpdateDraftContentOnConflict(version)) {
                versionClient(groupId, artifactId, version.version()).content().put(versionContent);
                return versionClient(groupId, artifactId, version.version()).get();
            }
            if (shouldPromoteDraftOnConflict(version)) {
                return promoteExistingDraftVersion(groupId, artifactId, versionContent, version.version(), e);
            }
            throw e;
        }
    }

    private VersionMetaData promoteExistingDraftVersion(String groupId, String artifactId,
            VersionContent versionContent, String version, ApiException registrationError) {
        VersionMetaData existing = versionClient(groupId, artifactId, version).get();
        if (!VersionState.DRAFT.equals(existing.getState())) {
            throw registrationError;
        }
        versionClient(groupId, artifactId, version).content().put(versionContent);
        WrappedVersionState enabled = new WrappedVersionState();
        enabled.setState(VersionState.ENABLED);
        versionClient(groupId, artifactId, version).state().put(enabled);
        return versionClient(groupId, artifactId, version).get();
    }

    private WithVersionExpressionItemRequestBuilder versionClient(String groupId, String artifactId, String version) {
        return client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version);
    }

    private static boolean shouldUpdateDraftContentOnConflict(ResolvedVersion version) {
        return Boolean.TRUE.equals(version.draft()) && version.version() != null;
    }

    private static boolean shouldPromoteDraftOnConflict(ResolvedVersion version) {
        return version.fromApiInfo() && !version.fromSnapshot() && !Boolean.TRUE.equals(version.draft())
                && version.version() != null;
    }

    private static boolean isProblem(ApiException e) {
        return e instanceof ProblemDetails || e instanceof RuleViolationProblemDetails;
    }

    private RegistrationException failure(PlannedArtifact artifact, RuntimeException e,
            Map<PlannedArtifact, VersionMetaData> registered, List<PlannedArtifact> skipped) {
        RegistrationResult partial = new RegistrationResult(registered, skipped, dryRun);
        String coordinates = "[" + group(artifact.coordinates().groupId()) + "] / ["
                + artifact.coordinates().artifactId() + "]";
        InterruptedException interrupt = findInterrupt(e);
        if (interrupt != null) {
            Thread.currentThread().interrupt();
            return new RegistrationException("Interrupted while registering artifact " + coordinates, artifact,
                    null, partial, interrupt);
        }
        if (e instanceof ApiException api && isProblem(api)) {
            return new RegistrationException("Failed to register artifact " + coordinates + ": "
                    + describe(api), artifact, api, partial, e);
        }
        return new RegistrationException("Failed to register artifact " + coordinates, artifact, null, partial, e);
    }

    private static String describe(ApiException e) {
        String title;
        String detail;
        if (e instanceof RuleViolationProblemDetails rv) {
            title = rv.getTitle();
            detail = rv.getDetail();
        } else {
            ProblemDetails pd = (ProblemDetails) e;
            title = pd.getTitle();
            detail = pd.getDetail();
        }
        StringBuilder sb = new StringBuilder("HTTP ").append(e.getResponseStatusCode());
        if (title != null) {
            sb.append(" ").append(title);
        }
        if (detail != null) {
            sb.append(" - ").append(detail);
        }
        return sb.toString();
    }

    private static InterruptedException findInterrupt(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof InterruptedException ie) {
                return ie;
            }
        }
        return null;
    }

    private String content(PlannedArtifact artifact) {
        String raw = artifact.source().content().getContent().content();
        String ct = artifact.contentType();
        if (minify && ct != null && ct.toLowerCase(Locale.ROOT).contains("json")) {
            try {
                return MAPPER.readTree(raw).toString();
            } catch (JacksonException e) {
                throw new UncheckedIOException("Unable to minify " + artifact.source().location(),
                        new IOException(e.getMessage(), e));
            }
        }
        return raw;
    }

    private record LatestKey(String groupId, String artifactId) {
    }

    private static String group(String groupId) {
        return groupId == null ? DEFAULT_GROUP : groupId;
    }

    /**
     * Builder for {@link ReferenceRegistrar}.
     */
    public static final class Builder {

        private final RegistryClient client;
        private IfArtifactExists ifExists;
        private Boolean canonical;
        private boolean dryRun;
        private boolean minify;
        private VersionStrategy versionStrategy = VersionStrategy.explicitOnly();
        private RegistrationListener listener = new RegistrationListener() {
        };

        private Builder(RegistryClient client) {
            this.client = Objects.requireNonNull(client, "client");
        }

        /**
         * Sets the behavior when the artifact already exists.
         *
         * @param ifExists the policy, or {@code null} for the server default
         * @return this builder
         */
        public Builder ifExists(IfArtifactExists ifExists) {
            this.ifExists = ifExists;
            return this;
        }

        /**
         * Sets whether content is canonicalized when matching existing versions.
         *
         * @param canonical the flag, or {@code null} for the server default
         * @return this builder
         */
        public Builder canonical(Boolean canonical) {
            this.canonical = canonical;
            return this;
        }

        /**
         * Enables dry-run mode, in which nothing is persisted. Each artifact is sent with
         * {@code dryRun=true} so the registry validates it without storing it. Because dry-run artifacts are
         * not stored, an artifact that references another artifact of the same plan cannot be validated:
         * such artifacts (and, transitively, anything depending on them) are not sent, but are reported via
         * {@link RegistrationListener#onSkipped} and {@link RegistrationResult#skipped()}. References to
         * {@link ExistingArtifact}s do not cause skipping. On a dry run a {@code 409 Conflict} is always
         * reported as a failure; the draft update/promotion fallbacks are not attempted.
         *
         * @param dryRun the flag
         * @return this builder
         */
        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        /**
         * Enables minification of JSON content before sending.
         *
         * @param minify the flag
         * @return this builder
         */
        public Builder minify(boolean minify) {
            this.minify = minify;
            return this;
        }

        /**
         * Sets the version strategy for root artifacts.
         *
         * @param strategy the strategy
         * @return this builder
         */
        public Builder versionStrategy(VersionStrategy strategy) {
            this.versionStrategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        /**
         * Sets the listener.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder listener(RegistrationListener listener) {
            this.listener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        /**
         * Builds the registrar.
         *
         * @return the registrar
         */
        public ReferenceRegistrar build() {
            return new ReferenceRegistrar(this);
        }
    }
}
