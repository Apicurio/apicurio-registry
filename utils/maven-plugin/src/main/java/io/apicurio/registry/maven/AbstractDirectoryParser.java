package io.apicurio.registry.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public abstract class AbstractDirectoryParser<Schema> {

    private final RegistryClient client;

    public AbstractDirectoryParser(RegistryClient client) {
        this.client = client;
    }

    private static final Logger log = LoggerFactory.getLogger(AbstractDirectoryParser.class);

    public abstract ParsedDirectoryWrapper<Schema> parse(File rootSchema);

    public abstract List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, Schema schema, Map<String, ContentHandle> fileContents) throws FileNotFoundException, ExecutionException, InterruptedException;

    protected ContentHandle readSchemaContent(File schemaFile) {
        try {
            return ContentHandle.create(Files.readAllBytes(schemaFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + schemaFile, e);
        }
    }

    protected RegisterArtifact buildFromRoot(RegisterArtifact rootArtifact, String artifactId) {
        RegisterArtifact nestedSchema = new RegisterArtifact();
        nestedSchema.setCanonicalize(rootArtifact.getCanonicalize());
        nestedSchema.setArtifactId(artifactId);
        nestedSchema.setGroupId(rootArtifact.getGroupId());
        nestedSchema.setContentType(rootArtifact.getContentType());
        nestedSchema.setType(rootArtifact.getType());
        nestedSchema.setMinify(rootArtifact.getMinify());
        nestedSchema.setContentType(rootArtifact.getContentType());
        nestedSchema.setIfExists(rootArtifact.getIfExists());
        return nestedSchema;
    }

    protected ArtifactReference registerNestedSchema(String referenceName, List<ArtifactReference> nestedArtifactReferences, 
            RegisterArtifact nestedSchema, String artifactContent) throws FileNotFoundException, ExecutionException, InterruptedException
    {
        CreateArtifactResponse car = registerArtifact(nestedSchema, IoUtil.toStream(artifactContent), nestedArtifactReferences);
        ArtifactReference referencedArtifact = new ArtifactReference();
        referencedArtifact.setName(referenceName);
        referencedArtifact.setArtifactId(car.getArtifact().getArtifactId());
        referencedArtifact.setGroupId(car.getArtifact().getGroupId());
        referencedArtifact.setVersion(car.getVersion().getVersion());
        return referencedArtifact;
    }

    private CreateArtifactResponse registerArtifact(RegisterArtifact artifact, InputStream artifactContent, List<ArtifactReference> references) throws ExecutionException, InterruptedException {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String type = artifact.getType();
        Boolean canonicalize = artifact.getCanonicalize();
        String ct = artifact.getContentType() == null ? ContentTypes.APPLICATION_JSON : artifact.getContentType();
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
        createArtifact.setType(type);

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

        var amd = client
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.forValue(artifact.getIfExists().value());
                    config.queryParameters.canonical = canonicalize;
                });

                // client.createArtifact(groupId, artifactId, version, type, ifExists, canonicalize, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, artifactContent, references);
        log.info(String.format("Successfully registered artifact [%s] / [%s].  GlobalId is [%d]", groupId, artifactId, amd.getVersion().getGlobalId()));

        return amd;
    }
}
