package io.apicurio.registry.utils.protobuf.schema;

import com.google.common.collect.Lists;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ProtobufSchemaLoader {
    /**
     * Creates a schema loader using a in-memory file system. This is required for square wire schema parser and linker
     * to load the types correctly. See https://github.com/square/wire/issues/2024#
     * As of now this only supports reading one .proto file but can be extended to support reading multiple files.
     * @param packageName Package name for the .proto if present
     * @param fileName Name of the .proto file.
     * @param schemaDefinition Schema Definition to parse.
     * @return Schema - parsed and properly linked Schema.
     */
    public static ProtobufSchemaLoaderContext loadSchema(Optional<String> packageName, String fileName, String schemaDefinition)
        throws IOException {
        final FileSystem inMemoryFileSystem = Jimfs.newFileSystem(Configuration.unix());
        String [] dirs = {};
        if (packageName.isPresent()) {
            dirs = packageName.get().split("\\.");
        }
        try {
            String dirPath = "";
            for (String dir: dirs) {
                dirPath = dirPath + "/" + dir;
                Path path = inMemoryFileSystem.getPath(dirPath);
                Files.createDirectory(path);
            }
            Path path = inMemoryFileSystem.getPath(dirPath, fileName);
            Files.write(path, schemaDefinition.getBytes());

            try (SchemaLoader schemaLoader = new SchemaLoader(inMemoryFileSystem)) {
                schemaLoader.initRoots(Lists.newArrayList(Location.get("/")), Lists.newArrayList(Location.get("/")));

                Schema schema = schemaLoader.loadSchema();
                ProtoFile protoFile = schema.protoFile(path.toString().replaceFirst("/", ""));

                if (protoFile == null) {
                    throw new RuntimeException("Error loading Protobuf File: " + fileName);
                }

                return new ProtobufSchemaLoaderContext(schema, protoFile);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            inMemoryFileSystem.close();
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
