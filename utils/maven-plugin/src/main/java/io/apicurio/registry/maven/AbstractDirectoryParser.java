/*
 * Copyright 2023 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public abstract class AbstractDirectoryParser<Schema> {

    private final RegistryClient client;

    public AbstractDirectoryParser(RegistryClient client) {
        this.client = client;
    }

    private static final Logger log = LoggerFactory.getLogger(AbstractDirectoryParser.class);

    public abstract ParsedDirectoryWrapper<Schema> parse(File rootSchema);

    public abstract List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, Schema schema, Map<String, ContentHandle> fileContents) throws FileNotFoundException;

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

    protected ArtifactReference registerNestedSchema(String referenceName, List<ArtifactReference> nestedArtifactReferences, RegisterArtifact nestedSchema, String artifactContent) throws FileNotFoundException {
        ArtifactMetaData referencedArtifactMetadata = registerArtifact(nestedSchema, IoUtil.toStream(artifactContent), nestedArtifactReferences);
        ArtifactReference referencedArtifact = new ArtifactReference();
        referencedArtifact.setName(referenceName);
        referencedArtifact.setArtifactId(referencedArtifactMetadata.getId());
        referencedArtifact.setGroupId(referencedArtifactMetadata.getGroupId());
        referencedArtifact.setVersion(referencedArtifactMetadata.getVersion());
        return referencedArtifact;
    }

    private ArtifactMetaData registerArtifact(RegisterArtifact artifact, InputStream artifactContent, List<ArtifactReference> references) {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String type = artifact.getType();
        IfExists ifExists = artifact.getIfExists();
        Boolean canonicalize = artifact.getCanonicalize();
        if (artifact.getMinify() != null && artifact.getMinify()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readValue(artifactContent, JsonNode.class);
                artifactContent = new ByteArrayInputStream(jsonNode.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ArtifactMetaData amd = client.createArtifact(groupId, artifactId, version, type, ifExists, canonicalize, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, artifactContent, references);
        log.info(String.format("Successfully registered artifact [%s] / [%s].  GlobalId is [%d]", groupId, artifactId, amd.getGlobalId()));

        return amd;
    }
}
