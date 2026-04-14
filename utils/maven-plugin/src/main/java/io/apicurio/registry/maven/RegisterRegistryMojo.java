package io.apicurio.registry.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.maven.refs.IndexedResource;
import io.apicurio.registry.maven.refs.ReferenceIndex;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.vertx.core.Vertx;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private RegisterArtifact.AvroAutoRefsNamingStrategy avroAutoRefsNamingStrategy;

    DefaultArtifactTypeUtilProviderImpl utilProviderFactory = new DefaultArtifactTypeUtilProviderImpl(true);
    private static final String ARTIFACTS_PROPERTY_PREFIX = "artifacts.";
    private Pattern registryArtifactUrlPattern;

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
            case "avroAutoRefsNamingStrategy":
                try {
                    artifact.setAvroAutoRefsNamingStrategy(RegisterArtifact.AvroAutoRefsNamingStrategy.valueOf(value));
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException("Invalid value for " + propertyKey + ": " + value
                            + ". Allowed values are: "
                            + Arrays.toString(RegisterArtifact.AvroAutoRefsNamingStrategy.values()));
                }
                break;
            default:
                throw new MojoExecutionException("Unsupported CLI property for artifact configuration: "
                        + propertyKey + ". Supported fields include groupId, artifactId, artifactType, file, ifExists, "
                        + "canonicalize, minify, autoRefs, isDraft, contentType, version, "
                        + "avroAutoRefsNamingStrategy, references.<index>.<field>, "
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
            RegistryClient registryClient = createClient(vertx);

            for (RegisterArtifact artifact : artifacts) {
                String groupId = artifact.getGroupId();
                String artifactId = artifact.getArtifactId();
                try {
                    if (artifact.getAutoRefs() != null && artifact.getAutoRefs()) {
                        // If we have references, then we'll need to create the local resource index and then
                        // process all refs.
                        ReferenceIndex index = createIndex(artifact);
                        addExistingReferencesToIndex(registryClient, index, existingReferences);
                        addExistingReferencesToIndex(registryClient, index, artifact.getExistingReferences());
                        Stack<RegisterArtifact> registrationStack = new Stack<>();

                        this.avroAutoRefsNamingStrategy = artifact.getAvroAutoRefsNamingStrategy();
                        registerWithAutoRefs(registryClient, artifact, index, registrationStack);
                    } else {
                        List<ArtifactReference> references = new ArrayList<>();
                        // First, we check if the artifact being processed has references defined
                        if (hasReferences(artifact)) {
                            references = processArtifactReferences(registryClient, artifact.getReferences());
                        }
                        registerArtifact(registryClient, artifact, references);
                    }
                } catch (Exception e) {
                    errorCount++;
                    getLog().error(String.format("Exception while registering artifact [%s] / [%s]", groupId,
                            artifactId), e);
                }

            }

            if (errorCount > 0) {
                throw new MojoExecutionException("Errors while registering artifacts ...");
            }
        }
    }

    private VersionMetaData registerWithAutoRefs(RegistryClient registryClient, RegisterArtifact artifact,
                                                 ReferenceIndex index, Stack<RegisterArtifact> registrationStack) throws IOException,
            ExecutionException, InterruptedException, MojoExecutionException, MojoFailureException {
        if (loopDetected(artifact, registrationStack)) {
            throw new MojoExecutionException(
                    "Artifact reference loop detected (not supported): " + printLoop(registrationStack));
        }
        registrationStack.push(artifact);

        // Read the artifact content.
        ContentHandle artifactContent = readContent(artifact.getFile());
        String artifactContentType = getContentTypeByExtension(artifact.getFile().getName());
        // Set the content type on the artifact if not already explicitly set by the user
        if (artifact.getContentType() == null) {
            artifact.setContentType(artifactContentType);
        }
        TypedContent typedArtifactContent = TypedContent.create(artifactContent, artifactContentType);

        // Find all references in the content
        ArtifactTypeUtilProvider provider = this.utilProviderFactory
                .getArtifactTypeProvider(artifact.getArtifactType());
        ReferenceFinder referenceFinder = provider.getReferenceFinder();
        var referenceArtifactIdentifierExtractor = provider.getReferenceArtifactIdentifierExtractor();
        Set<ExternalReference> externalReferences = referenceFinder
                .findExternalReferences(typedArtifactContent);

        // Register all the references first, then register the artifact.
        List<ArtifactReference> registeredReferences = new ArrayList<>(externalReferences.size());
        Map<String, VersionMetaData> resolvedRegistryReferences = new HashMap<>(); // avoid multiple lookups
        for (ExternalReference externalRef : externalReferences) {
            IndexedResource iresource = index.lookup(externalRef.getResource(),
                    Paths.get(artifact.getFile().toURI()));

            if (iresource == null) {
                Optional<ArtifactReference> registryReference = resolveRegistryReference(registryClient,
                        externalRef, resolvedRegistryReferences);
                if (registryReference.isPresent()) {
                    registeredReferences.add(registryReference.get());
                    continue;
                }

                if (ReferenceUrlUtil.isAbsoluteUri(externalRef.getResource())) {
                    getLog().warn("Skipping external reference not managed by Apicurio Registry: "
                            + externalRef.getFullReference());
                    continue;
                }

                throw new MojoExecutionException("Reference could not be resolved.  From: "
                        + artifact.getFile().getName() + "  To: " + externalRef.getFullReference());
            }

            // If the resource isn't already registered, then register it now.
            if (!iresource.isRegistered()) {
                String groupId = artifact.getGroupId(); // default is same group as root artifact
                // TODO: determine the artifactId better (type-specific logic here?)
                String artifactId = referenceArtifactIdentifierExtractor.extractArtifactId(externalRef.getResource());
                if (ArtifactType.AVRO.equals(iresource.getType())) {
                    if (avroAutoRefsNamingStrategy == RegisterArtifact.AvroAutoRefsNamingStrategy.USE_AVRO_NAMESPACE) {
                        groupId = referenceArtifactIdentifierExtractor.extractGroupId(externalRef.getResource());
                        artifactId = referenceArtifactIdentifierExtractor.extractArtifactId(externalRef.getResource());
                    }
                    if (avroAutoRefsNamingStrategy == RegisterArtifact.AvroAutoRefsNamingStrategy.INHERIT_PARENT_GROUP) {
                        groupId = artifact.getGroupId(); // same group as root artifact
                        artifactId = iresource.getResourceName(); // fq name
                    }
                }
                File localFile = getLocalFile(iresource.getPath());
                RegisterArtifact refArtifact = buildFromRoot(artifact, artifactId, groupId);
                refArtifact.setArtifactType(iresource.getType());
                refArtifact.setVersion(null);
                refArtifact.setFile(localFile);
                refArtifact.setContentType(getContentTypeByExtension(localFile.getName()));
                try {
                    var car = registerWithAutoRefs(registryClient, refArtifact, index, registrationStack);
                    iresource.setRegistration(car);
                } catch (IOException | ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            var reference = new ArtifactReference();
            reference.setName(externalRef.getFullReference());
            reference.setVersion(iresource.getRegistration().getVersion());
            reference.setGroupId(iresource.getRegistration().getGroupId());
            reference.setArtifactId(iresource.getRegistration().getArtifactId());
            registeredReferences.add(reference);
        }
        registeredReferences.sort((ref1, ref2) -> ref1.getName().compareTo(ref2.getName()));

        registrationStack.pop();
        return registerArtifact(registryClient, artifact, registeredReferences);
    }

    private Optional<ArtifactReference> resolveRegistryReference(RegistryClient registryClient,
                                                                 ExternalReference externalRef,
                                                                 Map<String, VersionMetaData> resolvedRegistryReferences) {
        Optional<RegistryReferenceLocation> location = parseRegistryReferenceLocation(externalRef.getResource());
        if (location.isEmpty()) {
            return Optional.empty();
        }

        RegistryReferenceLocation ref = location.get();
        VersionMetaData vmd = resolvedRegistryReferences.get(externalRef.getResource());
        if (vmd == null) {
            vmd = getRegistryReferenceMetadata(registryClient, ref);
            resolvedRegistryReferences.put(externalRef.getResource(), vmd);
        }
        return Optional.of(buildReferenceFromMetadata(vmd,
                ReferenceUrlUtil.registryReferenceName(externalRef.getFullReference())));
    }

    private VersionMetaData getRegistryReferenceMetadata(RegistryClient registryClient,
                                                         RegistryReferenceLocation ref) {
        return registryClient.groups().byGroupId(ref.groupId).artifacts()
                .byArtifactId(ref.artifactId).versions().byVersionExpression(ref.versionExpression).get();
    }

    private Optional<RegistryReferenceLocation> parseRegistryReferenceLocation(String resource) {
        if (resource == null || registryUrl == null) {
            return Optional.empty();
        }

        if (!ReferenceUrlUtil.isSameApicurioServer(registryUrl, resource)) {
            return Optional.empty();
        }

        URI resourceUri = URI.create(resource);
        Matcher matcher = registryArtifactUrlPattern.matcher(resourceUri.getRawPath());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(new RegistryReferenceLocation(
                ReferenceUrlUtil.decodePathSegment(matcher.group(1)),
                ReferenceUrlUtil.decodePathSegment(matcher.group(2)),
                ReferenceUrlUtil.decodePathSegment(matcher.group(3))));
    }

    private static class RegistryReferenceLocation {
        private final String groupId;
        private final String artifactId;
        private final String versionExpression;

        private RegistryReferenceLocation(String groupId, String artifactId, String versionExpression) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.versionExpression = versionExpression;
        }
    }

    private VersionMetaData registerArtifact(RegistryClient registryClient, RegisterArtifact artifact,
                                             List<ArtifactReference> references) throws FileNotFoundException, ExecutionException,
            InterruptedException, MojoExecutionException, MojoFailureException {
        if (artifact.getFile() != null) {
            return registerArtifact(registryClient, artifact, new FileInputStream(artifact.getFile()),
                    references);
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
        String version = artifact.getVersion();
        String type = artifact.getArtifactType();
        Boolean canonicalize = artifact.getCanonicalize();
        Boolean isDraft = artifact.getIsDraft();
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

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(type);

        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        createVersion.setIsDraft(isDraft);
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

            // If this is a draft, and we got a 409, then we should try to update the artifact content instead.
            if (Boolean.TRUE.equals(artifact.getIsDraft()) && e.getResponseStatusCode() == 409) {
                try {
                    registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                            .versions().byVersionExpression(version).content()
                            .put(content, config -> {

                    });
                    getLog().info(String.format("Successfully updated artifact [%s] / [%s].",
                            groupId, artifactId));
                    // Return version metadata
                    return registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(version).get();
                } catch (RuleViolationProblemDetails | ProblemDetails pd) {
                    logAndThrow(pd);
                    return null;
                }
            } else {
                logAndThrow(e);
                return null;
            }
        }
    }

    private static boolean hasReferences(RegisterArtifact artifact) {
        return artifact.getReferences() != null && !artifact.getReferences().isEmpty();
    }

    private List<ArtifactReference> processArtifactReferences(RegistryClient registryClient,
                                                              List<RegisterArtifactReference> referencedArtifacts) throws FileNotFoundException,
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

    @Override
    public void setRegistryUrl(String registryUrl) {
        super.setRegistryUrl(registryUrl);
        this.registryArtifactUrlPattern = registryUrl == null
                ? null
                : ReferenceUrlUtil.createRegistryArtifactUrlPattern(registryUrl);
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

    private static boolean isFileAllowedInIndex(File file) {
        return file.isFile() && (
                file.getName().toLowerCase().endsWith(".json") ||
                        file.getName().toLowerCase().endsWith(".yml") ||
                        file.getName().toLowerCase().endsWith(".yaml") ||
                        file.getName().toLowerCase().endsWith(".xml") ||
                        file.getName().toLowerCase().endsWith(".xsd") ||
                        file.getName().toLowerCase().endsWith(".wsdl") ||
                        file.getName().toLowerCase().endsWith(".graphql") ||
                        file.getName().toLowerCase().endsWith(".avsc") ||
                        file.getName().toLowerCase().endsWith(".proto")
        );
    }

    /**
     * Create a local index relative to the given file location.
     *
     * @param artifact
     */
    private static ReferenceIndex createIndex(RegisterArtifact artifact) {
        File file = artifact.getFile();
        ReferenceIndex index = new ReferenceIndex(file.getParentFile().toPath());
        if (artifact.getProtoPaths() != null) {
            artifact.getProtoPaths().forEach(path -> index.addSchemaPath(path.toPath()));
        }

        HashSet<File> roots = new HashSet<>();
        if (artifact.getProtoPaths() != null) {
            roots.addAll(artifact.getProtoPaths());
        } else {
            roots.add(file.getParentFile());
        }

        Collection<File> allFiles = new HashSet<>();
        for (File root : roots) {
            allFiles.addAll(FileUtils.listFiles(root, null, true));
            allFiles.stream().filter(RegisterRegistryMojo::isFileAllowedInIndex).forEach(f -> {
                index.index(f.toPath(), readContent(f));
            });
        }

        return index;
    }

    private void addExistingReferencesToIndex(RegistryClient registryClient, ReferenceIndex index,
                                              List<ExistingReference> existingReferences) throws ExecutionException, InterruptedException {
        if (existingReferences != null && !existingReferences.isEmpty()) {
            for (ExistingReference ref : existingReferences) {
                VersionMetaData vmd;
                if (ref.getVersion() == null || "LATEST".equalsIgnoreCase(ref.getVersion())) {
                    vmd = registryClient.groups().byGroupId(ref.getGroupId()).artifacts()
                            .byArtifactId(ref.getArtifactId()).versions().byVersionExpression("branch=latest")
                            .get();
                } else {
                    vmd = new VersionMetaData();
                    vmd.setGroupId(ref.getGroupId());
                    vmd.setArtifactId(ref.getArtifactId());
                    vmd.setVersion(ref.getVersion());
                }
                index.index(ref.getResourceName(), vmd);
            }
        }
    }

    protected static ContentHandle readContent(File file) {
        try {
            return ContentHandle.create(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + file, e);
        }
    }

    protected static RegisterArtifact buildFromRoot(RegisterArtifact rootArtifact, String artifactId, String groupId) {
        RegisterArtifact nestedSchema = new RegisterArtifact();
        nestedSchema.setCanonicalize(rootArtifact.getCanonicalize());
        nestedSchema.setArtifactId(artifactId);
        nestedSchema.setGroupId(groupId == null ? rootArtifact.getGroupId() : groupId);
        nestedSchema.setContentType(rootArtifact.getContentType());
        nestedSchema.setArtifactType(rootArtifact.getArtifactType());
        nestedSchema.setMinify(rootArtifact.getMinify());
        nestedSchema.setContentType(rootArtifact.getContentType());
        nestedSchema.setIfExists(rootArtifact.getIfExists());
        nestedSchema.setAutoRefs(rootArtifact.getAutoRefs());
        return nestedSchema;
    }

    private static File getLocalFile(Path path) {
        return path.toFile();
    }

    /**
     * Detects a loop by looking for the given artifact in the registration stack.
     *
     * @param artifact
     * @param registrationStack
     */
    private static boolean loopDetected(RegisterArtifact artifact,
                                        Stack<RegisterArtifact> registrationStack) {
        for (RegisterArtifact stackArtifact : registrationStack) {
            if (artifact.getFile().equals(stackArtifact.getFile())) {
                return true;
            }
        }
        return false;
    }

    private static String printLoop(Stack<RegisterArtifact> registrationStack) {
        return registrationStack.stream().map(artifact -> artifact.getFile().getName())
                .collect(Collectors.joining(" -> "));
    }

}
