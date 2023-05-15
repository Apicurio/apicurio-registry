/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.protobuf.Descriptors;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Register artifacts against registry.
 *
 * @author Ales Justin
 */
@Mojo(name = "register")
public class RegisterRegistryMojo extends AbstractRegistryMojo {

    /**
     * The list of artifacts to register.
     */
    @Parameter(required = true)
    List<RegisterArtifact> artifacts;

    /**
     * Validate the configuration.
     */
    protected void validate() throws MojoExecutionException {
        if (artifacts == null || artifacts.isEmpty()) {
            getLog().warn("No artifacts are configured for registration.");
        } else {
            int idx = 0;
            int errorCount = 0;
            for (RegisterArtifact artifact : artifacts) {
                if (artifact.getGroupId() == null) {
                    getLog().error(String.format("GroupId is required when registering an artifact.  Missing from artifacts[%d].", idx));
                    errorCount++;
                }
                if (artifact.getArtifactId() == null) {
                    getLog().error(String.format("ArtifactId is required when registering an artifact.  Missing from artifacts[%s].", idx));
                    errorCount++;
                }
                if (artifact.getFile() == null) {
                    getLog().error(String.format("File is required when registering an artifact.  Missing from artifacts[%s].", idx));
                    errorCount++;
                } else if (!artifact.getFile().exists()) {
                    getLog().error(String.format("Artifact file to register is configured but file does not exist: %s", artifact.getFile().getPath()));
                    errorCount++;
                }

                idx++;
            }

            if (errorCount > 0) {
                throw new MojoExecutionException("Invalid configuration of the Register Artifact(s) mojo. See the output log for details.");
            }
        }
    }

    @Override
    protected void executeInternal() throws MojoExecutionException {
        validate();

        int errorCount = 0;
        if (artifacts != null) {

            for (RegisterArtifact artifact : artifacts) {
                String groupId = artifact.getGroupId();
                String artifactId = artifact.getArtifactId();
                try {

                    if (artifact.getAnalyzeDirectory()) { //Auto register selected, we must figure out if the artifact has reference using the directory structure
                        registerDirectory(artifact);
                    } else {

                        List<ArtifactReference> references = new ArrayList<>();
                        //First, we check if the artifact being processed has references defined
                        if (hasReferences(artifact)) {
                            references = registerArtifactReferences(artifact.getReferences());
                        }
                        registerArtifact(artifact, references);
                    }
                } catch (Exception e) {
                    errorCount++;
                    getLog().error(String.format("Exception while registering artifact [%s] / [%s]", groupId, artifactId), e);
                }

            }

            if (errorCount > 0) {
                throw new MojoExecutionException("Errors while registering artifacts ...");
            }
        }
    }

    private void registerDirectory(RegisterArtifact artifact) throws IOException, Descriptors.DescriptorValidationException {
        switch (artifact.getType()) {
            case ArtifactType.AVRO -> {
                final Schema schema = AvroDirectoryParser.parse(artifact.getFile());
                registerArtifact(artifact, handleSchemaReferences(artifact, schema));
            }
            case ArtifactType.PROTOBUF -> {
                final Descriptors.FileDescriptor protoSchema = ProtobufDirectoryParser.parse(artifact.getFile());

            }
            case ArtifactType.JSON -> {

            }
        }
    }



