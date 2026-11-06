package io.apicurio.registry.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.microsoft.kiota.ApiException;
import io.apicurio.registry.client.refs.ReferenceRegistrar;
import io.apicurio.registry.client.refs.RegistrationException;
import io.apicurio.registry.client.refs.RegistrationListener;
import io.apicurio.registry.client.refs.VersionStrategy;
import io.apicurio.registry.content.refs.resolve.ArtifactNamingStrategy;
import io.apicurio.registry.content.refs.resolve.DefaultNamingStrategy;
import io.apicurio.registry.content.refs.resolve.ExistingArtifact;
import io.apicurio.registry.content.refs.resolve.LegacyNamingStrategy;
import io.apicurio.registry.content.refs.resolve.PlanOptions;
import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.content.refs.resolve.ReferenceCycleException;
import io.apicurio.registry.content.refs.resolve.ReferenceResolver;
import io.apicurio.registry.content.refs.resolve.RegistrationPlan;
import io.apicurio.registry.content.refs.resolve.RegistryUrlReferenceResolver;
import io.apicurio.registry.content.refs.resolve.RootArtifact;
import io.apicurio.registry.content.refs.resolve.SchemaSourceSet;
import io.apicurio.registry.content.refs.resolve.UnresolvedReferencesException;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.vertx.core.Vertx;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Register artifacts against registry.
 */
@Mojo(name = "register", requiresProject = false)
public class RegisterRegistryMojo extends AbstractRegistryMojo {

    /**
     * The list of pre-registered artifacts that can be used as references.
     */
    @Parameter(required = false)
    List<ExistingReference> existingReferences;

    /**
     * The list of artifacts to register.
     */
    @Parameter(required = false)
    List<RegisterArtifact> artifacts;

    /**
     * Set this to 'true' to skip registering the artifact(s). Convenient in case you want to skip for specific occasions.
     */
    @Parameter(property = "skipRegister", defaultValue = "false")
    boolean skip;

    /**
     * Set this to 'true' to perform the action with the "dryRun" option enabled. This will effectively test
     * whether registration *would have worked*. But it results in no changes made on the server.
     */
    @Parameter(property = "dryRun", defaultValue = "false")
    boolean dryRun;

    DefaultArtifactTypeUtilProviderImpl utilProviderFactory = new DefaultArtifactTypeUtilProviderImpl(true);
    private static final String ARTIFACTS_PROPERTY_PREFIX = "artifacts.";

    /**
     * Validate the configuration.
     */
    protected boolean validate() throws MojoExecutionException {
        loadArtifactsFromSystemPropertiesIfNeeded();

        if (skip) {
            getLog().info("register is skipped.");
            return false;
        }

        if (artifacts == null || artifacts.isEmpty()) {
            getLog().warn("No artifacts are configured for registration.");
            return false;
        }

        if (existingReferences == null) {
            existingReferences = new ArrayList<>();
        }

        int idx = 0;
        int errorCount = 0;
        for (RegisterArtifact artifact : artifacts) {
            if (artifact.getGroupId() == null) {
                getLog().error(String.format(
                        "GroupId is required when registering an artifact.  Missing from artifacts[%d].",
                        idx));
                errorCount++;
            }
            if (artifact.getArtifactId() == null) {
                getLog().error(String.format(
                        "ArtifactId is required when registering an artifact.  Missing from artifacts[%s].",
                        idx));
                errorCount++;
            }
            if (artifact.getFile() == null) {
                getLog().error(String.format(
                        "File is required when registering an artifact.  Missing from artifacts[%s].", idx));
                errorCount++;
            } else if (!artifact.getFile().exists()) {
                getLog().error(
                        String.format("Artifact file to register is configured but file does not exist: %s",
                                artifact.getFile().getPath()));
                errorCount++;
            }

            idx++;
        }

        if (errorCount > 0) {
            throw new MojoExecutionException(
                    "Invalid configuration of the Register Artifact(s) mojo. See the output log for details.");
        }
        return true;
    }

