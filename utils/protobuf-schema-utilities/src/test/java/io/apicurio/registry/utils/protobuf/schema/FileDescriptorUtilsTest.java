package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Assertions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDescriptorUtilsTest {

    /**
     * Helper method to read a proto schema from test resources
     */
    private static String readSchemaFile(String filename) {
        try (InputStream is = FileDescriptorUtilsTest.class.getResourceAsStream("/schemas/" + filename)) {
            if (is == null) {
                throw new IOException("Schema file not found: " + filename);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + filename, e);
        }
    }

    // Test schemas loaded from resources (cleaner than inline strings)
    private static final String SIMPLE_PROTO_SCHEMA = readSchemaFile("simple.proto");
    private static final String COMPLEX_PROTO_SCHEMA = readSchemaFile("complex.proto");
    private static final String ANYFILE_PROTO_SCHEMA = readSchemaFile("anyFile.proto");

    /**
     * Helper method to load a proto file from test resources and compile it to FileDescriptor.
     * This replaces the old approach of using compiled Java classes.
     */
    private static Descriptors.FileDescriptor loadProtoFileDescriptor(String protoFileName) {
        try {
            ClassLoader classLoader = FileDescriptorUtilsTest.class.getClassLoader();
            File protoFile = new File(
                    Objects.requireNonNull(classLoader.getResource("proto/" + protoFileName)).getFile());

            String schemaContent = readSchemaAsString(protoFile);

            // Load decimal.proto as a dependency since many test protos import it
            File decimalProtoFile = new File(
                    Objects.requireNonNull(classLoader.getResource("proto/additionalTypes/decimal.proto")).getFile());

            Map<String, String> deps = new HashMap<>();
            deps.put("additionalTypes/decimal.proto", readSchemaAsString(decimalProtoFile));

            // Check if this proto has metadata.proto dependency
            if (protoFileName.contains("CustomOptions")) {
                File metadataProtoFile = new File(
                        Objects.requireNonNull(classLoader.getResource("proto/metadata/metadata.proto")).getFile());
                deps.put("metadata/metadata.proto", readSchemaAsString(metadataProtoFile));
            }

            // Use FileDescriptorUtils to compile the proto with dependencies
            return FileDescriptorUtils.parseProtoFileWithDependencies(
                    FileDescriptorUtils.ProtobufSchemaContent.of(protoFileName, schemaContent),
                    deps.entrySet().stream()
                            .map(e -> FileDescriptorUtils.ProtobufSchemaContent.of(e.getKey(), e.getValue()))
                            .collect(Collectors.toList()),
                    new HashMap<>(),
                    false
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load proto file: " + protoFileName, e);
        }
    }

    /**
     * Load proto files from test resources and compile them to FileDescriptors.
     * This replaces the old approach of using compiled Java classes.
     */
    private static Stream<Arguments> testProtoFileProvider() throws Exception {
        return Stream.of(
                loadProtoFileDescriptor("TestOrderingSyntax2.proto"),
                loadProtoFileDescriptor("TestOrderingSyntax2Options.proto"),
                loadProtoFileDescriptor("TestOrderingSyntax2Specified.proto"),
                loadProtoFileDescriptor("TestOrderingSyntax3.proto"),
                loadProtoFileDescriptor("TestOrderingSyntax3Options.proto"),
                loadProtoFileDescriptor("TestOrderingSyntax2References.proto"),
                loadProtoFileDescriptor("TestOrderingSyntax3References.proto"),
                loadProtoFileDescriptor("WellKnownTypesTestSyntax3.proto"),
                loadProtoFileDescriptor("WellKnownTypesTestSyntax2.proto"),
                loadProtoFileDescriptor("TestSyntax3Optional.proto"),
                loadProtoFileDescriptor("TestSyntax2OneOfs.proto"),
                loadProtoFileDescriptor("TestSyntax3OneOfs.proto"),
                loadProtoFileDescriptor("TestSyntax2JavaPackage.proto"),
                loadProtoFileDescriptor("TestSyntax3JavaPackage.proto"),
                loadProtoFileDescriptor("TestSyntax2CustomOptions.proto"),
                loadProtoFileDescriptor("TestSyntax3CustomOptions.proto")
        ).map(Arguments::of);
    }

    private static Stream<Arguments> testProtoFileProviderForJsonName() throws Exception {
        return Stream.of(
                loadProtoFileDescriptor("TestSyntax2JsonName.proto"),
                loadProtoFileDescriptor("TestSyntax3JsonName.proto")
        ).map(Arguments::of);
    }

    private static Stream<Arguments> testParseWithDepsProtoFilesProvider() {
        ClassLoader classLoader = FileDescriptorUtilsTest.class.getClassLoader();
        File mainProtoFile = new File(
                Objects.requireNonNull(classLoader.getResource("parseWithDeps/producer.proto")).getFile());
        // do the same with the deps
        File[] deps = Stream
                .of("mypackage0/producerId.proto", "mypackage2/version.proto", "broken/helloworld.proto")
                .map(s -> new File(
                        Objects.requireNonNull(classLoader.getResource("parseWithDeps/" + s)).getFile()))
                .toArray(File[]::new);
        return Stream.of(Arguments.of(true, true, mainProtoFile, deps),
                Arguments.of(false, true, mainProtoFile, deps),
                Arguments.of(true, false, mainProtoFile, deps),
                Arguments.of(false, false, mainProtoFile, deps));
    }

    // ==================================================================================
    // DISABLED TESTS - Require AST support not yet available in protobuf4j
    // ==================================================================================
    // The following tests use FileDescriptorUtils.fileDescriptorToProtoFile() which
    // converts Google FileDescriptor back to protobuf AST (text format).
    // This method was removed during the wire-schema → protobuf4j migration because:
    //   1. wire-schema provided AST types (ProtoFileElement, etc.)
    //   2. protobuf4j currently does not expose parsed AST
    //
    // To re-enable these tests, one of the following is needed:
    //   A. protobuf4j adds AST support (preferred)
    //   B. Implement our own FileDescriptor → text converter
    //   C. Rewrite tests to work without AST (validate via FileDescriptorProto)
    // ==================================================================================

    @Test
    @Disabled("Requires AST support: fileDescriptorToProtoFile() method was removed (see above)")
    public void fileDescriptorToProtoFile_ParsesJsonNameOptionCorrectly() {
        // Original test: Verified that json_name options were correctly converted when
        // transforming FileDescriptor → ProtoFileElement → canonicalized text.
        // TODO: Consider rewriting to validate json_name options via FileDescriptorProto
    }

    @ParameterizedTest
    @MethodSource("testProtoFileProvider")
    @Disabled("Requires AST support: fileDescriptorToProtoFile() method was removed (see above)")
    public void ParsesFileDescriptorsAndRawSchemaIntoCanonicalizedForm_Accurately(
            Descriptors.FileDescriptor fileDescriptor) throws Exception {
        // Original test: Verified that .proto text → FileDescriptor → ProtoFileElement → text
        // produced consistent canonicalized output.
        // TODO: Consider alternative validation approach using FileDescriptorProto comparison
    }

    @ParameterizedTest
    @MethodSource("testProtoFileProviderForJsonName")
    @Disabled("Requires AST support: fileDescriptorToProtoFile() method was removed (see above)")
    public void ParsesFileDescriptorsAndRawSchemaIntoCanonicalizedForm_ForJsonName_Accurately(
            Descriptors.FileDescriptor fileDescriptor) throws Exception {
        // Original test: Same as above but specifically for json_name option handling.
        // TODO: Consider alternative validation approach using FileDescriptorProto comparison
    }

    // ==================================================================================
    // ACTIVE TESTS - Use protobuf4j
    // ==================================================================================

    @Test
    public void ParsesSchemasWithNoPackageNameSpecified() throws Exception {
        // Test schema loaded from anyFile.proto (no package declaration, uses well-known type)
        String actualFileDescriptorProto = schemaTextToFileDescriptor(ANYFILE_PROTO_SCHEMA, "anyFile.proto")
                .toProto().toString();

        String expectedFileDescriptorProto = "name: \"anyFile.proto\"\n"
                + "dependency: \"google/protobuf/timestamp.proto\"\n" + "message_type {\n"
                + "  name: \"Bar\"\n" + "  field {\n" + "    name: \"c\"\n" + "    number: 4\n"
                + "    label: LABEL_OPTIONAL\n" + "    type: TYPE_MESSAGE\n"
                + "    type_name: \".google.protobuf.Timestamp\"\n" + "  }\n" + "  field {\n"
                + "    name: \"a\"\n" + "    number: 1\n" + "    label: LABEL_REQUIRED\n"
                + "    type: TYPE_INT32\n" + "  }\n" + "  field {\n" + "    name: \"b\"\n" + "    number: 2\n"
                + "    label: LABEL_OPTIONAL\n" + "    type: TYPE_STRING\n" + "  }\n" + "}\n";

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto);
    }

    @ParameterizedTest
    @MethodSource("testParseWithDepsProtoFilesProvider")
    public void testParseProtoFileAndDependenciesOnDifferentPackagesAndKnownType(boolean failFast,
            boolean readFiles, File mainProtoFile, File[] deps)
            throws Descriptors.DescriptorValidationException, FileDescriptorUtils.ParseSchemaException,
            FileDescriptorUtils.ReadSchemaException {
        final Descriptors.FileDescriptor mainProtoFd;
        final Map<String, String> requiredSchemaDeps = new HashMap<>(2);
        if (!readFiles) {
            if (failFast) {
                // it fail-fast by default
                Assertions.assertThrowsExactly(FileDescriptorUtils.ParseSchemaException.class,
                        () -> FileDescriptorUtils.parseProtoFileWithDependencies(mainProtoFile,
                                Set.of(deps)));
                return;
            }
            mainProtoFd = FileDescriptorUtils.parseProtoFileWithDependencies(mainProtoFile, Set.of(deps),
                    requiredSchemaDeps, false);
        } else {
            if (failFast) {
                // it fail-fast by default
                Assertions.assertThrowsExactly(FileDescriptorUtils.ParseSchemaException.class,
                        () -> FileDescriptorUtils.parseProtoFileWithDependencies(
                                readSchemaContent(mainProtoFile), readSchemaContents(deps)));
                return;
            }
            mainProtoFd = FileDescriptorUtils.parseProtoFileWithDependencies(readSchemaContent(mainProtoFile),
                    readSchemaContents(deps), requiredSchemaDeps, false);

        }
        final Map<String, String> expectedSchemaDeps = Map.of("mypackage0/producerId.proto",
                readSelectedFileSchemaAsString("producerId.proto", deps), "mypackage2/version.proto",
                readSelectedFileSchemaAsString("version.proto", deps));
        Assertions.assertEquals(expectedSchemaDeps, requiredSchemaDeps);
        Assertions.assertNotNull(mainProtoFd.findServiceByName("MyService"));
        Assertions.assertNotNull(mainProtoFd.findServiceByName("MyService").findMethodByName("Foo"));
        Descriptors.Descriptor producer = mainProtoFd.findMessageTypeByName("Producer");
        // create a dynamic message with all fields populated
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(producer);
        builder.setField(producer.findFieldByName("name"), "name");
        builder.setField(producer.findFieldByName("timestamp"),
                Timestamp.newBuilder().setSeconds(1634123456).setNanos(789000000).build());
        Descriptors.FieldDescriptor personId = producer.findFieldByName("id");
        // assert that the id field is the expected msg type
        assertEquals("mypackage0.ProducerId", personId.getMessageType().getFullName());
        Descriptors.FieldDescriptor versionId = personId.getMessageType().findFieldByName("id");
        assertEquals("mypackage2.Version", versionId.getMessageType().getFullName());
        // populate all the rest of the fields in the dynamic message
        builder.setField(personId,
                DynamicMessage.newBuilder(personId.getMessageType())
                        .setField(versionId, DynamicMessage.newBuilder(versionId.getMessageType())
                                .setField(versionId.getMessageType().findFieldByName("id"), "id").build())
                        .setField(personId.getMessageType().findFieldByName("name"), "name").build());
        assertNotNull(builder.build());
    }

    private static Collection<FileDescriptorUtils.ProtobufSchemaContent> readSchemaContents(File[] files) {
        // Find common parent directory (parseWithDeps directory)
        if (files.length == 0) {
            return Collections.emptyList();
        }
        Path parentDir = files[0].toPath().getParent().getParent();

        return Arrays.stream(files).map(f -> {
            String relativePath = parentDir.relativize(f.toPath()).toString().replace('\\', '/');
            return FileDescriptorUtils.ProtobufSchemaContent.of(relativePath, readSchemaAsString(f));
        }).collect(Collectors.toList());
    }

    private static FileDescriptorUtils.ProtobufSchemaContent readSchemaContent(File file) {
        // For main proto file, use basename only
        return FileDescriptorUtils.ProtobufSchemaContent.of(file.getName(), readSchemaAsString(file));
    }

    private static String readSelectedFileSchemaAsString(String fileName, File[] files) {
        return Stream.of(files).filter(f -> f.getName().equals(fileName))
                .collect(Collectors.reducing((a, b) -> {
                    throw new IllegalStateException("More than one file with name " + fileName + " found");
                })).map(FileDescriptorUtilsTest::readSchemaAsString).get();
    }

    private static String readSchemaAsString(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to parse schema text to FileDescriptor using protobuf4j.
     */
    private Descriptors.FileDescriptor schemaTextToFileDescriptor(String schema, String fileName)
            throws Exception {
        // Extract package name from schema if present
        Optional<String> packageName = extractPackageName(schema);
        return FileDescriptorUtils.protoFileToFileDescriptor(schema, fileName, packageName);
    }

    /**
     * Extract package name from proto schema text.
     */
    private Optional<String> extractPackageName(String schema) {
        String[] lines = schema.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                String packageName = trimmed.substring(8, trimmed.length() - 1).trim();
                return Optional.of(packageName);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // ProtobufFile Tests (Updated for Phase 3)
    // ==================================================================================

    /**
     * Test for PR #6833: ProtobufFile should parse both text and base64-encoded schemas.
     * This tests that ProtobufFile can handle text-based protobuf schemas.
     */
    @Test
    public void testProtobufFileParseTextSchema() throws IOException {
        ProtobufFile protobufFile = new ProtobufFile(SIMPLE_PROTO_SCHEMA);

        assertNotNull(protobufFile);
        assertEquals("test.example", protobufFile.getPackageName());

        // Verify that fields are correctly indexed
        Map<String, Map<String, DescriptorProtos.FieldDescriptorProto>> fieldMap = protobufFile.getFieldMap();
        assertTrue(fieldMap.containsKey("Person"));

        Map<String, DescriptorProtos.FieldDescriptorProto> personFields = fieldMap.get("Person");
        assertTrue(personFields.containsKey("name"));
        assertTrue(personFields.containsKey("age"));
        assertTrue(personFields.containsKey("email"));
    }

    /**
     * Test for PR #6833: ProtobufFile should parse base64-encoded FileDescriptorProto.
     * This validates the fix where compatibility checking failed because stored schemas
     * (in base64 format) couldn't be parsed.
     */
    @Test
    public void testProtobufFileParseBase64EncodedSchema() throws Exception {
        // Create a FileDescriptorProto from text and encode to base64
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = FileDescriptorUtils
                .protoFileToFileDescriptor(SIMPLE_PROTO_SCHEMA, "test.proto", Optional.of("test.example"))
                .toProto();

        byte[] protoBytes = fileDescriptorProto.toByteArray();
        String base64EncodedSchema = Base64.getEncoder().encodeToString(protoBytes);

        // ProtobufFile should be able to parse the base64-encoded schema
        ProtobufFile protobufFile = new ProtobufFile(base64EncodedSchema);

        assertNotNull(protobufFile);
        assertEquals("test.example", protobufFile.getPackageName());

        Map<String, Map<String, DescriptorProtos.FieldDescriptorProto>> fieldMap = protobufFile.getFieldMap();
        assertTrue(fieldMap.containsKey("Person"));

        Map<String, DescriptorProtos.FieldDescriptorProto> personFields = fieldMap.get("Person");
        assertTrue(personFields.containsKey("name"));
        assertTrue(personFields.containsKey("age"));
        assertTrue(personFields.containsKey("email"));
    }

    /**
     * Test for PR #6833: Verify that text and base64 parsing produce equivalent results.
     */
    @Test
    public void testProtobufFileTextAndBase64Equivalence() throws Exception {
        // Parse from text
        ProtobufFile textProtobufFile = new ProtobufFile(COMPLEX_PROTO_SCHEMA);

        // Create base64 version and parse
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = FileDescriptorUtils
                .protoFileToFileDescriptor(COMPLEX_PROTO_SCHEMA, "complex.proto", Optional.of("test.complex"))
                .toProto();

        String base64EncodedSchema = Base64.getEncoder().encodeToString(fileDescriptorProto.toByteArray());
        ProtobufFile base64ProtobufFile = new ProtobufFile(base64EncodedSchema);

        // Both should produce the same package name
        assertEquals(textProtobufFile.getPackageName(), base64ProtobufFile.getPackageName());

        // Both should have the same message types
        assertEquals(textProtobufFile.getFieldMap().keySet(), base64ProtobufFile.getFieldMap().keySet());

        // Both should have the same enum types
        assertEquals(textProtobufFile.getEnumFieldMap().keySet(), base64ProtobufFile.getEnumFieldMap().keySet());

        // Verify User message fields match
        Map<String, DescriptorProtos.FieldDescriptorProto> textUserFields = textProtobufFile.getFieldMap().get("User");
        Map<String, DescriptorProtos.FieldDescriptorProto> base64UserFields = base64ProtobufFile.getFieldMap().get("User");
        assertEquals(textUserFields.keySet(), base64UserFields.keySet());
    }

    /**
     * Test for PR #6833: Invalid base64 that's not valid protobuf should throw exception.
     */
    @Test
    public void testInvalidBase64ThrowsException() {
        String invalidBase64 = Base64.getEncoder().encodeToString("not a valid protobuf".getBytes());

        Assertions.assertThrows(IOException.class, () -> {
            new ProtobufFile(invalidBase64);
        });
    }

    /**
     * Test basic schema compilation with protobuf4j.
     */
    @Test
    public void testProtoFileToFileDescriptor() throws Exception {
        String schema = """
                syntax = "proto3";
                package test;

                message TestMessage {
                  string id = 1;
                  int32 value = 2;
                }
                """;

        Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                schema, "test.proto", Optional.of("test"));

        assertNotNull(fd);
        assertEquals("test.proto", fd.getName());
        assertEquals("test", fd.getPackage());

        Descriptors.Descriptor messageType = fd.findMessageTypeByName("TestMessage");
        assertNotNull(messageType);
        assertEquals(2, messageType.getFields().size());
    }

    /**
     * Test schema compilation with dependencies.
     */
    @Test
    public void testProtoFileToFileDescriptorWithDependencies() throws Exception {
        String mainSchema = """
                syntax = "proto3";
                package main;

                import "google/protobuf/timestamp.proto";

                message Event {
                  string id = 1;
                  google.protobuf.Timestamp timestamp = 2;
                }
                """;

        Descriptors.FileDescriptor fd = FileDescriptorUtils.protoFileToFileDescriptor(
                mainSchema, "main.proto", Optional.of("main"));

        assertNotNull(fd);
        Descriptors.Descriptor messageType = fd.findMessageTypeByName("Event");
        assertNotNull(messageType);

        Descriptors.FieldDescriptor timestampField = messageType.findFieldByName("timestamp");
        assertNotNull(timestampField);
        assertEquals("google.protobuf.Timestamp", timestampField.getMessageType().getFullName());
    }
}
