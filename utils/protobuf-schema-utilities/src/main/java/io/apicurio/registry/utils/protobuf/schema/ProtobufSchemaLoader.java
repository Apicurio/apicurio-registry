package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProtobufSchemaLoader {

    // ==================== Performance Configuration ====================

    /**
     * Default buffer size for reading proto files (8KB).
     * Matches typical proto file sizes while avoiding excessive allocation.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    // ==================== Proto File Paths ====================

    private static final String GOOGLE_API_PATH = "google/type/";
    private static final String METADATA_PATH = "metadata/";
    private static final String DECIMAL_PATH = "additionalTypes/";

    // NOTE: Google Protocol Buffer well-known types (google.protobuf.*)
    // are handled internally by protobuf4j's ensureWellKnownTypes().
    // Do NOT load them explicitly, as that would cause duplicate definition errors.

    // Adding pre-built support for commonly used Google API Protos,
    // https://github.com/googleapis/googleapis
    // These are NOT the same as google.protobuf well-known types.
    private final static Set<String> GOOGLE_API_PROTOS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "money.proto", "timeofday.proto", "date.proto", "calendar_period.proto", "color.proto",
            "dayofweek.proto", "latlng.proto", "fraction.proto", "month.proto",
            "phone_number.proto", "postal_address.proto", "localized_text.proto",
            "interval.proto", "expr.proto", "quaternion.proto")));

    private final static String METADATA_PROTO = "metadata.proto";
    private final static String DECIMAL_PROTO = "decimal.proto";

    // Package names used by bundled protos - if user schema uses these packages,
    // we must skip loading the bundled protos to avoid duplicate definitions
    // @see ProtobufWellKnownTypes for centralized package name constants

    // Pattern to extract package name from proto schema
    // Matches: package some.package.name;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*?)\\s*;",
            Pattern.MULTILINE
    );

    /**
     * Extract the package name from a proto schema definition.
     *
     * @param schemaContent The proto schema content
     * @return The package name, or null if not found
     */
    private static String extractPackageName(String schemaContent) {
        if (schemaContent == null || schemaContent.isEmpty()) {
            return null;
        }
        Matcher matcher = PACKAGE_PATTERN.matcher(schemaContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void loadProtoFiles(Path baseDir, ClassLoader classLoader,
            Set<String> protos, String protoPath) throws IOException {
        // Create directory structure once for all protos in this path
        Path protoDir = baseDir.resolve(protoPath);
        Files.createDirectories(protoDir);

        for (String proto : protos) {
            // Loads the proto file resource files.
            final InputStream inputStream = classLoader.getResourceAsStream(protoPath + proto);
            if (inputStream == null) {
                throw new IOException("Could not find proto resource: " + protoPath + proto);
            }

            // Read input stream to string with pre-sized buffer
            final String fileContents;
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                // Pre-size StringBuilder to avoid resizing (typical proto files are < 8KB)
                StringBuilder sb = new StringBuilder(DEFAULT_BUFFER_SIZE);
                char[] buffer = new char[DEFAULT_BUFFER_SIZE];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                fileContents = sb.toString();
            }

            // Write proto file
            Path protoFile = protoDir.resolve(proto);
            Files.write(protoFile, fileContents.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Write well-known proto files to the work directory.
     *
     * @param baseDir The base directory to write to
     * @param skipPackages Set of package names to skip (to avoid conflicts with user schemas)
     * @throws IOException if writing fails
     */
    private static void writeWellKnownProtos(Path baseDir, Set<String> skipPackages) throws IOException {
        final ClassLoader classLoader = ProtobufSchemaLoader.class.getClassLoader();

        // NOTE: Do NOT load Google Protobuf well-known types (google.protobuf.*)
        // protobuf4j handles these internally via ensureWellKnownTypes().
        // Loading them here would cause duplicate definition errors.

        // Load Google API protos (google.type.*)
        // These are NOT handled by protobuf4j's ensureWellKnownTypes()
        loadProtoFiles(baseDir, classLoader, GOOGLE_API_PROTOS, GOOGLE_API_PATH);

        // Load custom Apicurio protos, but skip if user schema uses the same package
        // to avoid duplicate definition errors that crash the WASM protoc
        if (!skipPackages.contains(ProtobufWellKnownTypes.getMetadataPackage())) {
            loadProtoFiles(baseDir, classLoader, Collections.singleton(METADATA_PROTO), METADATA_PATH);
        }
        if (!skipPackages.contains(ProtobufWellKnownTypes.getAdditionalTypesPackage())) {
            loadProtoFiles(baseDir, classLoader, Collections.singleton(DECIMAL_PROTO), DECIMAL_PATH);
        }
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
     * This method now uses {@link ProtobufCompilationContext} for pooled filesystem
     * and WASM instance reuse, improving performance for repeated compilations.
     *
     * @param fileName Name of the .proto file.
     * @param schemaDefinition Schema Definition to parse.
     * @param deps Map of dependency file names to their schema definitions.
     * @return ProtobufSchemaLoaderContext containing the compiled FileDescriptor.
     */
    public static ProtobufSchemaLoaderContext loadSchema(String fileName, String schemaDefinition, Map<String, String> deps) throws IOException {
        // Detect packages used by user schemas that CONFLICT with bundled protos.
        // We only need to skip bundled protos if user schema uses the same package
        // (metadata or additionalTypes). Otherwise, we can use pooled contexts.
        Set<String> conflictingPackages = new HashSet<>();
        String mainPackage = extractPackageName(schemaDefinition);
        if (mainPackage != null && ProtobufWellKnownTypes.isBundledPackage(mainPackage)) {
            conflictingPackages.add(mainPackage);
        }
        // Also check dependency packages
        if (deps != null) {
            for (String depContent : deps.values()) {
                String depPackage = extractPackageName(depContent);
                if (depPackage != null && ProtobufWellKnownTypes.isBundledPackage(depPackage)) {
                    conflictingPackages.add(depPackage);
                }
            }
        }

        // Acquire a compilation context from the pool (or create a new one)
        // The context includes a virtual filesystem and cached Protobuf WASM instance
        // Only pass conflicting packages - empty set means we can use the pool
        try (ProtobufCompilationContext ctx = ProtobufCompilationContext.acquire(conflictingPackages)) {
            Path workDir = ctx.getWorkDir();

            // Step 1: Convert all .proto files to ProtoContent instances
            // Filter out google/protobuf/* well-known types - protobuf4j handles these internally
            // via ensureWellKnownTypes(). Including them here would cause duplicate definitions.
            String protoFileName = fileName.endsWith(".proto") ? fileName : fileName + ".proto";
            ProtoContent protoContent = new ProtoContent(protoFileName, schemaDefinition);
            List<ProtoContent> allDependencies = deps.entrySet().stream()
                    .filter(entry -> !ProtobufWellKnownTypes.isHandledByProtobuf4j(entry.getKey()))
                    .map(entry -> new ProtoContent(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // For the main file, we'll use the original fileName, not the package-based path
            // This ensures FileDescriptor.getName() returns the original name
            // Dependencies still use package-based paths to match import statements

            // Step 2: Fix up the import statements in all .proto files so they point to canonical locations
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

            // Step 3: Write out all .proto files to their correct locations
            // Dependencies use package-based paths to match import statements
            for (ProtoContent proto : allDependencies) {
                proto.writeTo(workDir);
            }

            // Main file uses original fileName to preserve FileDescriptor.getName()
            Path mainProtoPath = workDir.resolve(protoFileName);
            // Create parent directory if needed (for paths like "mypackage/file.proto")
            Path parentDir = mainProtoPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            Files.write(mainProtoPath, protoContent.getContent().getBytes(StandardCharsets.UTF_8));

            // Step 4: Build the list of proto files to compile
            // NOTE: protobuf4j handles google.protobuf.* well-known types internally,
            // so we only need to compile the user's proto and explicit dependencies.
            // Bundled protos (google.type.*, metadata/*, additionalTypes/*) are added
            // only if they're actually imported by the user schema.

            // Extract imports from main schema and dependencies to determine which bundled protos are needed
            Set<String> allImports = new HashSet<>(ProtobufSchemaUtils.extractImports(schemaDefinition));
            for (ProtoContent proto : allDependencies) {
                allImports.addAll(ProtobufSchemaUtils.extractImports(proto.getContent()));
            }

            // Collect all dependency paths to avoid duplicates
            Set<String> depPaths = new HashSet<>(allDependencies.size());
            for (ProtoContent proto : allDependencies) {
                depPaths.add(proto.getExpectedImportPath());
            }

            // Pre-size for bundled protos + user's schema + dependencies
            List<String> protoFiles = new ArrayList<>(allImports.size() + allDependencies.size() + 1);

            // Add Google API protos (google.type.*) only if they're actually imported
            // These are NOT handled by protobuf4j's ensureWellKnownTypes()
            for (String proto : GOOGLE_API_PROTOS) {
                String path = GOOGLE_API_PATH + proto;
                if (allImports.contains(path) && !depPaths.contains(path)) {
                    protoFiles.add(path);
                }
            }

            // Add custom Apicurio protos only if imported and not conflicting with user packages
            String metadataPath = METADATA_PATH + METADATA_PROTO;
            if (allImports.contains(metadataPath) && !depPaths.contains(metadataPath)
                    && !conflictingPackages.contains(ProtobufWellKnownTypes.getMetadataPackage())) {
                protoFiles.add(metadataPath);
            }
            String decimalPath = DECIMAL_PATH + DECIMAL_PROTO;
            if (allImports.contains(decimalPath) && !depPaths.contains(decimalPath)
                    && !conflictingPackages.contains(ProtobufWellKnownTypes.getAdditionalTypesPackage())) {
                protoFiles.add(decimalPath);
            }

            // Add all explicit dependencies (using their package-based paths)
            for (ProtoContent proto : allDependencies) {
                protoFiles.add(proto.getExpectedImportPath());
            }

            // Add main file last (using original fileName)
            protoFiles.add(protoFileName);

            // Step 5: Use protobuf4j to compile the proto files and build FileDescriptors
            // The cached Protobuf instance from the context avoids WASM cold-start overhead
            List<Descriptors.FileDescriptor> fileDescriptors = ctx.getProtobuf().buildFileDescriptors(protoFiles);

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
            String detailedMessage = extractProtocError(e);
            throw new IOException("Failed to compile protobuf schema '" + fileName + "': " + detailedMessage, e);
        }
    }

    /**
     * Extract detailed error information from a protobuf4j exception.
     *
     * @param e The runtime exception from protobuf4j
     * @return A detailed error message
     */
    private static String extractProtocError(RuntimeException e) {
        // Try to get the most specific error message
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return e.getMessage() != null ? e.getMessage() : "Unknown compilation error";
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