    private void loadArtifactsFromSystemPropertiesIfNeeded() throws MojoExecutionException {
        if (artifacts != null && !artifacts.isEmpty()) {
            return;
        }
        List<RegisterArtifact> cliArtifacts = parseArtifactsFromSystemProperties();
        if (!cliArtifacts.isEmpty()) {
            artifacts = cliArtifacts;
            getLog().info("Loaded artifact configuration from system properties (artifacts.<index>.<field>).");
        }
    }

    private static List<RegisterArtifact> parseArtifactsFromSystemProperties() throws MojoExecutionException {
        Map<Integer, RegisterArtifact> artifactsByIndex = new TreeMap<>();

        for (String key : System.getProperties().stringPropertyNames()) {
            ParsedArtifactProperty parsedProperty = parseArtifactPropertyKey(key);
            if (parsedProperty == null) {
                continue;
            }

            String value = System.getProperty(key);

            RegisterArtifact artifact = artifactsByIndex.computeIfAbsent(parsedProperty.index,
                    idx -> new RegisterArtifact());
            applyCliArtifactField(artifact, parsedProperty.field, value, key);
        }

        return new ArrayList<>(artifactsByIndex.values());
    }

    private static ParsedArtifactProperty parseArtifactPropertyKey(String key) throws MojoExecutionException {
        if (!key.startsWith(ARTIFACTS_PROPERTY_PREFIX)) {
            return null;
        }

        String remainder = key.substring(ARTIFACTS_PROPERTY_PREFIX.length());
        if (remainder.isEmpty()) {
            return null;
        }

        int firstDotIdx = remainder.indexOf('.');
        if (firstDotIdx == -1) {
            return new ParsedArtifactProperty(0, remainder);
        }

        String firstSegment = remainder.substring(0, firstDotIdx);
        String field = remainder.substring(firstDotIdx + 1);
        if (field.isEmpty()) {
            return null;
        }

        if (isAllDigits(firstSegment)) {
            return new ParsedArtifactProperty(Integer.parseInt(firstSegment), field);
        }

        return new ParsedArtifactProperty(0, remainder);
    }

