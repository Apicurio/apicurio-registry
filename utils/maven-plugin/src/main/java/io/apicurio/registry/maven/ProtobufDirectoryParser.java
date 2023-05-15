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

    public static Descriptors.FileDescriptor parse(File protoFile) throws IOException, Descriptors.DescriptorValidationException {

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
            return schemaDescriptor;
        } catch (Exception ex) {

        }

        return null;
    }


    private static Descriptors.FileDescriptor parseProtoFile(File protoFile, Map<String, String> schemaDefs, Map<String, Descriptors.FileDescriptor> dependencies, String schemaContent) throws IOException, Descriptors.DescriptorValidationException {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(Location.get(protoFile.getAbsolutePath()), schemaContent);
        return FileDescriptorUtils.protoFileToFileDescriptor(schemaContent, protoFile.getName(), Optional.ofNullable(protoFileElement.getPackageName()), schemaDefs, dependencies);
    }
}

