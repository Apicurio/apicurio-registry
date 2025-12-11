package io.apicurio.registry.utils.protobuf.schema;

import io.roastedroot.protobuf4j.v4.Protobuf;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Pooled compilation context for protobuf schema operations.
 *
 * <p>This class manages a pool of ZeroFs virtual filesystems with pre-loaded well-known types
 * and cached Protobuf WASM instances to reduce overhead for repeated schema compilations.</p>
 *
 * <h2>Benefits:</h2>
 * <ul>
 *   <li>Reduces filesystem creation overhead</li>
 *   <li>Avoids WASM cold-start for repeated operations</li>
 *   <li>Pre-loads well-known types once per pool slot</li>
 *   <li>Thread-safe pool access</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * try (ProtobufCompilationContext ctx = ProtobufCompilationContext.acquire()) {
 *     // Write user proto files
 *     Files.write(ctx.getWorkDir().resolve("schema.proto"), content.getBytes());
 *
 *     // Compile using cached Protobuf instance
 *     List<FileDescriptor> fds = ctx.getProtobuf().buildFileDescriptors(protoFiles);
 * }
 * // Context is automatically returned to pool or closed
 * }</pre>
 */
public class ProtobufCompilationContext implements AutoCloseable {

    // ==================== Pool Configuration ====================

    /**
     * Maximum number of contexts to keep in the pool.
     * Additional contexts beyond this limit will be closed immediately after use.
     */
    private static final int POOL_SIZE = 4;

    /**
     * Maximum time to wait for a pooled context before creating a new one (in milliseconds).
     */
    private static final long POOL_WAIT_MS = 10;

    /**
     * The shared pool of compilation contexts.
     */
    private static final BlockingQueue<ProtobufCompilationContext> POOL = new ArrayBlockingQueue<>(POOL_SIZE);

    // ==================== Well-Known Types Configuration ====================

    private static final String GOOGLE_API_PATH = "google/type/";
    private static final String METADATA_PATH = "metadata/";
    private static final String DECIMAL_PATH = "additionalTypes/";

