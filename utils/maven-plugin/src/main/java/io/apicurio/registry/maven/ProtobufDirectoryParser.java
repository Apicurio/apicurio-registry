package io.apicurio.registry.maven;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ProtobufDirectoryParser extends AbstractDirectoryParser<Descriptors.FileDescriptor> {

    private static final String PROTO_SCHEMA_EXTENSION = ".proto";
    private static final Logger log = LoggerFactory.getLogger(ProtobufDirectoryParser.class);

    public ProtobufDirectoryParser(RegistryClient client) {
        super(client);
    }

    @Override
    public ParsedDirectoryWrapper<Descriptors.FileDescriptor> parse(File protoFile) {

        Set<File> protoFiles = Arrays.stream(Objects.requireNonNull(protoFile.getParentFile().listFiles((dir, name) -> name.endsWith(PROTO_SCHEMA_EXTENSION))))
                .filter(file -> !file.getName().equals(protoFile.getName()))
                .collect(Collectors.toSet());

        try {
            final Map<String, String> requiredSchemaDefs = new HashMap<>();
            final Descriptors.FileDescriptor schemaDescriptor = FileDescriptorUtils.parseProtoFileWithDependencies(protoFile, protoFiles, requiredSchemaDefs);
            assert allDependenciesHaveSamePackageName(requiredSchemaDefs, schemaDescriptor.getPackage()) : "All dependencies must have the same package name as the main proto file";
            Map<String, ContentHandle> schemaContents = convertSchemaDefs(requiredSchemaDefs, schemaDescriptor.getPackage());
            return new DescriptorWrapper(schemaDescriptor, schemaContents);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new RuntimeException("Failed to read schema file: " + protoFile, e);
        } catch (FileDescriptorUtils.ReadSchemaException e) {
            log.warn("Error processing Avro schema with name {}. This usually means that the references are not ready yet to read it", e.file());
            throw new RuntimeException(e.getCause());
        } catch (FileDescriptorUtils.ParseSchemaException e) {
            log.warn("Error processing Avro schema with name {}. This usually means that the references are not ready yet to parse it", e.fileName());
            throw new RuntimeException(e.getCause());
        }
    }

    private static boolean allDependenciesHaveSamePackageName(Map<String, String> schemas, String mainProtoPackageName) {
           return schemas.keySet().stream().allMatch(fullDepName -> fullDepName.contains(mainProtoPackageName));
    }

    /**
     * Converts the schema definitions to a map of ContentHandle, stripping any package information from the key,
     * which is not needed for the schema registry, given that the dependent schemas are *always* in the same package
     * of the main proto file.
     */
    private Map<String, ContentHandle> convertSchemaDefs(Map<String, String> requiredSchemaDefs, String mainProtoPackageName) {
        if (requiredSchemaDefs.isEmpty()) {
            return Map.of();
        }
        Map<String, ContentHandle> schemaDefs = new HashMap<>(requiredSchemaDefs.size());
        for (Map.Entry<String, String> entry : requiredSchemaDefs.entrySet()) {
            if (schemaDefs.put(FileDescriptorUtils.extractProtoFileName(entry.getKey()),
                    ContentHandle.create(entry.getValue())) != null) {
                log.warn("There's a clash of dependency name, likely due to stripping the expected package name ie {}: dependencies: {}",
                        mainProtoPackageName, Arrays.toString(requiredSchemaDefs.keySet().toArray(new Object[0])));
            }
        }
        return schemaDefs;
    }

    @Override
    public List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, Descriptors.FileDescriptor protoSchema, Map<String, ContentHandle> fileContents) throws FileNotFoundException, InterruptedException, ExecutionException {
        Set<ArtifactReference> references = new HashSet<>();
        final Set<Descriptors.FileDescriptor> baseDeps = new HashSet<>(Arrays.asList(FileDescriptorUtils.baseDependencies()));
        final ProtoFileElement rootSchemaElement = FileDescriptorUtils.fileDescriptorToProtoFile(protoSchema.toProto());

        for (Descriptors.FileDescriptor dependency : protoSchema.getDependencies()) {

            List<ArtifactReference> nestedArtifactReferences = new ArrayList<>();
            String dependencyFullName = dependency.getPackage() + "/" + dependency.getName(); //FIXME find a better wat to do this
            if (!baseDeps.contains(dependency) && rootSchemaElement.getImports().contains(dependencyFullName)) {

                RegisterArtifact nestedArtifact = buildFromRoot(rootArtifact, dependencyFullName);

                if (!dependency.getDependencies().isEmpty()) {
                    nestedArtifactReferences = handleSchemaReferences(nestedArtifact, dependency, fileContents);
                }

                references.add(registerNestedSchema(dependencyFullName, nestedArtifactReferences, nestedArtifact, fileContents.get(dependency.getName()).content()));
            }
        }

        return new ArrayList<>(references);
    }

    public static class DescriptorWrapper implements ParsedDirectoryWrapper<Descriptors.FileDescriptor> {
        final Descriptors.FileDescriptor fileDescriptor;
        final Map<String, ContentHandle> schemaContents; //used to store the original file content to register the content as-is.

        public DescriptorWrapper(Descriptors.FileDescriptor fileDescriptor, Map<String, ContentHandle> schemaContents) {
            this.fileDescriptor = fileDescriptor;
            this.schemaContents = schemaContents;
        }

        @Override
        public Descriptors.FileDescriptor getSchema() {
            return fileDescriptor;
        }

        public Map<String, ContentHandle> getSchemaContents() {
            return schemaContents;
        }
    }
}

