package io.apicurio.registry.maven;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
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
import java.util.Optional;
import java.util.Set;
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

        Map<String, Descriptors.FileDescriptor> parsedFiles = new HashMap<>();
        Map<String, ContentHandle> schemaDefs = new HashMap<>();

        // Add file to set of parsed files to avoid circular dependencies
        while (parsedFiles.size() != protoFiles.size()) {
            boolean fileParsed = false;
            for (File fileToProcess : protoFiles) {
                if (fileToProcess.getName().equals(protoFile.getName()) || parsedFiles.containsKey(fileToProcess.getName())) {
                    continue;
                }
                try {
                    final ContentHandle schemaContent = readSchemaContent(fileToProcess);
                    parsedFiles.put(fileToProcess.getName(), parseProtoFile(fileToProcess, schemaDefs, parsedFiles, schemaContent));
                    schemaDefs.put(fileToProcess.getName(), schemaContent);
                    fileParsed = true;
                } catch (Exception ex) {
                    log.warn("Error processing Avro schema with name {}. This usually means that the references are not ready yet to parse it", fileToProcess.getName());
                }
            }

            //If no schema has been processed during this iteration, that means there is an error in the configuration, throw exception.
            if (!fileParsed) {
                throw new IllegalStateException("Error found in the directory structure. Check that all required files are present.");
            }
        }

        //parse the main schema
        final ContentHandle schemaContent = readSchemaContent(protoFile);
        final Descriptors.FileDescriptor schemaDescriptor = parseProtoFile(protoFile, schemaDefs, parsedFiles, schemaContent);
        return new DescriptorWrapper(schemaDescriptor, schemaDefs);
    }

    @Override
    public List<ArtifactReference> handleSchemaReferences(RegisterArtifact rootArtifact, Descriptors.FileDescriptor protoSchema, Map<String, ContentHandle> fileContents) throws FileNotFoundException {
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

    private Descriptors.FileDescriptor parseProtoFile(File protoFile, Map<String, ContentHandle> schemaDefs, Map<String, Descriptors.FileDescriptor> dependencies, ContentHandle schemaContent) {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(Location.get(protoFile.getAbsolutePath()), schemaContent.content());
        try {

            final Map<String, String> schemaStrings = schemaDefs.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue().content()));

            return FileDescriptorUtils.protoFileToFileDescriptor(schemaContent.content(), protoFile.getName(), Optional.ofNullable(protoFileElement.getPackageName()), schemaStrings, dependencies);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new RuntimeException("Failed to read schema file: " + protoFile, e);
        }
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

