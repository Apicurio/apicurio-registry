package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.type.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.google.protobuf.DescriptorProtos.*;

/**
 * Utilities for working with Protobuf FileDescriptors.
 *
 * NOTE: This class has been refactored to use protobuf4j instead of wire-schema.
 * Some methods that previously accepted/returned wire-schema types (ProtoFileElement, etc.)
 * have been removed or marked as requiring protobuf4j AST support.
 *
 */
public class FileDescriptorUtils {

    private static final FileDescriptor[] WELL_KNOWN_DEPENDENCIES;

    static {
        // Support all the Protobuf WellKnownTypes
        // and the protos from Google API, https://github.com/googleapis/googleapis
        // TODO: Re-enable custom types (metadata, decimal) when proto compilation is set up
        WELL_KNOWN_DEPENDENCIES = new FileDescriptor[]{ ApiProto.getDescriptor().getFile(),
                FieldMaskProto.getDescriptor().getFile(), SourceContextProto.getDescriptor().getFile(),
                StructProto.getDescriptor().getFile(), TypeProto.getDescriptor().getFile(),
                TimestampProto.getDescriptor().getFile(), WrappersProto.getDescriptor().getFile(),
                AnyProto.getDescriptor().getFile(), EmptyProto.getDescriptor().getFile(),
                DurationProto.getDescriptor().getFile(), TimeOfDayProto.getDescriptor().getFile(),
                DateProto.getDescriptor().getFile(), CalendarPeriodProto.getDescriptor().getFile(),
                ColorProto.getDescriptor().getFile(), DayOfWeek.getDescriptor().getFile(),
                LatLng.getDescriptor().getFile(), FractionProto.getDescriptor().getFile(),
                MoneyProto.getDescriptor().getFile(), MonthProto.getDescriptor().getFile(),
                PhoneNumberProto.getDescriptor().getFile(), PostalAddressProto.getDescriptor().getFile(),
                CalendarPeriodProto.getDescriptor().getFile(), LocalizedTextProto.getDescriptor().getFile(),
                IntervalProto.getDescriptor().getFile(), ExprProto.getDescriptor().getFile(),
                QuaternionProto.getDescriptor().getFile(), PostalAddressProto.getDescriptor().getFile()
                // ProtobufSchemaMetadata.getDescriptor().getFile(), Decimals.getDescriptor().getFile()
        };
    }

    public static FileDescriptor[] baseDependencies() {
        return WELL_KNOWN_DEPENDENCIES.clone();
    }

    /**
     * Parse a self-contained descriptor proto with only the base dependencies.
     */
    public static FileDescriptor protoFileToFileDescriptor(FileDescriptorProto descriptorProto)
            throws DescriptorValidationException {
        Objects.requireNonNull(descriptorProto);
        return FileDescriptor.buildFrom(descriptorProto, baseDependencies());
    }

    private static Map<String, FileDescriptor> mutableBaseDependenciesByName(int ensureCapacity) {
        final Map<String, FileDescriptor> deps = new HashMap<>(
                WELL_KNOWN_DEPENDENCIES.length + ensureCapacity);
        for (FileDescriptor fd : WELL_KNOWN_DEPENDENCIES) {
            deps.put(fd.getName(), fd);
        }
        return deps;
    }

    /**
     * Compile a protobuf schema string to FileDescriptor using protobuf4j.
     * Uses the new buildFileDescriptors() method which handles dependency resolution automatically.
     */
    public static FileDescriptor protoFileToFileDescriptor(String schemaDefinition, String protoFileName,
                                                           Optional<String> optionalPackageName) throws DescriptorValidationException {
        Objects.requireNonNull(schemaDefinition);
        Objects.requireNonNull(protoFileName);

        try {
            // Use ProtobufSchemaLoader which now uses buildFileDescriptors() internally
            // This automatically handles all dependency resolution
            ProtobufSchemaLoader.ProtobufSchemaLoaderContext context = ProtobufSchemaLoader.loadSchema(
                    protoFileName, schemaDefinition, Collections.emptyMap());
            return context.getFileDescriptor();
        } catch (IOException e) {
            throw new RuntimeException("Failed to compile protobuf schema: " + protoFileName, e);
        }
    }

