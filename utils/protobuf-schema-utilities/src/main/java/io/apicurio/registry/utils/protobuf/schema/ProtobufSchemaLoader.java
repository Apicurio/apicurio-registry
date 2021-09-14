package io.apicurio.registry.utils.protobuf.schema;

import com.google.common.collect.Lists;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;
import okio.Buffer;
import okio.Sink;
import okio.fakefilesystem.FakeFileSystem;

import java.io.IOException;
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
        final FakeFileSystem inMemoryFileSystem = new FakeFileSystem();
        String [] dirs = {};

        if (packageName.isPresent()) {
            dirs = packageName.get().split("\\.");
        }

        String dirPath = "";
        for (String dir: dirs) {
            dirPath = dirPath + "/" + dir;
            inMemoryFileSystem.createDirectory(okio.Path.get(dirPath));
        }

        final Buffer buffer = new Buffer();
        buffer.writeUtf8(schemaDefinition);

        final Sink sink = inMemoryFileSystem.sink(okio.Path.get(dirPath + "/" + fileName));
        sink.write(buffer, buffer.size());
        sink.close();
        buffer.close();

        final SchemaLoader schemaLoader = new SchemaLoader(inMemoryFileSystem);
        schemaLoader.initRoots(Lists.newArrayList(Location.get("/")), Lists.newArrayList(Location.get("/")));

        final Schema schema = schemaLoader.loadSchema();
        final String path = dirPath + "/" + fileName;
        final ProtoFile protoFile = schema.protoFile(path.replaceFirst("/", ""));

        if (protoFile == null) {
            throw new RuntimeException("Error loading Protobuf File: " + fileName);
        }

        return new ProtobufSchemaLoaderContext(schema, protoFile);
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
