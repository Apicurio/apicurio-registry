package io.apicurio.registry.utils.protobuf.schema;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;
import okio.FileHandle;
import okio.FileSystem;
import okio.fakefilesystem.FakeFileSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProtobufSchemaLoader {

    private static final String GOOGLE_API_PATH = "google/type/";
    private static final String GOOGLE_WELLKNOWN_PATH = "google/protobuf/";
    private static final String METADATA_PATH = "metadata/";
    private static final String DECIMAL_PATH = "additionalTypes/";
    // Adding pre-built support for commonly used Google API Protos,
    // https://github.com/googleapis/googleapis
    // These files need to be manually loaded into the FileSystem
    // as Square doesn't support them by default.
    private final static Set<String> GOOGLE_API_PROTOS = ImmutableSet.<String> builder().add("money.proto")
            .add("timeofday.proto").add("date.proto").add("calendar_period.proto").add("color.proto")
            .add("dayofweek.proto").add("latlng.proto").add("fraction.proto").add("month.proto")
            .add("phone_number.proto").add("postal_address.proto").add("localized_text.proto")
            .add("interval.proto").add("expr.proto").add("quaternion.proto").build();
    // Adding support for Protobuf well-known types under package google.protobuf that are not covered by
    // Square
    // https://developers.google.com/protocol-buffers/docs/reference/google.protobuf
    // These files need to be manually loaded into the FileSystem
    // as Square doesn't support them by default.
    private final static Set<String> GOOGLE_WELLKNOWN_PROTOS = ImmutableSet.<String> builder()
            .add("api.proto").add("field_mask.proto").add("source_context.proto").add("struct.proto")
            .add("type.proto").build();

    private final static String METADATA_PROTO = "metadata.proto";
    private final static String DECIMAL_PROTO = "decimal.proto";

    private static FileSystem getFileSystem() throws IOException {
        final FakeFileSystem inMemoryFileSystem = new FakeFileSystem();
        inMemoryFileSystem.setWorkingDirectory(okio.Path.get("/"));
        inMemoryFileSystem.setAllowSymlinks(true);

        final ClassLoader classLoader = ProtobufSchemaLoader.class.getClassLoader();

        createDirectory(GOOGLE_API_PATH, inMemoryFileSystem);
        loadProtoFiles(inMemoryFileSystem, classLoader, GOOGLE_API_PROTOS, GOOGLE_API_PATH);

        createDirectory(GOOGLE_WELLKNOWN_PATH, inMemoryFileSystem);
        loadProtoFiles(inMemoryFileSystem, classLoader, GOOGLE_WELLKNOWN_PROTOS, GOOGLE_WELLKNOWN_PATH);

        createDirectory(METADATA_PATH, inMemoryFileSystem);
        loadProtoFiles(inMemoryFileSystem, classLoader, Collections.singleton(METADATA_PROTO), METADATA_PATH);

        createDirectory(DECIMAL_PATH, inMemoryFileSystem);
        loadProtoFiles(inMemoryFileSystem, classLoader, Collections.singleton(DECIMAL_PROTO), DECIMAL_PATH);

        return inMemoryFileSystem;
    }

    private static void loadProtoFiles(FakeFileSystem inMemoryFileSystem, ClassLoader classLoader,
            Set<String> protos, String protoPath) throws IOException {
        for (String proto : protos) {
            // Loads the proto file resource files.
            final InputStream inputStream = classLoader.getResourceAsStream(protoPath + proto);
            final String fileContents = CharStreams
                    .toString(new InputStreamReader(inputStream, Charsets.UTF_8));
            final okio.Path path = okio.Path.get("/" + protoPath + "/" + proto);
            FileHandle fileHandle = inMemoryFileSystem.openReadWrite(path);
            fileHandle.write(0, fileContents.getBytes(StandardCharsets.UTF_8), 0,
                    fileContents.getBytes(StandardCharsets.UTF_8).length);
            fileHandle.close();
        }
    }

    private static void createDirectory(String directory, FileSystem fileSystem) throws IOException {
        final okio.Path path = okio.Path.get(directory);
        if (!fileSystem.exists(path)) {
            fileSystem.createDirectories(path);
        }
    }

    /**
     * Creates a schema loader using a in-memory file system. This is required for square wire schema parser
     * and linker to load the types correctly. See https://github.com/square/wire/issues/2024# As of now this
     * only supports reading one .proto file but can be extended to support reading multiple files.
     * 
     * @param fileName Name of the .proto file.
     * @param schemaDefinition Schema Definition to parse.
     * @param schemaDefinition Schema Definition to parse.
     * @return Schema - parsed and properly linked Schema.
     */
    public static ProtobufSchemaLoaderContext loadSchema(String fileName, String schemaDefinition, Map<String, String> deps) throws IOException {
        final FileSystem inMemoryFileSystem = getFileSystem();

        String protoFileName = fileName.endsWith(".proto") ? fileName : fileName + ".proto";

        try {
            // Step 1: Convert all .proto files to ProtoContent instances
            ProtoContent protoContent = new ProtoContent(protoFileName, schemaDefinition);
            List<ProtoContent> allDepencencies = deps.entrySet().stream().map(entry ->
                    new ProtoContent(entry.getKey(), entry.getValue())).toList();

            // Step 2: Fix up the import statements in all .proto files so they point to canonical locations
            allDepencencies.forEach(proto -> {
                if (proto.isImportPathMismatched()) {
                    protoContent.fixImport(proto.getImportPath(), proto.getExpectedImportPath());
                    allDepencencies.forEach(proto2 -> {
                        if (proto2 != proto) {
                            proto2.fixImport(proto.getImportPath(), proto.getExpectedImportPath());
                        }
                    });
                }
            });

            // Step 3: Write out all .proto files to their correct (package based) locations
            for (ProtoContent proto : allDepencencies) {
                proto.writeTo(inMemoryFileSystem);
            }
            okio.Path path = protoContent.writeTo(inMemoryFileSystem);

            // Step 4: Load the .proto using SchemaLoader
            SchemaLoader schemaLoader = new SchemaLoader(inMemoryFileSystem);
            schemaLoader.initRoots(Lists.newArrayList(Location.get("/")),
                    Lists.newArrayList(Location.get("/")));

            Schema schema = schemaLoader.loadSchema();
            ProtoFile protoFile = schema.protoFile(path);

            if (protoFile == null) {
                throw new RuntimeException("Error loading Protobuf File: " + protoFileName);
            }

            return new ProtobufSchemaLoaderContext(schema, protoFile);
        } catch (Exception e) {
            throw e;
        }
    }

    protected static class ProtobufSchemaLoaderContext {
        private final Schema schema;
        private final ProtoFile protoFile;

        public Schema getSchema() {
            return schema;
        }

        public ProtoFile getProtoFile() {
            return protoFile;
        }

        public ProtobufSchemaLoaderContext(Schema schema, ProtoFile protoFile) {
            this.schema = schema;
            this.protoFile = protoFile;
        }
    }
}