    private static boolean isAllDigits(String value) {
        for (int idx = 0; idx < value.length(); idx++) {
            if (!Character.isDigit(value.charAt(idx))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private static final class ParsedArtifactProperty {
        private final int index;
        private final String field;

        private ParsedArtifactProperty(int index, String field) {
            this.index = index;
            this.field = field;
        }
    }

    private static void applyCliArtifactField(RegisterArtifact artifact, String field, String value, String propertyKey)
            throws MojoExecutionException {
        ParsedListProperty parsedListProperty = parseListProperty(field);
        if (parsedListProperty != null) {
            applyCliArtifactListField(artifact, parsedListProperty, value, propertyKey);
            return;
        }

        switch (field) {
            case "groupId":
                artifact.setGroupId(value);
                break;
            case "artifactId":
                artifact.setArtifactId(value);
                break;
            case "artifactType":
                artifact.setArtifactType(value);
                break;
            case "file":
                artifact.setFile(value == null ? null : new File(value).getAbsoluteFile());
                break;
            case "ifExists":
                artifact.setIfExists(parseIfExists(value, propertyKey));
                break;
            case "canonicalize":
                artifact.setCanonicalize(Boolean.valueOf(value));
                break;
            case "minify":
                artifact.setMinify(Boolean.valueOf(value));
                break;
            case "autoRefs":
                artifact.setAutoRefs(Boolean.valueOf(value));
                break;
            case "isDraft":
                artifact.setIsDraft(Boolean.valueOf(value));
                break;
            case "contentType":
                artifact.setContentType(value);
                break;
            case "version":
                artifact.setVersion(value);
                break;
            case "versionStrategy":
                try {
                    artifact.setVersionStrategy(RegisterArtifact.VersionStrategy.valueOf(value));
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException("Invalid value for " + propertyKey + ": " + value
                            + ". Allowed values are: "
                            + Arrays.toString(RegisterArtifact.VersionStrategy.values()));
                }
                break;
            case "avroAutoRefsNamingStrategy":
                try {
                    artifact.setAvroAutoRefsNamingStrategy(RegisterArtifact.AvroAutoRefsNamingStrategy.valueOf(value));
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException("Invalid value for " + propertyKey + ": " + value
                            + ". Allowed values are: "
                            + Arrays.toString(RegisterArtifact.AvroAutoRefsNamingStrategy.values()));
                }
                break;
            case "autoRefsNamingStrategy":
                try {
                    artifact.setAutoRefsNamingStrategy(RegisterArtifact.AutoRefsNamingStrategy.valueOf(value));
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException("Invalid value for " + propertyKey + ": " + value
                            + ". Allowed values are: "
                            + Arrays.toString(RegisterArtifact.AutoRefsNamingStrategy.values()));
                }
                break;
            default:
                throw new MojoExecutionException("Unsupported CLI property for artifact configuration: "
                        + propertyKey + ". Supported fields include groupId, artifactId, artifactType, file, ifExists, "
                        + "canonicalize, minify, autoRefs, isDraft, contentType, version, "
                        + "versionStrategy, avroAutoRefsNamingStrategy, autoRefsNamingStrategy, references.<index>.<field>, "
                        + "existingReferences.<index>.<field>, protoPaths.<index>.");
        }
    }

    private static void applyCliArtifactListField(RegisterArtifact artifact, ParsedListProperty parsedListProperty,
                                                  String value, String propertyKey) throws MojoExecutionException {
        switch (parsedListProperty.listName) {
            case "references":
                RegisterArtifactReference reference = getOrCreateListItem(artifact.getReferences(),
                        artifact::setReferences, parsedListProperty.index, RegisterArtifactReference::new);
                if (parsedListProperty.field == null) {
                    throw new MojoExecutionException("Missing field for reference configuration: " + propertyKey);
                }
                if ("name".equals(parsedListProperty.field)) {
                    reference.setName(value);
                } else {
                    applyCliArtifactField(reference, parsedListProperty.field, value, propertyKey);
                }
                break;
            case "existingReferences":
                ExistingReference existingReference = getOrCreateListItem(artifact.getExistingReferences(),
                        artifact::setExistingReferences, parsedListProperty.index, ExistingReference::new);
                if (parsedListProperty.field == null) {
                    throw new MojoExecutionException("Missing field for existingReference configuration: " + propertyKey);
                }
                applyCliExistingReferenceField(existingReference, parsedListProperty.field, value, propertyKey);
                break;
            case "protoPaths":
                if (parsedListProperty.field != null) {
                    throw new MojoExecutionException("Unsupported protoPaths CLI property: " + propertyKey
                            + ". Use protoPaths.<index>=<path>.");
                }
                List<File> protoPaths = artifact.getProtoPaths();
                if (protoPaths == null) {
                    protoPaths = new ArrayList<>();
                    artifact.setProtoPaths(protoPaths);
                }
                ensureListSize(protoPaths, parsedListProperty.index);
                protoPaths.set(parsedListProperty.index, value == null ? null : new File(value).getAbsoluteFile());
                break;
            default:
                throw new MojoExecutionException("Unsupported CLI list property for artifact configuration: "
                        + propertyKey);
        }
    }

    private static void applyCliExistingReferenceField(ExistingReference existingReference, String field, String value,
                                                       String propertyKey) throws MojoExecutionException {
        switch (field) {
            case "groupId":
                existingReference.setGroupId(value);
                break;
            case "artifactId":
                existingReference.setArtifactId(value);
                break;
            case "version":
                existingReference.setVersion(value);
                break;
            case "resourceName":
                existingReference.setResourceName(value);
                break;
            default:
                throw new MojoExecutionException("Unsupported CLI property for existingReference configuration: "
                        + propertyKey + ". Supported fields include resourceName, groupId, artifactId, version.");
        }
    }

    private static ParsedListProperty parseListProperty(String field) {
        int firstDotIdx = field.indexOf('.');
        if (firstDotIdx == -1) {
            return null;
        }

        String listName = field.substring(0, firstDotIdx);
        String remainder = field.substring(firstDotIdx + 1);
        int secondDotIdx = remainder.indexOf('.');

        String indexSegment = secondDotIdx == -1 ? remainder : remainder.substring(0, secondDotIdx);
        if (!isAllDigits(indexSegment)) {
            return null;
        }

        String nestedField = secondDotIdx == -1 ? null : remainder.substring(secondDotIdx + 1);
        if (nestedField != null && nestedField.isEmpty()) {
            return null;
        }

        return new ParsedListProperty(listName, Integer.parseInt(indexSegment), nestedField);
    }

    private static <T> T getOrCreateListItem(List<T> list, java.util.function.Consumer<List<T>> setter, int index,
                                             java.util.function.Supplier<T> supplier) {
        if (list == null) {
            list = new ArrayList<>();
            setter.accept(list);
        }
        ensureListSize(list, index);
        T item = list.get(index);
        if (item == null) {
            item = supplier.get();
            list.set(index, item);
        }
        return item;
    }

    private static <T> void ensureListSize(List<T> list, int index) {
        while (list.size() <= index) {
            list.add(null);
        }
    }

    private static final class ParsedListProperty {
        private final String listName;
        private final int index;
        private final String field;

        private ParsedListProperty(String listName, int index, String field) {
            this.listName = listName;
            this.index = index;
            this.field = field;
        }
    }

    private record ResolvedArtifactVersion(String version, Boolean isDraft, boolean derivedFromApiInfoVersion,
                                           boolean derivedFromSnapshot) {
        private boolean shouldUpdateDraftContentOnConflict() {
            return Boolean.TRUE.equals(isDraft) && version != null;
        }

        private boolean shouldPromoteDraftOnConflict() {
            return derivedFromApiInfoVersion && !derivedFromSnapshot && !Boolean.TRUE.equals(isDraft)
                    && version != null;
        }
    }

    private static io.apicurio.registry.rest.v3.beans.IfArtifactExists parseIfExists(String value, String propertyKey)
            throws MojoExecutionException {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return io.apicurio.registry.rest.v3.beans.IfArtifactExists.valueOf(value);
        } catch (IllegalArgumentException e) {
            for (io.apicurio.registry.rest.v3.beans.IfArtifactExists candidate
                    : io.apicurio.registry.rest.v3.beans.IfArtifactExists.values()) {
                if (candidate.value().equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            throw new MojoExecutionException("Invalid value for " + propertyKey + ": " + value
                    + ". Allowed values are: "
                    + Arrays.toString(io.apicurio.registry.rest.v3.beans.IfArtifactExists.values()));
        }
    }

    @Override
    protected void executeInternal() throws MojoExecutionException {
        int errorCount = 0;
        if (validate()) {
            Vertx vertx = createVertx();
            try {
                RegistryClient registryClient = createClient(vertx);

                for (RegisterArtifact artifact : artifacts) {
                    String groupId = artifact.getGroupId();
                    String artifactId = artifact.getArtifactId();
                    try {
                        if (artifact.getAutoRefs() != null && artifact.getAutoRefs()) {
                            registerWithAutoRefs(registryClient, artifact);
                        } else {
                            List<ArtifactReference> references = new ArrayList<>();
                            // First, we check if the artifact being processed has references defined
                            if (hasReferences(artifact)) {
                                references = processArtifactReferences(registryClient, artifact.getReferences());
                            }
                            registerArtifact(registryClient, artifact, references);
                        }
                    } catch (Exception e) {
                        // The registry client wraps an InterruptedException (e.g. RuntimeException from
                        // Kiota's Vert.x adapter) and clears the interrupt flag, so look for it in the cause
                        // chain rather than relying on a bare InterruptedException reaching this loop.
                        InterruptedException interrupt = findInterrupt(e);
                        if (interrupt != null) {
                            Thread.currentThread().interrupt();
                            throw new MojoExecutionException(String.format(
                                    "Interrupted while registering artifact [%s] / [%s]", groupId, artifactId),
                                    interrupt);
                        }
                        errorCount++;
                        getLog().error(String.format("Exception while registering artifact [%s] / [%s]", groupId,
                                artifactId), e);
                    }

                }

                if (errorCount > 0) {
                    throw new MojoExecutionException("Errors while registering artifacts ...");
                }
            } finally {
                vertx.close();
            }
        }
    }

    private static InterruptedException findInterrupt(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof InterruptedException) {
                return (InterruptedException) c;
            }
        }
        return null;
    }

    private void registerWithAutoRefs(RegistryClient registryClient, RegisterArtifact artifact)
            throws MojoExecutionException, MojoFailureException {
        List<Path> dirs = artifact.getProtoPaths() != null
                ? artifact.getProtoPaths().stream().map(File::toPath).toList()
                : List.of(artifact.getFile().getParentFile().toPath());
        SchemaSourceSet sources = SchemaSourceSet.fromDirectories(dirs, SchemaSourceSet.DEFAULT_FILTER);
        String rootLocation = sources.locationOf(artifact.getFile().toPath());
        List<ExistingArtifact> existing = Stream
                .concat(streamOf(existingReferences), streamOf(artifact.getExistingReferences()))
                .map(r -> new ExistingArtifact(r.getResourceName(), r.getGroupId(), r.getArtifactId(),
                        r.getVersion()))
                .toList();
        ArtifactNamingStrategy naming = artifact.getAutoRefsNamingStrategy()
                == RegisterArtifact.AutoRefsNamingStrategy.DEFAULT
                ? DefaultNamingStrategy.INSTANCE
                : new LegacyNamingStrategy(LegacyNamingStrategy.AvroMode.valueOf(
                        artifact.getAvroAutoRefsNamingStrategy().name()), utilProviderFactory);
        try {
            RegistrationPlan plan = ReferenceResolver.plan(
                    List.of(new RootArtifact(rootLocation, artifact.getGroupId(), artifact.getArtifactId(),
                            artifact.getArtifactType(), artifact.getContentType(), artifact.getVersion(),
                            artifact.getIsDraft())),
                    sources, PlanOptions.builder().namingStrategy(naming).existingArtifacts(existing)
                            .urlResolver(new RegistryUrlReferenceResolver(registryUrl))
                            .providerFactory(utilProviderFactory).build());
            ReferenceRegistrar.builder(registryClient).ifExists(toClientIfExists(artifact.getIfExists()))
                    .canonical(artifact.getCanonicalize()).dryRun(dryRun)
                    .minify(Boolean.TRUE.equals(artifact.getMinify()))
                    .versionStrategy(artifact.getVersionStrategy() == RegisterArtifact.VersionStrategy.API_INFO_VERSION
                            ? VersionStrategy.apiInfoVersion() : VersionStrategy.explicitOnly())
                    .listener(new RegistrationListener() {
                        @Override
                        public void onRegistered(PlannedArtifact a, VersionMetaData v) {
                            getLog().info(String.format(
                                    "Successfully registered artifact [%s] / [%s].  GlobalId is [%d]",
                                    v.getGroupId(), v.getArtifactId(), v.getGlobalId()));
                        }

                        @Override
                        public void onWarning(String message) {
                            getLog().warn(message);
                        }
                    }).build().execute(plan);
        } catch (ReferenceCycleException | UnresolvedReferencesException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (RegistrationException e) {
            if (e.problem() instanceof RuleViolationProblemDetails rv) {
                logAndThrow(rv);
            } else if (e.problem() instanceof ProblemDetails pd) {
                logAndThrow(pd);
            }
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private static Stream<ExistingReference> streamOf(List<ExistingReference> refs) {
        return refs == null ? Stream.empty() : refs.stream();
    }

    private static IfArtifactExists toClientIfExists(io.apicurio.registry.rest.v3.beans.IfArtifactExists value) {
        return value == null ? null : IfArtifactExists.forValue(value.value());
    }

    private VersionMetaData registerArtifact(RegistryClient registryClient, RegisterArtifact artifact,
                                             List<ArtifactReference> references) throws IOException, ExecutionException,
            InterruptedException, MojoExecutionException, MojoFailureException {
        if (artifact.getFile() != null) {
            try (InputStream artifactContent = new FileInputStream(artifact.getFile())) {
                return registerArtifact(registryClient, artifact, artifactContent, references);
            }
        } else {
            return getArtifactVersionMetadata(registryClient, artifact);
        }
    }

    private VersionMetaData getArtifactVersionMetadata(RegistryClient registryClient,
                                                       RegisterArtifact artifact) {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();

        VersionMetaData amd = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).get();
        getLog().info(String.format("Successfully processed artifact [%s] / [%s].  GlobalId is [%d]", groupId,
                artifactId, amd.getGlobalId()));

        return amd;
    }

    private VersionMetaData registerArtifact(RegistryClient registryClient, RegisterArtifact artifact,
                                             InputStream artifactContent, List<ArtifactReference> references)
            throws ExecutionException, InterruptedException, MojoFailureException, MojoExecutionException {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String type = artifact.getArtifactType();
        Boolean canonicalize = artifact.getCanonicalize();
        String ct = artifact.getContentType() == null ? ContentTypes.APPLICATION_JSON
                : artifact.getContentType();
        String data = null;
        try {
            if (artifact.getMinify() != null && artifact.getMinify()) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readValue(artifactContent, JsonNode.class);
                data = jsonNode.toString();
            } else {
                data = new String(artifactContent.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ResolvedArtifactVersion resolvedArtifactVersion = resolveVersion(artifact, data);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(type);

        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(resolvedArtifactVersion.version());
        createVersion.setIsDraft(resolvedArtifactVersion.isDraft());
        createArtifact.setFirstVersion(createVersion);

        VersionContent content = new VersionContent();
        content.setContent(data);
        content.setContentType(ct);
        content.setReferences(references.stream().map(r -> {
            ArtifactReference ref = new ArtifactReference();
            ref.setArtifactId(r.getArtifactId());
            ref.setGroupId(r.getGroupId());
            ref.setVersion(r.getVersion());
            ref.setName(r.getName());
            return ref;
        }).collect(Collectors.toList()));
        createVersion.setContent(content);

        try {
            var vmd = registryClient.groups().byGroupId(groupId).artifacts().post(createArtifact, config -> {
                if (artifact.getIfExists() != null) {
                    config.queryParameters.ifExists = IfArtifactExists
                            .forValue(artifact.getIfExists().value());
                    if (dryRun) {
                        config.queryParameters.dryRun = true;
                    }
                }
                config.queryParameters.canonical = canonicalize;
            });

            getLog().info(String.format("Successfully registered artifact [%s] / [%s].  GlobalId is [%d]",
                    groupId, artifactId, vmd.getVersion().getGlobalId()));


            return vmd.getVersion();
        } catch (RuleViolationProblemDetails | ProblemDetails e) {

            if (e.getResponseStatusCode() == 409
                    && resolvedArtifactVersion.shouldUpdateDraftContentOnConflict()) {
                try {
                    registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                            .versions().byVersionExpression(resolvedArtifactVersion.version()).content()
                            .put(content, config -> {

                    });
                    getLog().info(String.format("Successfully updated artifact [%s] / [%s].",
                            groupId, artifactId));
                    // Return version metadata
                    return registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                            .versions().byVersionExpression(resolvedArtifactVersion.version()).get();
                } catch (RuleViolationProblemDetails | ProblemDetails pd) {
                    logAndThrow(pd);
                    return null;
                }
            } else if (e.getResponseStatusCode() == 409
                    && resolvedArtifactVersion.shouldPromoteDraftOnConflict()) {
                return promoteExistingDraftVersion(registryClient, groupId, artifactId, content,
                        resolvedArtifactVersion, e);
            } else {
                logAndThrow(e);
                return null;
            }
        }
    }

    private ResolvedArtifactVersion resolveVersion(RegisterArtifact artifact, String data) throws MojoExecutionException {
        if (artifact.getVersion() != null && !artifact.getVersion().isBlank()) {
            return new ResolvedArtifactVersion(artifact.getVersion(), artifact.getIsDraft(), false, false);
        }

        if (artifact.getVersionStrategy() == RegisterArtifact.VersionStrategy.API_INFO_VERSION
                && isApiArtifact(artifact.getArtifactType())) {
            String apiInfoVersion = extractApiInfoVersion(artifact, data);
            if (apiInfoVersion == null) {
                return new ResolvedArtifactVersion(null, artifact.getIsDraft(), false, false);
            }
            if (apiInfoVersion.endsWith("-SNAPSHOT")) {
                return new ResolvedArtifactVersion(
                        apiInfoVersion.substring(0, apiInfoVersion.length() - "-SNAPSHOT".length()),
                        Boolean.TRUE,
                        true,
                        true);
            }
            return new ResolvedArtifactVersion(apiInfoVersion, artifact.getIsDraft(), true, false);
        }

        return new ResolvedArtifactVersion(null, artifact.getIsDraft(), false, false);
    }

    private VersionMetaData promoteExistingDraftVersion(RegistryClient registryClient, String groupId,
                                                        String artifactId, VersionContent content,
                                                        ResolvedArtifactVersion resolvedArtifactVersion,
                                                        ApiException registrationError)
            throws MojoExecutionException, MojoFailureException, InterruptedException, ExecutionException {
        try {
            VersionMetaData existingVersion = registryClient.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().byVersionExpression(resolvedArtifactVersion.version()).get();
            if (!VersionState.DRAFT.equals(existingVersion.getState())) {
                logAndThrow(registrationError);
                return null;
            }

            registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression(resolvedArtifactVersion.version()).content().put(content, config -> {
                    });

            WrappedVersionState enabled = new WrappedVersionState();
            enabled.setState(VersionState.ENABLED);
            registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression(resolvedArtifactVersion.version()).state().put(enabled);

            getLog().info(String.format("Successfully promoted draft artifact [%s] / [%s] version [%s].",
                    groupId, artifactId, resolvedArtifactVersion.version()));

            return registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression(resolvedArtifactVersion.version()).get();
        } catch (RuleViolationProblemDetails | ProblemDetails e) {
            logAndThrow(e);
            return null;
        }
    }

    private boolean isApiArtifact(String artifactType) {
        return ArtifactType.OPENAPI.equals(artifactType) || ArtifactType.ASYNCAPI.equals(artifactType);
    }

    private String extractApiInfoVersion(RegisterArtifact artifact, String data) throws MojoExecutionException {
        try {
            JsonNode root = getVersionExtractionMapper(artifact).readTree(data);
            JsonNode versionNode = root.path("info").path("version");
            if (versionNode.isTextual()) {
                String version = versionNode.asText();
                if (!version.isBlank()) {
                    return version;
                }
            }
            return null;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to extract info.version from API artifact: "
                    + artifact.getFile().getPath(), e);
        }
    }

    private ObjectMapper getVersionExtractionMapper(RegisterArtifact artifact) {
        return isYamlContent(artifact) ? new ObjectMapper(new YAMLFactory()) : new ObjectMapper();
    }

    private boolean isYamlContent(RegisterArtifact artifact) {
        String contentType = artifact.getContentType();
        if (contentType != null) {
            String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
            if (normalizedContentType.contains("yaml") || normalizedContentType.contains("yml")) {
                return true;
            }
        }

        File file = artifact.getFile();
        if (file == null) {
            return false;
        }

        String fileName = file.getName().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }

    private static boolean hasReferences(RegisterArtifact artifact) {
        return artifact.getReferences() != null && !artifact.getReferences().isEmpty();
    }

    private List<ArtifactReference> processArtifactReferences(RegistryClient registryClient,
                                                              List<RegisterArtifactReference> referencedArtifacts) throws IOException,
            ExecutionException, InterruptedException, MojoExecutionException, MojoFailureException {
        List<ArtifactReference> references = new ArrayList<>();
        for (RegisterArtifactReference artifact : referencedArtifacts) {
            List<ArtifactReference> nestedReferences = new ArrayList<>();
            // First, we check if the artifact being processed has references defined, and register them if
            // needed
            if (hasReferences(artifact)) {
                nestedReferences = processArtifactReferences(registryClient, artifact.getReferences());
            }
            final VersionMetaData artifactMetaData = registerArtifact(registryClient, artifact,
                    nestedReferences);
            references.add(buildReferenceFromMetadata(artifactMetaData, artifact.getName()));
        }
        return references;
    }

    public void setArtifacts(List<RegisterArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    private static ArtifactReference buildReferenceFromMetadata(VersionMetaData metaData,
                                                                String referenceName) {
        ArtifactReference reference = new ArtifactReference();
        reference.setName(referenceName);
        reference.setArtifactId(metaData.getArtifactId());
        reference.setGroupId(metaData.getGroupId());
        reference.setVersion(metaData.getVersion());
        return reference;
    }

}