    /**
     * Compile a protobuf schema string to FileDescriptor with dependencies using protobuf4j.
     * Uses the new buildFileDescriptors() method which handles dependency resolution automatically.
     */
    public static FileDescriptor protoFileToFileDescriptor(String schemaDefinition, String protoFileName,
                                                           Optional<String> optionalPackageName, Map<String, String> schemaDefs,
                                                           Map<String, Descriptors.FileDescriptor> dependencies) throws DescriptorValidationException {
        Objects.requireNonNull(schemaDefinition);
        Objects.requireNonNull(protoFileName);

        try {
            // Use ProtobufSchemaLoader which now uses buildFileDescriptors() internally
            // This automatically handles all dependency resolution, including the ones passed in schemaDefs
            ProtobufSchemaLoader.ProtobufSchemaLoaderContext context = ProtobufSchemaLoader.loadSchema(
                    protoFileName, schemaDefinition, schemaDefs);
            return context.getFileDescriptor();
        } catch (IOException e) {
            throw new RuntimeException("Failed to compile protobuf schema: " + protoFileName, e);
        }
    }

    public static final class ReadSchemaException extends Exception {
        private final File file;

        private ReadSchemaException(File file, Throwable cause) {
            super(cause);
            this.file = file;
        }

        public File file() {
            return file;
        }
    }

    public static final class ParseSchemaException extends Exception {
        private final String fileName;

        private ParseSchemaException(String fileName, Throwable cause) {
            super(cause);
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }
    }

    /**
     * Parse a proto file with its dependencies using protobuf4j.
     */
    public static FileDescriptor parseProtoFileWithDependencies(File mainProtoFile, Set<File> dependencies)
            throws DescriptorValidationException, ReadSchemaException, ParseSchemaException {
        return parseProtoFileWithDependencies(mainProtoFile, dependencies, null, true);
    }

    /**
     * Parse a proto file with its dependencies using protobuf4j.
     */
    public static FileDescriptor parseProtoFileWithDependencies(File mainProtoFile, Set<File> dependencies,
                                                                Map<String, String> requiredSchemaDeps)
            throws ReadSchemaException, DescriptorValidationException, ParseSchemaException {
        return parseProtoFileWithDependencies(mainProtoFile, dependencies, requiredSchemaDeps, true);
    }

    /**
     * Parse a proto file with its dependencies using protobuf4j.
     */
    public static FileDescriptor parseProtoFileWithDependencies(File mainProtoFile, Set<File> dependencies,
                                                                Map<String, String> requiredSchemaDeps, boolean failFast)
            throws DescriptorValidationException, ReadSchemaException, ParseSchemaException {
        Objects.requireNonNull(mainProtoFile);
        Objects.requireNonNull(dependencies);

        try {
            // Create temp directory
            Path tempDir = Files.createTempDirectory("protobuf-parse");

            try {
                // Write main proto file
                String mainContent = new String(Files.readAllBytes(mainProtoFile.toPath()), StandardCharsets.UTF_8);

                // Write dependencies
                Map<String, String> depMap = new HashMap<>();

                if (!failFast) {
                    // When failFast=false, use multi-pass validation to handle dependency order
                    // Read all dependency contents first
                    // Note: We need to extract the relative path for proto files (e.g., "mypackage0/producerId.proto")
                    Path mainDir = mainProtoFile.toPath().getParent();
                    Map<String, String> allDepContents = new HashMap<>();
                    for (File depFile : dependencies) {
                        try {
                            String depContent = new String(Files.readAllBytes(depFile.toPath()), StandardCharsets.UTF_8);
                            // Get relative path from main proto file directory
                            String relativePath = mainDir.relativize(depFile.toPath()).toString().replace('\\', '/');
                            allDepContents.put(relativePath, depContent);
                        } catch (IOException e) {
                            // Skip files that can't be read
                        }
                    }

                    // Multi-pass validation: keep trying until no more files can be validated
                    Set<String> remaining = new HashSet<>(allDepContents.keySet());
                    int previousSize;
                    do {
                        previousSize = depMap.size();
                        Iterator<String> iter = remaining.iterator();
                        while (iter.hasNext()) {
                            String relativePath = iter.next();
                            String content = allDepContents.get(relativePath);
                            try {
                                // Try to compile with currently valid dependencies
                                // Use the full relativePath to ensure correct file placement
                                ProtobufSchemaLoader.loadSchema(relativePath, content, depMap);
                                // Success - add to valid set and remove from remaining
                                depMap.put(relativePath, content);
                                iter.remove();
                            } catch (IOException validationError) {
                                // Will retry in next pass if other dependencies become available
                            }
                        }
                    } while (depMap.size() > previousSize && !remaining.isEmpty());
                } else {
                    // failFast=true, add all dependencies without pre-validation
                    Path mainDir = mainProtoFile.toPath().getParent();
                    for (File depFile : dependencies) {
                        try {
                            String depContent = new String(Files.readAllBytes(depFile.toPath()), StandardCharsets.UTF_8);
                            // Get relative path from main proto file directory
                            String relativePath = mainDir.relativize(depFile.toPath()).toString().replace('\\', '/');
                            depMap.put(relativePath, depContent);
                        } catch (IOException e) {
                            throw new ReadSchemaException(depFile, e);
                        }
                    }
                }

                // Use ProtobufSchemaLoader to compile
                try {
                    ProtobufSchemaLoader.ProtobufSchemaLoaderContext context =
                            ProtobufSchemaLoader.loadSchema(mainProtoFile.getName(), mainContent, depMap);

                    // Populate required dependencies if requested
                    if (requiredSchemaDeps != null) {
                        requiredSchemaDeps.clear();
                        requiredSchemaDeps.putAll(depMap);
                    }

                    return context.getFileDescriptor();
                } catch (IOException e) {
                    // Compilation error - wrap in ParseSchemaException
                    throw new ParseSchemaException(mainProtoFile.getName(), e);
                }
            } finally {
                // Note: No cleanup needed - ProtobufSchemaLoader uses ZeroFs which is auto-closed
            }
        } catch (IOException e) {
            throw new ReadSchemaException(mainProtoFile, e);
        }
    }