    private List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, Schema rootSchema) throws FileNotFoundException {

        List<ArtifactReference> references = new ArrayList<>();

        //Iterate through all the fields of the schema
        for (Schema.Field field : rootSchema.getFields()) {
            List<ArtifactReference> nestedArtifactReferences = new ArrayList<>();
            if (field.schema().getType() == Schema.Type.RECORD) { //If the field is a sub-schema, recursively check for nested sub-schemas and register all of them

                RegisterArtifact nestedSchema = buildFromRoot(rootArtifact, field.schema());

                if (field.schema().hasFields()) {
                    nestedArtifactReferences = handleSchemaReferences(nestedSchema, field.schema());
                }

                registerNestedSchema(references, field.schema(), nestedArtifactReferences, nestedSchema);
            } else if (field.schema().getType() == Schema.Type.ENUM) { //If the nested schema is an enum, just register

                RegisterArtifact nestedSchema = buildFromRoot(rootArtifact, field.schema());
                registerNestedSchema(references, field.schema(), nestedArtifactReferences, nestedSchema);
            } else if (isArrayWithSubschemaElement(field)) { //If the nested schema is an array and the element is a sub-schema, handle it

                Schema elementSchema = field.schema().getElementType();

                RegisterArtifact nestedSchema = buildFromRoot(rootArtifact, elementSchema);

                if (elementSchema.hasFields()) {
                    nestedArtifactReferences = handleSchemaReferences(nestedSchema, elementSchema);
                }

                registerNestedSchema(references, elementSchema,  nestedArtifactReferences, nestedSchema);
            }
        }
        return references;
    }

    private boolean isArrayWithSubschemaElement(Schema.Field field) {
        return field.schema().getType() == Schema.Type.ARRAY && field.schema().getElementType().getType() == Schema.Type.RECORD;
    }

    private RegisterArtifact buildFromRoot(RegisterArtifact rootArtifact, Schema schema) {
        RegisterArtifact nestedSchema = new RegisterArtifact();
        nestedSchema.setCanonicalize(rootArtifact.getCanonicalize());
        nestedSchema.setArtifactId(schema.getFullName());
        nestedSchema.setGroupId(rootArtifact.getGroupId());
        nestedSchema.setContentType(rootArtifact.getContentType());
        nestedSchema.setType(rootArtifact.getType());
        nestedSchema.setMinify(rootArtifact.getMinify());
        nestedSchema.setContentType(rootArtifact.getContentType());
        nestedSchema.setIfExists(rootArtifact.getIfExists());

        return nestedSchema;
    }

    private void registerNestedSchema(List<ArtifactReference> references, Schema schema, List<ArtifactReference> nestedArtifactReferences, RegisterArtifact nestedSchema) throws FileNotFoundException {
        ArtifactMetaData referencedArtifactMetadata = registerArtifact(nestedSchema, IoUtil.toStream(schema.toString()), nestedArtifactReferences);
        ArtifactReference referencedArtifact = new ArtifactReference();
        referencedArtifact.setName(schema.getFullName());
        referencedArtifact.setArtifactId(referencedArtifactMetadata.getId());
        referencedArtifact.setGroupId(referencedArtifactMetadata.getGroupId());
        referencedArtifact.setVersion(referencedArtifactMetadata.getVersion());
        references.add(referencedArtifact);
    }

    private ArtifactMetaData registerArtifact(RegisterArtifact artifact, List<ArtifactReference> references) throws
            FileNotFoundException {
        return registerArtifact(artifact, new FileInputStream(artifact.getFile()), references);

    }

    private ArtifactMetaData registerArtifact(RegisterArtifact artifact, InputStream artifactContent, List<ArtifactReference> references) throws FileNotFoundException {
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
        ArtifactMetaData amd = this.getClient().createArtifact(groupId, artifactId, version, type, ifExists, canonicalize, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, artifactContent, references);
        getLog().info(String.format("Successfully registered artifact [%s] / [%s].  GlobalId is [%d]", groupId, artifactId, amd.getGlobalId()));

        return amd;
    }


    private boolean hasReferences(RegisterArtifact artifact) {
        return artifact.getReferences() != null && !artifact.getReferences().isEmpty();
    }

    private List<ArtifactReference> registerArtifactReferences
            (List<RegisterArtifactReference> referencedArtifacts) throws FileNotFoundException {
        List<ArtifactReference> references = new ArrayList<>();
        for (RegisterArtifactReference artifact : referencedArtifacts) {
            List<ArtifactReference> nestedReferences = new ArrayList<>();
            //First, we check if the artifact being processed has references defined, and register them if needed
            if (hasReferences(artifact)) {
                nestedReferences = registerArtifactReferences(artifact.getReferences());
            }
            final ArtifactMetaData artifactMetaData = registerArtifact(artifact, nestedReferences);
            references.add(buildReferenceFromMetadata(artifactMetaData, artifact.getName()));
        }
        return references;
    }

    private ArtifactReference buildReferenceFromMetadata(ArtifactMetaData amd, String referenceName) {
        ArtifactReference reference = new ArtifactReference();
        reference.setName(referenceName);
        reference.setArtifactId(amd.getId());
        reference.setGroupId(amd.getGroupId());
        reference.setVersion(amd.getVersion());
        return reference;
    }

    private String contentType(RegisterArtifact registerArtifact) {
        String contentType = registerArtifact.getContentType();
        if (contentType != null) {
            return contentType;
        }
        return getContentTypeByExtension(registerArtifact.getFile().getName());
    }

    public void setArtifacts(List<RegisterArtifact> artifacts) {
        this.artifacts = artifacts;
    }
}