    private static final Set<String> GOOGLE_API_PROTOS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "money.proto", "timeofday.proto", "date.proto", "calendar_period.proto", "color.proto",
            "dayofweek.proto", "latlng.proto", "fraction.proto", "month.proto",
            "phone_number.proto", "postal_address.proto", "localized_text.proto",
            "interval.proto", "expr.proto", "quaternion.proto")));

    private static final String METADATA_PROTO = "metadata.proto";
    private static final String DECIMAL_PROTO = "decimal.proto";

    /**
     * Directories that contain well-known types and should be preserved during reset.
     */
    private static final Set<String> WELL_KNOWN_DIRS = new HashSet<>(Arrays.asList(
            "google", "metadata", "additionalTypes"));

    // ==================== Instance Fields ====================

    private final FileSystem fs;
    private final Path workDir;
    private final Protobuf protobuf;
    private final boolean fromPool;
    private boolean closed = false;

    // ==================== Factory Methods ====================

    /**
     * Acquire a compilation context from the pool or create a new one.
     *
     * <p>The returned context should be used within a try-with-resources block
     * to ensure proper cleanup or return to pool.</p>
     *
     * @return A compilation context ready for use
     * @throws IOException if context creation fails
     */
    public static ProtobufCompilationContext acquire() throws IOException {
        // Try to get from pool first (non-blocking with short timeout)
        try {
            ProtobufCompilationContext pooled = POOL.poll(POOL_WAIT_MS, TimeUnit.MILLISECONDS);
            if (pooled != null && !pooled.closed) {
                pooled.reset();
                return pooled;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create new context - mark as poolable so it gets returned to pool on close
        return createNew(true);
    }

    /**
     * Acquire a compilation context, optionally skipping specific bundled packages.
     *
     * @param skipPackages Packages to skip when loading well-known types
     * @return A compilation context ready for use
     * @throws IOException if context creation fails
     */
    public static ProtobufCompilationContext acquire(Set<String> skipPackages) throws IOException {
        // For now, create fresh context when custom skip packages are needed
        // Pooled contexts have all well-known types loaded
        if (skipPackages != null && !skipPackages.isEmpty()) {
            return createNewWithSkipPackages(skipPackages);
        }
        return acquire();
    }

    /**
     * Create a new compilation context (not from pool).
     */
    private static ProtobufCompilationContext createNew(boolean forPool) throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(
                Configuration.unix().toBuilder().setAttributeViews("unix").build());

        try {
            Path workDir = fs.getPath(".");

            // Load all well-known types
            writeWellKnownProtos(workDir, Collections.emptySet());

            // Create and cache the Protobuf WASM instance
            Protobuf protobuf = Protobuf.builder().withWorkdir(workDir).build();

            return new ProtobufCompilationContext(fs, workDir, protobuf, forPool);
        } catch (Exception e) {
            try {
                fs.close();
            } catch (Exception closeEx) {
                e.addSuppressed(closeEx);
            }
            throw new IOException("Failed to create compilation context", e);
        }
    }

    /**
     * Create a new compilation context with specific packages skipped.
     */
    private static ProtobufCompilationContext createNewWithSkipPackages(Set<String> skipPackages) throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(
                Configuration.unix().toBuilder().setAttributeViews("unix").build());

        try {
            Path workDir = fs.getPath(".");

            // Load well-known types, skipping conflicting packages
            writeWellKnownProtos(workDir, skipPackages);

            // Create the Protobuf WASM instance
            Protobuf protobuf = Protobuf.builder().withWorkdir(workDir).build();

            // Don't pool contexts with custom skip packages
            return new ProtobufCompilationContext(fs, workDir, protobuf, false);
        } catch (Exception e) {
            try {
                fs.close();
            } catch (Exception closeEx) {
                e.addSuppressed(closeEx);
            }
            throw new IOException("Failed to create compilation context with skip packages", e);
        }
    }

    // ==================== Constructor ====================

    private ProtobufCompilationContext(FileSystem fs, Path workDir, Protobuf protobuf, boolean fromPool) {
        this.fs = fs;
        this.workDir = workDir;
        this.protobuf = protobuf;
        this.fromPool = fromPool;
    }

    // ==================== Public API ====================

    /**
     * Get the working directory for this context.
     * User proto files should be written here.
     *
     * @return The working directory path
     */
    public Path getWorkDir() {
        return workDir;
    }

    /**
     * Get the cached Protobuf WASM instance for this context.
     *
     * @return The Protobuf instance for compilation and normalization
     */
    public Protobuf getProtobuf() {
        return protobuf;
    }

    /**
     * Get the virtual filesystem for this context.
     *
     * @return The ZeroFs filesystem
     */
    public FileSystem getFileSystem() {
        return fs;
    }

    /**
     * Reset this context for reuse by removing user-added files.
     * Well-known type directories are preserved.
     *
     * @throws IOException if cleanup fails
     */
    public void reset() throws IOException {
        // Remove all files and directories except well-known types
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workDir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!WELL_KNOWN_DIRS.contains(name)) {
                    deleteRecursively(entry);
                }
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        if (fromPool) {
            // Try to return to pool
            try {
                reset();
                if (POOL.offer(this)) {
                    return; // Successfully returned to pool
                }
            } catch (IOException e) {
                // Reset failed, close instead
            }
        }

        // Close resources
        closed = true;
        try {
            protobuf.close();
        } catch (Exception e) {
            // Ignore close errors
        }
        try {
            fs.close();
        } catch (Exception e) {
            // Ignore close errors
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Recursively delete a file or directory.
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * Write well-known proto files to the work directory.
     */
    private static void writeWellKnownProtos(Path baseDir, Set<String> skipPackages) throws IOException {
        final ClassLoader classLoader = ProtobufCompilationContext.class.getClassLoader();

        // Load Google API protos (google.type.*)
        loadProtoFiles(baseDir, classLoader, GOOGLE_API_PROTOS, GOOGLE_API_PATH);

        // Load custom Apicurio protos, but skip if user schema uses the same package
        if (!skipPackages.contains(ProtobufWellKnownTypes.getMetadataPackage())) {
            loadProtoFiles(baseDir, classLoader, Collections.singleton(METADATA_PROTO), METADATA_PATH);
        }
        if (!skipPackages.contains(ProtobufWellKnownTypes.getAdditionalTypesPackage())) {
            loadProtoFiles(baseDir, classLoader, Collections.singleton(DECIMAL_PROTO), DECIMAL_PATH);
        }
    }

    /**
     * Load proto files from classpath resources to the filesystem.
     */
    private static void loadProtoFiles(Path baseDir, ClassLoader classLoader,
                                        Set<String> protos, String protoPath) throws IOException {
        Path protoDir = baseDir.resolve(protoPath);
        Files.createDirectories(protoDir);

        for (String proto : protos) {
            final InputStream inputStream = classLoader.getResourceAsStream(protoPath + proto);
            if (inputStream == null) {
                throw new IOException("Could not find proto resource: " + protoPath + proto);
            }

            final String fileContents;
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder(8 * 1024);
                char[] buffer = new char[8 * 1024];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                fileContents = sb.toString();
            }

            Path protoFile = protoDir.resolve(proto);
            Files.write(protoFile, fileContents.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ==================== Pool Management ====================

    /**
     * Clear the pool and close all pooled contexts.
     * Useful for testing or shutdown.
     */
    public static void clearPool() {
        ProtobufCompilationContext ctx;
        while ((ctx = POOL.poll()) != null) {
            ctx.closed = true;
            try {
                ctx.protobuf.close();
            } catch (Exception e) {
                // Ignore
            }
            try {
                ctx.fs.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Get the current pool size (for testing/monitoring).
     *
     * @return Number of contexts currently in the pool
     */
    public static int getPoolSize() {
        return POOL.size();
    }

    /**
     * Pre-warm the pool by creating contexts up to the pool size.
     * This is optional and can be called at application startup.
     *
     * @throws IOException if context creation fails
     */
    public static void warmPool() throws IOException {
        while (POOL.size() < POOL_SIZE) {
            ProtobufCompilationContext ctx = createNew(true);
            if (!POOL.offer(ctx)) {
                ctx.close();
                break;
            }
        }
    }
}
