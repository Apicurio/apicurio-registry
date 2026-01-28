package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Timestamp;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestOrderingSyntax2;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestSyntax2JavaPackage;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestSyntax2OneOfs;
import io.apicurio.registry.utils.protobuf.schema.syntax2.WellKnownTypesTestSyntax2;
import io.apicurio.registry.utils.protobuf.schema.syntax2.customoptions.TestSyntax2CustomOptions;
import io.apicurio.registry.utils.protobuf.schema.syntax2.jsonname.TestSyntax2JsonName;
import io.apicurio.registry.utils.protobuf.schema.syntax2.options.example.TestOrderingSyntax2OptionsExampleName;
import io.apicurio.registry.utils.protobuf.schema.syntax2.references.TestOrderingSyntax2References;
import io.apicurio.registry.utils.protobuf.schema.syntax2.specified.TestOrderingSyntax2Specified;
import io.apicurio.registry.utils.protobuf.schema.syntax3.TestOrderingSyntax3;
import io.apicurio.registry.utils.protobuf.schema.syntax3.TestSyntax3JavaPackage;
import io.apicurio.registry.utils.protobuf.schema.syntax3.TestSyntax3OneOfs;
import io.apicurio.registry.utils.protobuf.schema.syntax3.TestSyntax3Optional;
import io.apicurio.registry.utils.protobuf.schema.syntax3.WellKnownTypesTestSyntax3;
import io.apicurio.registry.utils.protobuf.schema.syntax3.customoptions.TestSyntax3CustomOptions;
import io.apicurio.registry.utils.protobuf.schema.syntax3.jsonname.TestSyntax3JsonName;
import io.apicurio.registry.utils.protobuf.schema.syntax3.options.TestOrderingSyntax3Options;
import io.apicurio.registry.utils.protobuf.schema.syntax3.references.TestOrderingSyntax3References;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.Base64;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDescriptorUtilsTest {

    // Test schema for base64 encoding tests (PR #6833)
    private static final String SIMPLE_PROTO_SCHEMA = """
            syntax = "proto3";
            package test.example;

            message Person {
              string name = 1;
              int32 age = 2;
              string email = 3;
            }
            """;

    private static final String COMPLEX_PROTO_SCHEMA = """
            syntax = "proto3";
            package test.complex;

            enum Status {
              UNKNOWN = 0;
              ACTIVE = 1;
              INACTIVE = 2;
            }

            message Address {
              string street = 1;
              string city = 2;
              int32 zip = 3;
            }

            message User {
              string id = 1;
              string name = 2;
              Status status = 3;
              Address address = 4;
            }
            """;

    // Test schemas with dependencies for base64 binary format testing (Issue #7066)
    private static final String DEP_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            message Dep {
              string name = 1;
            }
            """;

    private static final String ROOT_PROTO_SCHEMA = """
            syntax = "proto3";
            package test;
            import "dep.proto";
            message Root {
              Dep d = 1;
            }
            """;

    private static Stream<Arguments> testProtoFileProvider() {
        return Stream.of(TestOrderingSyntax2.getDescriptor(),
                TestOrderingSyntax2OptionsExampleName.getDescriptor(),
                TestOrderingSyntax2Specified.getDescriptor(), TestOrderingSyntax3.getDescriptor(),
                TestOrderingSyntax3Options.getDescriptor(), TestOrderingSyntax2References.getDescriptor(),
                TestOrderingSyntax3References.getDescriptor(), WellKnownTypesTestSyntax3.getDescriptor(),
                WellKnownTypesTestSyntax2.getDescriptor(), TestSyntax3Optional.getDescriptor(),
                TestSyntax2OneOfs.getDescriptor(), TestSyntax3OneOfs.getDescriptor(),
                TestSyntax2JavaPackage.getDescriptor(), TestSyntax3JavaPackage.getDescriptor(),
                TestSyntax2CustomOptions.getDescriptor(), TestSyntax3CustomOptions.getDescriptor())
                .map(Descriptors.FileDescriptor::getFile).map(Arguments::of);
    }

    private static Stream<Arguments> testProtoFileProviderForJsonName() {
        return Stream.of(TestSyntax2JsonName.getDescriptor(), TestSyntax3JsonName.getDescriptor())
                .map(Descriptors.FileDescriptor::getFile).map(Arguments::of);
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

    @Test
    public void fileDescriptorToProtoFile_ParsesJsonNameOptionCorrectly() {
        Descriptors.FileDescriptor fileDescriptor = TestOrderingSyntax2.getDescriptor().getFile();
        String expectedFieldWithJsonName = "required string street = 1 [json_name = \"Address_Street\"];\n";
        String expectedFieldWithoutJsonName = "optional int32 zip = 2 [deprecated = true];\n";

        ProtoFileElement protoFile = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptor.toProto());

        String actualSchema = protoFile.toSchema();

        // TODO: Need a better way to compare schema strings.
        assertTrue(actualSchema.contains(expectedFieldWithJsonName));
        assertTrue(actualSchema.contains(expectedFieldWithoutJsonName));
    }

    @ParameterizedTest
    @MethodSource("testProtoFileProvider")
    public void ParsesFileDescriptorsAndRawSchemaIntoCanonicalizedForm_Accurately(
            Descriptors.FileDescriptor fileDescriptor) throws Exception {
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String fileName = fileDescriptorProto.getName();
        String expectedSchema = ProtobufTestCaseReader.getRawSchema(fileName);

        // Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(
                expectedSchema, fileName).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(
                actualSchema, fileName).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto, fileName);
        // We are comparing the generated fileDescriptor against the original fileDescriptorProto generated by
        // Protobuf compiler.
        // TODO: Square library doesn't respect the ordering of OneOfs and Proto3 optionals.
        // This will be fixed in upcoming square version, https://github.com/square/wire/pull/2046

        assertThat(expectedFileDescriptorProto).ignoringRepeatedFieldOrder().isEqualTo(fileDescriptorProto);
    }

    @Test
    public void ParsesSchemasWithNoPackageNameSpecified() throws Exception {
        String schemaDefinition = "import \"google/protobuf/timestamp.proto\"; message Bar {optional google.protobuf.Timestamp c = 4; required int32 a = 1; optional string b = 2; }";
        String actualFileDescriptorProto = schemaTextToFileDescriptor(schemaDefinition, "anyFile.proto")
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
        return Arrays.stream(files).map(FileDescriptorUtilsTest::readSchemaContent)
                .collect(Collectors.toList());
    }

    private static FileDescriptorUtils.ProtobufSchemaContent readSchemaContent(File file) {
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

    private Descriptors.FileDescriptor schemaTextToFileDescriptor(String schema, String fileName)
            throws Exception {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION,
                schema);
        return FileDescriptorUtils.protoFileToFileDescriptor(schema, fileName,
                Optional.ofNullable(protoFileElement.getPackageName()));
    }

    @ParameterizedTest
    @MethodSource("testProtoFileProviderForJsonName")
    public void ParsesFileDescriptorsAndRawSchemaIntoCanonicalizedForm_ForJsonName_Accurately(
            Descriptors.FileDescriptor fileDescriptor) throws Exception {
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String fileName = fileDescriptorProto.getName();
        String expectedSchema = ProtobufTestCaseReader.getRawSchema(fileName);

        // Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(
                expectedSchema, fileName).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(
                actualSchema, fileName).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto, fileName);
        // We are comparing the generated fileDescriptor against the original fileDescriptorProto generated by
        // Protobuf compiler.
        // TODO: Square library doesn't respect the ordering of OneOfs and Proto3 optionals.
        // This will be fixed in upcoming square version, https://github.com/square/wire/pull/2046

        // This assertion is not working for json_name as the generation of FileDescriptorProto will always
        // contain
        // the json_name field as long as it is specifies (no matter it is default or non default)
        // assertThat(expectedFileDescriptorProto).ignoringRepeatedFieldOrder().isEqualTo(fileDescriptorProto);
    }

    /**
     * Test for PR #6833: ProtobufFile should parse both text and base64-encoded schemas.
     * This tests that ProtobufFile.toProtoFileElement() can handle text-based protobuf schemas.
     */
    @Test
    public void testProtobufFileParseTextSchema() {
        ProtobufFile protobufFile = new ProtobufFile(SIMPLE_PROTO_SCHEMA);

        assertNotNull(protobufFile);
        assertEquals("test.example", protobufFile.getPackageName());

        // Verify that fields are correctly indexed
        var fieldMap = protobufFile.getFieldMap();
        assertTrue(fieldMap.containsKey("Person"));

        var personFields = fieldMap.get("Person");
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

        var fieldMap = protobufFile.getFieldMap();
        assertTrue(fieldMap.containsKey("Person"));

        var personFields = fieldMap.get("Person");
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
        var textUserFields = textProtobufFile.getFieldMap().get("User");
        var base64UserFields = base64ProtobufFile.getFieldMap().get("User");
        assertEquals(textUserFields.keySet(), base64UserFields.keySet());
    }

    /**
     * Test for PR #6833: Static method should handle both text and base64.
     */
    @Test
    public void testToProtoFileElementWithBase64() throws Exception {
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = FileDescriptorUtils
                .protoFileToFileDescriptor(SIMPLE_PROTO_SCHEMA, "test.proto", Optional.of("test.example"))
                .toProto();

        String base64EncodedSchema = Base64.getEncoder().encodeToString(fileDescriptorProto.toByteArray());

        ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(base64EncodedSchema);

        assertNotNull(protoFileElement);
        assertEquals("test.example", protoFileElement.getPackageName());
        assertEquals(1, protoFileElement.getTypes().size());
    }

    /**
     * Test for PR #6833: Invalid base64 that's not valid protobuf should throw exception.
     */
    @Test
    public void testInvalidBase64ThrowsException() {
        String invalidBase64 = Base64.getEncoder().encodeToString("not a valid protobuf".getBytes());

        Assertions.assertThrows(RuntimeException.class, () -> {
            new ProtobufFile(invalidBase64);
        });
    }

    /**
     * Test for Issue #7066: Parse proto file with binary (base64-encoded) dependencies.
     * This validates that dependencies in binary format can be used when resolving references.
     */
    @Test
    public void testParseProtoFileWithBinaryDependencies() throws Exception {
        // Create binary (base64) version of dependency
        DescriptorProtos.FileDescriptorProto depProto = FileDescriptorUtils
            .protoFileToFileDescriptor(DEP_PROTO_SCHEMA, "dep.proto", Optional.of("test"))
            .toProto();
        String base64Dep = Base64.getEncoder().encodeToString(depProto.toByteArray());

        // Main schema as text
        Set<FileDescriptorUtils.ProtobufSchemaContent> deps = Set.of(
            FileDescriptorUtils.ProtobufSchemaContent.of("dep.proto", base64Dep));

        FileDescriptorUtils.ProtobufSchemaContent main =
            FileDescriptorUtils.ProtobufSchemaContent.of("root.proto", ROOT_PROTO_SCHEMA);

        // Should successfully parse with binary dependency
        Descriptors.FileDescriptor fd = FileDescriptorUtils.parseProtoFileWithDependencies(main, deps, null, true, true);
        assertNotNull(fd);
        assertNotNull(fd.findMessageTypeByName("Root"));
    }

    /**
     * Test for Issue #7066: Parse proto file when both main and dependencies are in binary format.
     * This test uses a simple self-contained schema to verify binary format parsing.
     */
    @Test
    public void testParseProtoFileAllBinaryFormat() throws Exception {
        // Create binary version of a simple schema
        DescriptorProtos.FileDescriptorProto simpleProto = FileDescriptorUtils
            .protoFileToFileDescriptor(SIMPLE_PROTO_SCHEMA, "simple.proto", Optional.of("test.example"))
            .toProto();
        String base64Simple = Base64.getEncoder().encodeToString(simpleProto.toByteArray());

        // Parse using our binary fallback mechanism (no dependencies needed for this test)
        FileDescriptorUtils.ProtobufSchemaContent main =
            FileDescriptorUtils.ProtobufSchemaContent.of("simple.proto", base64Simple);

        Descriptors.FileDescriptor fd = FileDescriptorUtils.parseProtoFileWithDependencies(main, Set.of(), null, true, true);
        assertNotNull(fd);
        assertNotNull(fd.findMessageTypeByName("Person"));
    }

    /**
     * Test for Issue #7066: Test the parseWithBinaryFallback helper method directly.
     */
    @Test
    public void testParseWithBinaryFallbackHelper() throws Exception {
        // Test the new helper method directly
        DescriptorProtos.FileDescriptorProto proto = FileDescriptorUtils
            .protoFileToFileDescriptor(SIMPLE_PROTO_SCHEMA, "test.proto", Optional.of("test.example"))
            .toProto();
        String base64 = Base64.getEncoder().encodeToString(proto.toByteArray());

        // Text format should work
        ProtoFileElement textResult = FileDescriptorUtils.parseWithBinaryFallback(
            FileDescriptorUtils.DEFAULT_LOCATION, SIMPLE_PROTO_SCHEMA);
        assertNotNull(textResult);

        // Binary format should work via fallback
        ProtoFileElement binaryResult = FileDescriptorUtils.parseWithBinaryFallback(
            FileDescriptorUtils.DEFAULT_LOCATION, base64);
        assertNotNull(binaryResult);

        // Both should produce same package
        assertEquals(textResult.getPackageName(), binaryResult.getPackageName());
    }
}