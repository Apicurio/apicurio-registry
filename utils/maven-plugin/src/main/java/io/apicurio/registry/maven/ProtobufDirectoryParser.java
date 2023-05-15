package io.apicurio.registry.maven;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ProtobufDirectoryParser {

    private static final String PROTO_SCHEMA_EXTENSION = ".proto";

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
                    FileInputStream fis = new FileInputStream(fileToProcess);
                    final String schemaContent = IoUtil.toString(fis);
                    parsedFiles.put(fileToProcess.getName(), parseProtoFile(fileToProcess, schemaDefs, parsedFiles, schemaContent));
                    schemaDefs.put(fileToProcess.getName(), schemaContent);
                    fis.close();
                } catch (Exception ex) {
                    System.out.println("");
                    //Just ignore, the schema cannot be parsed yet.
                }
            }
        }

        //parse the main schema
        try {
            FileInputStream fis = new FileInputStream(protoFile);
            final String schemaContent = IoUtil.toString(fis);
            final Descriptors.FileDescriptor schemaDescriptor = parseProtoFile(protoFile, schemaDefs, parsedFiles, schemaContent);
            fis.close();
            return new DescriptorWrapper(schemaDescriptor, schemaDefs);
        } catch (Exception ex) {
            System.out.println("");
            //TODO log exception
        }

        return null;
    }


    private static Descriptors.FileDescriptor parseProtoFile(File protoFile, Map<String, String> schemaDefs, Map<String, Descriptors.FileDescriptor> dependencies, String schemaContent) throws Descriptors.DescriptorValidationException {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(Location.get(protoFile.getAbsolutePath()), schemaContent);
        return FileDescriptorUtils.protoFileToFileDescriptor(schemaContent, protoFile.getName(), Optional.ofNullable(protoFileElement.getPackageName()), schemaDefs, dependencies);
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

