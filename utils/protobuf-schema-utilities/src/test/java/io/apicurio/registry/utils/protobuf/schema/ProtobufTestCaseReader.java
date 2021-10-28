package io.apicurio.registry.utils.protobuf.schema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ProtobufTestCaseReader {
    private static final String TEST_PROTO_PATH = "src/test/proto/";

    public static String getRawSchema(String fileName) {
        try {
            return new String(Files.readAllBytes(Paths.get(TEST_PROTO_PATH, fileName)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error reading file", e);
        }
    }
}