    public static final class ProtobufSchemaContent {
        private final String fileName;
        private final String schemaDefinition;

        private ProtobufSchemaContent(String fileName, String schemaDefinition) {
            Objects.requireNonNull(fileName);
            Objects.requireNonNull(schemaDefinition);
            this.fileName = fileName;
            this.schemaDefinition = schemaDefinition;
        }

        public String fileName() {
            return fileName;
        }

        public String schemaDefinition() {
            return schemaDefinition;
        }

        public static ProtobufSchemaContent of(String fileName, String schemaDefinition) {
            return new ProtobufSchemaContent(fileName, schemaDefinition);
        }
    }

    /**
     * Parse a proto file with its dependencies using protobuf4j.
     */
    public static FileDescriptor parseProtoFileWithDependencies(ProtobufSchemaContent mainProtoFile,
                                                                Collection<ProtobufSchemaContent> dependencies)
            throws DescriptorValidationException, ParseSchemaException {
        return parseProtoFileWithDependencies(mainProtoFile, dependencies, null, true);
    }

    /**
     * Parse a proto file with its dependencies using protobuf4j.
     */
    public static FileDescriptor parseProtoFileWithDependencies(ProtobufSchemaContent mainProtoFile,
                                                                Collection<ProtobufSchemaContent> dependencies,
                                                                Map<String, String> requiredSchemaDeps,
                                                                boolean failFast) throws DescriptorValidationException, ParseSchemaException {
        Objects.requireNonNull(mainProtoFile);
        Objects.requireNonNull(dependencies);

        try {
            Map<String, String> depMap = new HashMap<>();

            if (!failFast) {
                // When failFast=false, use multi-pass validation to handle dependency order
                Map<String, String> allDepContents = new HashMap<>();
                for (ProtobufSchemaContent dep : dependencies) {
                    allDepContents.put(dep.fileName(), dep.schemaDefinition());
                }

                // Multi-pass validation: keep trying until no more files can be validated
                Set<String> remaining = new HashSet<>(allDepContents.keySet());
                int previousSize;
                do {
                    previousSize = depMap.size();
                    Iterator<String> iter = remaining.iterator();
                    while (iter.hasNext()) {
                        String fileName = iter.next();
                        String content = allDepContents.get(fileName);
                        try {
                            // Try to compile with currently valid dependencies
                            // Use the full fileName to ensure correct file placement
                            ProtobufSchemaLoader.loadSchema(fileName, content, depMap);
                            // Success - add to valid set and remove from remaining
                            depMap.put(fileName, content);
                            iter.remove();
                        } catch (IOException validationError) {
                            // Will retry in next pass if other dependencies become available
                        }
                    }
                } while (depMap.size() > previousSize && !remaining.isEmpty());
            } else {
                // failFast=true, add all dependencies without pre-validation
                for (ProtobufSchemaContent dep : dependencies) {
                    depMap.put(dep.fileName(), dep.schemaDefinition());
                }
            }

            ProtobufSchemaLoader.ProtobufSchemaLoaderContext context =
                    ProtobufSchemaLoader.loadSchema(mainProtoFile.fileName(), mainProtoFile.schemaDefinition(), depMap);

            if (requiredSchemaDeps != null) {
                requiredSchemaDeps.clear();
                requiredSchemaDeps.putAll(depMap);
            }

            return context.getFileDescriptor();
        } catch (IOException e) {
            throw new ParseSchemaException(mainProtoFile.fileName(), e);
        }
    }

