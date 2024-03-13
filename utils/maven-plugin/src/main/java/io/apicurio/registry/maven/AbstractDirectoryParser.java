package io.apicurio.registry.maven;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.IfExists;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;

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
        VersionMetaData referencedArtifactMetadata = registerArtifact(nestedSchema, IoUtil.toStream(artifactContent), nestedArtifactReferences);
        ArtifactReference referencedArtifact = new ArtifactReference();
        referencedArtifact.setName(referenceName);
        referencedArtifact.setArtifactId(referencedArtifactMetadata.getArtifactId());
        referencedArtifact.setGroupId(referencedArtifactMetadata.getGroupId());
        referencedArtifact.setVersion(referencedArtifactMetadata.getVersion());
        return referencedArtifact;
    }

    private VersionMetaData registerArtifact(RegisterArtifact artifact, InputStream artifactContent, List<ArtifactReference> references) throws ExecutionException, InterruptedException {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String type = artifact.getType();
        Boolean canonicalize = artifact.getCanonicalize();
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
        ArtifactContent content = new ArtifactContent();
        content.setContent(data);
        content.setReferences(references.stream().map(r -> {
            ArtifactReference ref = new ArtifactReference();
            ref.setArtifactId(r.getArtifactId());
            ref.setGroupId(r.getGroupId());
            ref.setVersion(r.getVersion());
            ref.setName(r.getName());
            return ref;
        }).collect(Collectors.toList()));
        VersionMetaData amd = client
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .post(content, config -> {
                    config.queryParameters.ifExists = IfExists.forValue(artifact.getIfExists().value());
                    config.queryParameters.canonical = canonicalize;
                    config.headers.add("Content-Type", ContentTypes.APPLICATION_CREATE_EXTENDED);
                    if (artifactId != null) {
                        config.headers.add("X-Registry-ArtifactId", artifactId);
                    }
                    if (type != null) {
                        config.headers.add("X-Registry-ArtifactType", type);
                    }
                    if (version != null) {
                        config.headers.add("X-Registry-Version", version);
                    }
                });

                // client.createArtifact(groupId, artifactId, version, type, ifExists, canonicalize, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, artifactContent, references);
        log.info(String.format("Successfully registered artifact [%s] / [%s].  GlobalId is [%d]", groupId, artifactId, amd.getGlobalId()));

        return amd;
    }
}
