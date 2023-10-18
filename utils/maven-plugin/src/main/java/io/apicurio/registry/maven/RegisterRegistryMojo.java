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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import org.apache.avro.Schema;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.maven.refs.IndexedResource;
import io.apicurio.registry.maven.refs.ReferenceIndex;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;

/**
 * Register artifacts against registry.
 *
 * @author Ales Justin
 */
@Mojo(name = "register")
public class RegisterRegistryMojo extends AbstractRegistryMojo {

    /**
     * The list of pre-registered artifacts that can be used as references.
     */
    @Parameter(required = true)
    List<ExistingReference> existingReferences;

    /**
     * The list of artifacts to register.
     */
    @Parameter(required = true)
    List<RegisterArtifact> artifacts;
    
    DefaultArtifactTypeUtilProviderImpl utilProviderFactory = new DefaultArtifactTypeUtilProviderImpl();

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
                    if (artifact.getAutoRefs() != null && artifact.getAutoRefs()) {
                        // If we have references, then we'll need to create the local resource index and then process all refs.
                        ReferenceIndex index = createIndex(artifact.getFile());
                        addExistingReferencesToIndex(index, existingReferences);
                        addExistingReferencesToIndex(index, artifact.getExistingReferences());
                        Stack<RegisterArtifact> registrationStack = new Stack<>();

                        registerWithAutoRefs(artifact, index, registrationStack);
                    } else if (artifact.getAnalyzeDirectory() != null && artifact.getAnalyzeDirectory()) { //Auto register selected, we must figure out if the artifact has reference using the directory structure
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

    private ArtifactMetaData registerWithAutoRefs(RegisterArtifact artifact, ReferenceIndex index, Stack<RegisterArtifact> registrationStack) throws IOException {
        if (loopDetected(artifact, registrationStack)) {
            throw new RuntimeException("Artifact reference loop detected (not supported): " + printLoop(registrationStack));
        }
        registrationStack.push(artifact);
        
        // Read the artifact content.
        ContentHandle artifactContent = readContent(artifact.getFile());
        
        // Find all references in the content
        ArtifactTypeUtilProvider provider = this.utilProviderFactory.getArtifactTypeProvider(artifact.getType());
        ReferenceFinder referenceFinder = provider.getReferenceFinder();
        Set<ExternalReference> externalReferences = referenceFinder.findExternalReferences(artifactContent);
        
        // Register all of the references first, then register the artifact.
        List<ArtifactReference> registeredReferences = externalReferences.stream().map(externalRef -> {
            IndexedResource iresource = index.lookup(externalRef.getResource(), Paths.get(artifact.getFile().toURI()));
            
            // TODO: need a way to resolve references that are not local (already registered in the registry)
            if (iresource == null) {
                throw new RuntimeException("Reference could not be resolved.  From: " + artifact.getFile().getName() + "  To: " + externalRef.getFullReference());
            }
            
            // If the resource isn't already registered, then register it now.
            if (!iresource.isRegistered()) {
                // TODO: determine the artifactId better (type-specific logic here?)
                String artifactId = externalRef.getResource();
                File localFile = getLocalFile(iresource.getPath());
                RegisterArtifact refArtifact = buildFromRoot(artifact, artifactId);
                refArtifact.setType(iresource.getType());
                refArtifact.setVersion(null);
                refArtifact.setFile(localFile);
                refArtifact.setContentType(getContentTypeByExtension(localFile.getName()));
                try {
                    ArtifactMetaData amd = registerWithAutoRefs(refArtifact, index, registrationStack);
                    iresource.setRegistration(amd);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return new ArtifactReference(
                    iresource.getRegistration().getGroupId(), 
                    iresource.getRegistration().getId(),
                    iresource.getRegistration().getVersion(),
                    externalRef.getFullReference());

        }).sorted((ref1, ref2) -> ref1.getName().compareTo(ref2.getName())).collect(Collectors.toList());

        registrationStack.pop();
        return registerArtifact(artifact, registeredReferences);
    }

    private void registerDirectory(RegisterArtifact artifact) throws IOException {
        switch (artifact.getType()) {
            case ArtifactType.AVRO:
                final AvroDirectoryParser avroDirectoryParser = new AvroDirectoryParser(getClient());
                final ParsedDirectoryWrapper<Schema> schema = avroDirectoryParser.parse(artifact.getFile());
                registerArtifact(artifact, avroDirectoryParser.handleSchemaReferences(artifact, schema.getSchema(), schema.getSchemaContents()));
                break;
            case ArtifactType.PROTOBUF:
                final ProtobufDirectoryParser protobufDirectoryParser = new ProtobufDirectoryParser(getClient());
                final ParsedDirectoryWrapper<FileDescriptor> protoSchema = protobufDirectoryParser.parse(artifact.getFile());
                registerArtifact(artifact, protobufDirectoryParser.handleSchemaReferences(artifact, protoSchema.getSchema(), protoSchema.getSchemaContents()));
                break;
            case ArtifactType.JSON:
                final JsonSchemaDirectoryParser jsonSchemaDirectoryParser = new JsonSchemaDirectoryParser(getClient());
                final ParsedDirectoryWrapper<org.everit.json.schema.Schema> jsonSchema = jsonSchemaDirectoryParser.parse(artifact.getFile());
                registerArtifact(artifact, jsonSchemaDirectoryParser.handleSchemaReferences(artifact, jsonSchema.getSchema(), jsonSchema.getSchemaContents()));
                break;
            default:
                throw new IllegalArgumentException(String.format("Artifact type not recognized for analyzing a directory structure %s", artifact.getType()));
        }
    }

    private ArtifactMetaData registerArtifact(RegisterArtifact artifact, List<ArtifactReference> references) throws
            FileNotFoundException {
        return registerArtifact(artifact, new FileInputStream(artifact.getFile()), references);
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
        ArtifactMetaData amd = this.getClient().createArtifact(groupId, artifactId, version, type, ifExists, canonicalize, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, artifactContent, references);
        getLog().info(String.format("Successfully registered artifact [%s] / [%s].  GlobalId is [%d]", groupId, artifactId, amd.getGlobalId()));

        return amd;
    }

    private static boolean hasReferences(RegisterArtifact artifact) {
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

    public void setArtifacts(List<RegisterArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    private static ArtifactReference buildReferenceFromMetadata(ArtifactMetaData amd, String referenceName) {
        ArtifactReference reference = new ArtifactReference();
        reference.setName(referenceName);
        reference.setArtifactId(amd.getId());
        reference.setGroupId(amd.getGroupId());
        reference.setVersion(amd.getVersion());
        return reference;
    }

    /**
     * Create a local index relative to the given file location.
     * @param file
     */
    private static ReferenceIndex createIndex(File file) {
        ReferenceIndex index = new ReferenceIndex(file.getParentFile().toPath());
        Collection<File> allFiles = FileUtils.listFiles(file.getParentFile(), null, true);
        allFiles.stream().filter(f -> f.isFile()).forEach(f -> {
            index.index(f.toPath(), readContent(f));
        });
        return index;
    }

    private void addExistingReferencesToIndex(ReferenceIndex index, List<ExistingReference> existingReferences) {
        if (existingReferences != null && !existingReferences.isEmpty()) {
            existingReferences.forEach(ref -> {
                ArtifactMetaData amd;
                if (ref.getVersion() == null || "LATEST".equalsIgnoreCase(ref.getVersion())) {
                    amd = getClient().getArtifactMetaData(ref.getGroupId(), ref.getArtifactId());
                } else {
                    amd = new ArtifactMetaData();
                    amd.setGroupId(ref.getGroupId());
                    amd.setId(ref.getArtifactId());
                    amd.setVersion(ref.getVersion());
                }
                index.index(ref.getResourceName(), amd);
            });
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
        nestedSchema.setType(rootArtifact.getType());
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
     * @param artifact
     * @param registrationStack
     */
    private static boolean loopDetected(RegisterArtifact artifact, Stack<RegisterArtifact> registrationStack) {
        for (RegisterArtifact stackArtifact : registrationStack) {
            if (artifact.getFile().equals(stackArtifact.getFile())) {
                return true;
            }
        }
        return false;
    }

    private static String printLoop(Stack<RegisterArtifact> registrationStack) {
        return registrationStack.stream().map(artifact -> artifact.getFile().getName()).collect(Collectors.joining(" -> "));
    }

}