    /**
     * Extract the proto file name from a full proto file name (packageName/fileName).
     */
    public static String extractProtoFileName(String protoFullName) {
        int beforeStartFileName = protoFullName.lastIndexOf('/');
        final String fileName;
        if (beforeStartFileName != -1) {
            fileName = protoFullName.substring(beforeStartFileName + 1);
        }
        else {
            fileName = protoFullName;
        }
        return fileName;
    }

    /**
     * Compile a protobuf schema to FileDescriptorProto using protobuf4j.
     * This method uses protobuf4j to handle all parsing, linking, and option processing.
     */
    public static FileDescriptorProto toFileDescriptorProto(String schemaDefinition, String protoFileName,
                                                            Optional<String> optionalPackageName, Map<String, String> deps) {
        try {
            // Use protobuf4j to compile the schema - it handles all parsing, linking, and option processing
            ProtobufSchemaLoader.ProtobufSchemaLoaderContext context = ProtobufSchemaLoader.loadSchema(
                    protoFileName, schemaDefinition, deps);
            return context.getFileDescriptor().toProto();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert map entry field name to canonical form.
     * Example: "user_address" -> "userAddress"
     */
    public static String toMapField(String s) {
        if (s.endsWith("Entry")) {
            s = s.substring(0, s.length() - "Entry".length());
            // Convert from UPPER_CAMEL to lower_underscore
            s = s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        }
        return s;
    }

    // ==================================================================================
    // METHODS REQUIRING PROTOBUF4J AST SUPPORT
    // ==================================================================================
    // The following methods require wire-schema AST types (ProtoFileElement, MessageElement, etc.)
    // and have been removed in this refactoring. To restore them, protobuf4j needs to expose
    // AST functionality similar to wire-schema.
    //
    // Removed methods:
    // - protoFileToFileDescriptor(ProtoFileElement)
    // - fileDescriptorToProtoFile(FileDescriptor) - NEEDED BY SERDES LAYER
    // - fileDescriptorWithDepsToProtoFile(FileDescriptor, Map)
    // - toDescriptor(String, ProtoFileElement, Map)
    // - firstMessage(ProtoFileElement)
    // - toDynamicSchema(...) and related helpers
    // - messageElementToDescriptorProto(...)
    // - enumElementToProto(...)
    // - serviceElementToProto(...)
    // - Various helper methods for wire-schema type conversion
    //
    // To restore these methods, protobuf4j should provide:
    // 1. AST classes similar to ProtoFileElement, MessageElement, EnumElement, etc.
    // 2. Conversion from FileDescriptor/FileDescriptorProto to AST
    // 3. Conversion from AST to FileDescriptor/FileDescriptorProto
    //
    // For downstream modules that depend on these methods:
    // - schema-util/protobuf: ProtobufContentAccepter, ProtobufReferenceFinder, ProtobufDereferencer, ProtobufContentValidator
    // - serdes: ProtobufSchemaParser (primary user)
    // - app: AbstractResource, SchemaFormatService
    // - integration-tests: Various test classes
    //
    // Migration path:
    // 1. Add AST support to protobuf4j
    // 2. Create wire-schema compatible API wrapper
    // 3. Update downstream modules to use new API
    // 4. Or refactor downstream modules to work directly with FileDescriptor
    // ==================================================================================

}
