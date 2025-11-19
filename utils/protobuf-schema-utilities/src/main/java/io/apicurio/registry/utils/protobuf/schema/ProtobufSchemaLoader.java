package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors;
import io.roastedroot.protobuf4j.Protobuf;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProtobufSchemaLoader {

    private static final String GOOGLE_API_PATH = "google/type/";
    private static final String GOOGLE_WELLKNOWN_PATH = "google/protobuf/";
    private static final String METADATA_PATH = "metadata/";
    private static final String DECIMAL_PATH = "additionalTypes/";
    // Adding pre-built support for commonly used Google API Protos,
    // https://github.com/googleapis/googleapis
    // These files need to be manually loaded into the FileSystem
    // as Square doesn't support them by default.
    private final static Set<String> GOOGLE_API_PROTOS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "money.proto", "timeofday.proto", "date.proto", "calendar_period.proto", "color.proto",
            "dayofweek.proto", "latlng.proto", "fraction.proto", "month.proto",
            "phone_number.proto", "postal_address.proto", "localized_text.proto",
            "interval.proto", "expr.proto", "quaternion.proto")));
    // Adding support for Protobuf well-known types under package google.protobuf
    // https://developers.google.com/protocol-buffers/docs/reference/google.protobuf
    // These files need to be manually loaded from the protobuf-java JAR into the temp directory
    // for protobuf4j compilation. protobuf4j's ensureWellKnownTypes() handles this internally,
    // but we need to make sure our resources include these files.
    private final static Set<String> GOOGLE_WELLKNOWN_PROTOS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Core well-known types (most commonly used)
            "timestamp.proto", "duration.proto", "any.proto", "empty.proto",
            "wrappers.proto", "descriptor.proto",
            // Additional well-known types
            "api.proto", "field_mask.proto", "source_context.proto", "struct.proto",
            "type.proto")));

    private final static String METADATA_PROTO = "metadata.proto";
    private final static String DECIMAL_PROTO = "decimal.proto";

    private static void loadProtoFiles(Path baseDir, ClassLoader classLoader,
            Set<String> protos, String protoPath) throws IOException {
        for (String proto : protos) {
            // Loads the proto file resource files.
            final InputStream inputStream = classLoader.getResourceAsStream(protoPath + proto);
            if (inputStream == null) {
                throw new IOException("Could not find proto resource: " + protoPath + proto);
            }

            // Read input stream to string
            final String fileContents;
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                fileContents = sb.toString();
            }

            // Create directory structure
            Path protoDir = baseDir.resolve(protoPath);
            Files.createDirectories(protoDir);

            // Write proto file
            Path protoFile = protoDir.resolve(proto);
            Files.write(protoFile, fileContents.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeWellKnownProtos(Path baseDir) throws IOException {
        final ClassLoader classLoader = ProtobufSchemaLoader.class.getClassLoader();

        loadProtoFiles(baseDir, classLoader, GOOGLE_API_PROTOS, GOOGLE_API_PATH);
        loadProtoFiles(baseDir, classLoader, GOOGLE_WELLKNOWN_PROTOS, GOOGLE_WELLKNOWN_PATH);
        loadProtoFiles(baseDir, classLoader, Collections.singleton(METADATA_PROTO), METADATA_PATH);
        loadProtoFiles(baseDir, classLoader, Collections.singleton(DECIMAL_PROTO), DECIMAL_PATH);
    }

    /**
     * Creates a schema loader using protobuf4j to compile .proto files.
     * This replaces the previous wire-schema based approach with a pure JVM solution.
     *
     * Uses the new protobuf4j buildFileDescriptors() method which combines:
     * - Protobuf compilation (getDescriptors)
     * - Automatic dependency resolution
     * - FileDescriptor building with proper dependency linking
     *
     * @param fileName Name of the .proto file.
     * @param schemaDefinition Schema Definition to parse.
     * @param deps Map of dependency file names to their schema definitions.
     * @return ProtobufSchemaLoaderContext containing the compiled FileDescriptor.
     */
    public static ProtobufSchemaLoaderContext loadSchema(String fileName, String schemaDefinition, Map<String, String> deps) throws IOException {
        // Create virtual filesystem for proto files (required by protobuf4j WASM)
        // protobuf4j uses WASM which requires a virtual filesystem mapped via WASI
        FileSystem fs = ZeroFs.newFileSystem(
                Configuration.unix().toBuilder().setAttributeViews("unix").build());

        try (FileSystem ignored = fs) {
            Path workDir = fs.getPath(".");
            // Step 1: Write well-known protos to work directory
            writeWellKnownProtos(workDir);

            // Step 2: Convert all .proto files to ProtoContent instances
            String protoFileName = fileName.endsWith(".proto") ? fileName : fileName + ".proto";
            ProtoContent protoContent = new ProtoContent(protoFileName, schemaDefinition);
            List<ProtoContent> allDependencies = deps.entrySet().stream().map(entry ->
                    new ProtoContent(entry.getKey(), entry.getValue())).collect(Collectors.toList());

            // For the main file, we'll use the original fileName, not the package-based path
            // This ensures FileDescriptor.getName() returns the original name
            // Dependencies still use package-based paths to match import statements

            // Step 3: Fix up the import statements in all .proto files so they point to canonical locations
            allDependencies.forEach(proto -> {
                if (proto.isImportPathMismatched()) {
                    protoContent.fixImport(proto.getImportPath(), proto.getExpectedImportPath());
                    allDependencies.forEach(proto2 -> {
                        if (proto2 != proto) {
                            proto2.fixImport(proto.getImportPath(), proto.getExpectedImportPath());
                        }
                    });
                }
            });

            // Step 4: Write out all .proto files to their correct locations
            // Dependencies use package-based paths to match import statements
            for (ProtoContent proto : allDependencies) {
                proto.writeTo(workDir);
            }

            // Main file uses original fileName to preserve FileDescriptor.getName()
            Path mainProtoPath = workDir.resolve(protoFileName);
            Files.write(mainProtoPath, protoContent.getContent().getBytes(StandardCharsets.UTF_8));

            // Step 5: Use protobuf4j to compile the proto files and build FileDescriptors
            // The new buildFileDescriptors() method handles both compilation and dependency resolution
            // Note: buildFileDescriptors expects file paths relative to workDir, not absolute paths
            // We need to pass ALL files (main + dependencies) for compilation
            List<String> protoFiles = new ArrayList<>();

            // Add all dependencies (using their package-based paths)
            for (ProtoContent proto : allDependencies) {
                protoFiles.add(proto.getExpectedImportPath());
            }

            // Add main file last (using original fileName)
            protoFiles.add(protoFileName);

            List<Descriptors.FileDescriptor> fileDescriptors = Protobuf.buildFileDescriptors(workDir, protoFiles);

            // Step 6: Find the main FileDescriptor from the built descriptors
            Descriptors.FileDescriptor mainDescriptor = fileDescriptors.stream()
                    .filter(fd -> fd.getName().equals(protoFileName))  // Match original fileName
                    .findFirst()
                    .orElse(null);

            if (mainDescriptor == null) {
                throw new IOException("Error loading Protobuf File: " + protoFileName);
            }

            return new ProtobufSchemaLoaderContext(mainDescriptor);
        }
        catch (RuntimeException e) {
            // Wrap protobuf4j RuntimeExceptions (compilation errors) in IOException
            // so they can be properly handled upstream
            throw new IOException("Failed to compile protobuf schema: " + fileName, e);
        }
        // Close the virtual filesystem
        // Ignore close errors
    }

    protected static class ProtobufSchemaLoaderContext {
        private final Descriptors.FileDescriptor fileDescriptor;

        public Descriptors.FileDescriptor getFileDescriptor() {
            return fileDescriptor;
        }

        public ProtobufSchemaLoaderContext(Descriptors.FileDescriptor fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
        }
    }
}
