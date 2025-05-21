package io.apicurio.registry.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.maven.refs.IndexedResource;
import io.apicurio.registry.maven.refs.ReferenceIndex;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rules.ParsedJsonSchema;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.vertx.core.Vertx;
import org.apache.avro.Schema;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Register artifacts against registry.
 */
@Mojo(name = "register")
public class RegisterRegistryMojo extends AbstractRegistryMojo {

    /**
     * The list of pre-registered artifacts that can be used as references.
     */
    @Parameter(required = false)
    List<ExistingReference> existingReferences;

    /**
     * The list of artifacts to register.
     */
    @Parameter(required = true)
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

    DefaultArtifactTypeUtilProviderImpl utilProviderFactory = new DefaultArtifactTypeUtilProviderImpl();

    /**
     * Validate the configuration.
     */
    protected boolean validate() throws MojoExecutionException {
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
                        ReferenceIndex index = createIndex(artifact.getFile());
                        addExistingReferencesToIndex(registryClient, index, existingReferences);
                        addExistingReferencesToIndex(registryClient, index, artifact.getExistingReferences());
                        Stack<RegisterArtifact> registrationStack = new Stack<>();

                        registerWithAutoRefs(registryClient, artifact, index, registrationStack);
                    } else if (artifact.getAnalyzeDirectory() != null && artifact.getAnalyzeDirectory()) { // Auto
                        // register
                        // selected,
                        // we
                        // must
                        // figure
                        // out
                        // if
                        // the
                        // artifact
                        // has
                        // reference
                        // using
                        // the
                        // directory
                        // structure
                        registerDirectory(registryClient, artifact);
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
            throw new RuntimeException(
                    "Artifact reference loop detected (not supported): " + printLoop(registrationStack));
        }
        registrationStack.push(artifact);

        // Read the artifact content.
        ContentHandle artifactContent = readContent(artifact.getFile());
        String artifactContentType = getContentTypeByExtension(artifact.getFile().getName());
        TypedContent typedArtifactContent = TypedContent.create(artifactContent, artifactContentType);

        // Find all references in the content
        ArtifactTypeUtilProvider provider = this.utilProviderFactory
                .getArtifactTypeProvider(artifact.getArtifactType());
        ReferenceFinder referenceFinder = provider.getReferenceFinder();
        Set<ExternalReference> externalReferences = referenceFinder
                .findExternalReferences(typedArtifactContent);

        // Register all the references first, then register the artifact.
        List<ArtifactReference> registeredReferences = new ArrayList<>(externalReferences.size());
        for (ExternalReference externalRef : externalReferences) {
            IndexedResource iresource = index.lookup(externalRef.getResource(),
                    Paths.get(artifact.getFile().toURI()));

            // TODO: need a way to resolve references that are not local (already registered in the registry)
            if (iresource == null) {
                throw new RuntimeException("Reference could not be resolved.  From: "
                        + artifact.getFile().getName() + "  To: " + externalRef.getFullReference());
            }

            // If the resource isn't already registered, then register it now.
            if (!iresource.isRegistered()) {
                // TODO: determine the artifactId better (type-specific logic here?)
                String artifactId = externalRef.getResource();
                File localFile = getLocalFile(iresource.getPath());
                RegisterArtifact refArtifact = buildFromRoot(artifact, artifactId);
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

    private void registerDirectory(RegistryClient registryClient, RegisterArtifact artifact)
            throws IOException, ExecutionException, InterruptedException, MojoExecutionException,
            MojoFailureException {
        switch (artifact.getArtifactType()) {
            case ArtifactType.AVRO:
                final AvroDirectoryParser avroDirectoryParser = new AvroDirectoryParser(registryClient);
                final ParsedDirectoryWrapper<Schema> schema = avroDirectoryParser.parse(artifact.getFile());
                registerArtifact(registryClient, artifact, avroDirectoryParser
                        .handleSchemaReferences(artifact, schema.getSchema(), schema.getSchemaContents()));
                break;
            case ArtifactType.PROTOBUF:
                final ProtobufDirectoryParser protobufDirectoryParser = new ProtobufDirectoryParser(
                        registryClient);
                final ParsedDirectoryWrapper<FileDescriptor> protoSchema = protobufDirectoryParser
                        .parse(artifact.getFile());
                registerArtifact(registryClient, artifact, protobufDirectoryParser.handleSchemaReferences(
                        artifact, protoSchema.getSchema(), protoSchema.getSchemaContents()));
                break;
            case ArtifactType.JSON:
                final JsonSchemaDirectoryParser jsonSchemaDirectoryParser = new JsonSchemaDirectoryParser(
                        registryClient);
                final ParsedDirectoryWrapper<ParsedJsonSchema> jsonSchema = jsonSchemaDirectoryParser
                        .parse(artifact.getFile());
                registerArtifact(registryClient, artifact, jsonSchemaDirectoryParser.handleSchemaReferences(
                        artifact, jsonSchema.getSchema(), jsonSchema.getSchemaContents()));
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Artifact type not recognized for analyzing a directory structure %s",
                                artifact.getArtifactType()));
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
            logAndThrow(e);
            return null;
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
     * @param file
     */
    private static ReferenceIndex createIndex(File file) {
        ReferenceIndex index = new ReferenceIndex(file.getParentFile().toPath());
        Collection<File> allFiles = FileUtils.listFiles(file.getParentFile(), null, true);
        allFiles.stream().filter(RegisterRegistryMojo::isFileAllowedInIndex).forEach(f -> {
            index.index(f.toPath(), readContent(f));
        });
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

    protected static RegisterArtifact buildFromRoot(RegisterArtifact rootArtifact, String artifactId) {
        RegisterArtifact nestedSchema = new RegisterArtifact();
        nestedSchema.setCanonicalize(rootArtifact.getCanonicalize());
        nestedSchema.setArtifactId(artifactId);
        nestedSchema.setGroupId(rootArtifact.getGroupId());
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
