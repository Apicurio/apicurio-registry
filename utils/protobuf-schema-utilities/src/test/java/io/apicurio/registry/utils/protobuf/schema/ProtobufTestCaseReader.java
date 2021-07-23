package io.apicurio.registry.utils.protobuf.schema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ProtobufTestCaseReader {
    private static final String TEST_PROTO_PATH = "src/test/proto/";

    public static String getRawSchema(String fileName) {
        try {
            return Files.readString(Paths.get(TEST_PROTO_PATH, fileName));
        } catch (IOException e) {
            throw new RuntimeException("Error reading file", e);
        }
    }
}