package io.apicurio.registry.maven;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ProtobufDirectoryParser {

    private static final String PROTO_SCHEMA_EXTENSION = ".proto";
    private static final Logger log = LoggerFactory.getLogger(ProtobufDirectoryParser.class);

    public static DescriptorWrapper parse(File protoFile) {

        Set<File> protoFiles = Arrays.stream(Objects.requireNonNull(protoFile.getParentFile().listFiles((dir, name) -> name.endsWith(PROTO_SCHEMA_EXTENSION))))
                .filter(file -> !file.getName().equals(protoFile.getName()))
                .collect(Collectors.toSet());

        Map<String, Descriptors.FileDescriptor> parsedFiles = new HashMap<>();
        Map<String, String> schemaDefs = new HashMap<>();

        // Add file to set of parsed files to avoid circular dependencies
        while (parsedFiles.size() != protoFiles.size()) {
            for (File fileToProcess : protoFiles) {
                if (fileToProcess.getName().equals(protoFile.getName()) || parsedFiles.containsKey(fileToProcess.getName())) {
                    continue;
                }
                try {
                    final ContentHandle schemaContent = readProtoFile(fileToProcess);
                    parsedFiles.put(fileToProcess.getName(), parseProtoFile(fileToProcess, schemaDefs, parsedFiles, schemaContent));
                    schemaDefs.put(fileToProcess.getName(), schemaContent.content());
                } catch (Exception ex) {
                    log.warn("Error processing Avro schema with name {}. This usually means that the references are not ready yet to parse it", fileToProcess.getName());
                }
            }
        }

        //parse the main schema
        final ContentHandle schemaContent = readProtoFile(protoFile);
        final Descriptors.FileDescriptor schemaDescriptor = parseProtoFile(protoFile, schemaDefs, parsedFiles, schemaContent);
        return new DescriptorWrapper(schemaDescriptor, schemaDefs);
    }

    private static ContentHandle readProtoFile(File schemaFile) {
        try {
            return ContentHandle.create(Files.readAllBytes(schemaFile.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + schemaFile, e);
        }
    }

    private static Descriptors.FileDescriptor parseProtoFile(File protoFile, Map<String, String> schemaDefs, Map<String, Descriptors.FileDescriptor> dependencies, ContentHandle schemaContent) {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(Location.get(protoFile.getAbsolutePath()), schemaContent.content());
        try {
            return FileDescriptorUtils.protoFileToFileDescriptor(schemaContent.content(), protoFile.getName(), Optional.ofNullable(protoFileElement.getPackageName()), schemaDefs, dependencies);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new RuntimeException("Failed to read schema file: " + protoFile, e);
        }
    }

    public static class DescriptorWrapper {
        final Descriptors.FileDescriptor fileDescriptor;
        final Map<String, String> fileContents; //used to store the original file content to register the content as-is.

        public DescriptorWrapper(Descriptors.FileDescriptor fileDescriptor, Map<String, String> fileContents) {
            this.fileDescriptor = fileDescriptor;
            this.fileContents = fileContents;
        }

        public Descriptors.FileDescriptor getFileDescriptor() {
            return fileDescriptor;
        }

        public Map<String, String> getFileContents() {
            return fileContents;
        }
    }
}

