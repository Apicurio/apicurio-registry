package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestOrderingSyntax2;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestSyntax2JavaPackage;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestSyntax2OneOfs;
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
import io.apicurio.registry.utils.protobuf.schema.syntax2.WellKnownTypesTestSyntax2;
import io.apicurio.registry.utils.protobuf.schema.syntax3.customoptions.TestSyntax3CustomOptions;
import io.apicurio.registry.utils.protobuf.schema.syntax3.jsonname.TestSyntax3JsonName;
import io.apicurio.registry.utils.protobuf.schema.syntax3.options.TestOrderingSyntax3Options;
import io.apicurio.registry.utils.protobuf.schema.syntax3.references.TestOrderingSyntax3References;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDescriptorUtilsTest {

    private static Stream<Arguments> testProtoFileProvider() {
        return
            Stream.of(
            TestOrderingSyntax2.getDescriptor(),
            TestOrderingSyntax2OptionsExampleName.getDescriptor(),
            TestOrderingSyntax2Specified.getDescriptor(),
            TestOrderingSyntax3.getDescriptor(),
            TestOrderingSyntax3Options.getDescriptor(),
            TestOrderingSyntax2References.getDescriptor(),
            TestOrderingSyntax3References.getDescriptor(),
            WellKnownTypesTestSyntax3.getDescriptor(),
            WellKnownTypesTestSyntax2.getDescriptor(),
            TestSyntax3Optional.getDescriptor(),
            TestSyntax2OneOfs.getDescriptor(),
            TestSyntax3OneOfs.getDescriptor(),
            TestSyntax2JavaPackage.getDescriptor(),
            TestSyntax3JavaPackage.getDescriptor(),
            TestSyntax2CustomOptions.getDescriptor(),
            TestSyntax3CustomOptions.getDescriptor()
        )
        .map(Descriptors.FileDescriptor::getFile)
        .map(Arguments::of);
    }

    private static Stream<Arguments> testProtoFileProviderForJsonName() {
        return
            Stream.of(
                TestSyntax2JsonName.getDescriptor(),
                TestSyntax3JsonName.getDescriptor()
            )
            .map(Descriptors.FileDescriptor::getFile)
            .map(Arguments::of);
    }

    @Test
    public void fileDescriptorToProtoFile_ParsesJsonNameOptionCorrectly() {
        Descriptors.FileDescriptor fileDescriptor = TestOrderingSyntax2.getDescriptor().getFile();
        String expectedFieldWithJsonName = "required string street = 1 [json_name = \"Address_Street\"];\n";
        String expectedFieldWithoutJsonName = "optional int32 zip = 2 [deprecated = true];\n";

        ProtoFileElement protoFile = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptor.toProto());

        String actualSchema = protoFile.toSchema();

        //TODO: Need a better way to compare schema strings.
        assertTrue(actualSchema.contains(expectedFieldWithJsonName));
        assertTrue(actualSchema.contains(expectedFieldWithoutJsonName));
    }

    @ParameterizedTest
    @MethodSource("testProtoFileProvider")
    public void ParsesFileDescriptorsAndRawSchemaIntoCanonicalizedForm_Accurately(Descriptors.FileDescriptor fileDescriptor) throws Exception {
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String fileName = fileDescriptorProto.getName();
        String expectedSchema = ProtobufTestCaseReader.getRawSchema(fileName);

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema, fileName).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema, fileName).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto, fileName);
        //We are comparing the generated fileDescriptor against the original fileDescriptorProto generated by Protobuf compiler.
        //TODO: Square library doesn't respect the ordering of OneOfs and Proto3 optionals.
        //This will be fixed in upcoming square version, https://github.com/square/wire/pull/2046

        assertThat(expectedFileDescriptorProto).ignoringRepeatedFieldOrder().isEqualTo(fileDescriptorProto);
    }

    @Test
    public void ParsesSchemasWithNoPackageNameSpecified() throws Exception {
        String schemaDefinition = "import \"google/protobuf/timestamp.proto\"; message Bar {optional google.protobuf.Timestamp c = 4; required int32 a = 1; optional string b = 2; }";
        String actualFileDescriptorProto = schemaTextToFileDescriptor(schemaDefinition, "anyFile.proto").toProto().toString();

        String expectedFileDescriptorProto = "name: \"anyFile.proto\"\n"
            + "dependency: \"google/protobuf/timestamp.proto\"\n"
            + "message_type {\n"
            + "  name: \"Bar\"\n"
            + "  field {\n"
            + "    name: \"c\"\n"
            + "    number: 4\n"
            + "    label: LABEL_OPTIONAL\n"
            + "    type: TYPE_MESSAGE\n"
            + "    type_name: \".google.protobuf.Timestamp\"\n"
            + "  }\n"
            + "  field {\n"
            + "    name: \"a\"\n"
            + "    number: 1\n"
            + "    label: LABEL_REQUIRED\n"
            + "    type: TYPE_INT32\n"
            + "  }\n"
            + "  field {\n"
            + "    name: \"b\"\n"
            + "    number: 2\n"
            + "    label: LABEL_OPTIONAL\n"
            + "    type: TYPE_STRING\n"
            + "  }\n"
            + "}\n";

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto);
    }

    private Descriptors.FileDescriptor schemaTextToFileDescriptor(String schema, String fileName) throws Exception {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, schema);
        return FileDescriptorUtils.protoFileToFileDescriptor(schema, fileName, Optional.ofNullable(protoFileElement.getPackageName()));
    }

    @ParameterizedTest
    @MethodSource("testProtoFileProviderForJsonName")
    public void ParsesFileDescriptorsAndRawSchemaIntoCanonicalizedForm_ForJsonName_Accurately
            (Descriptors.FileDescriptor fileDescriptor) throws Exception {
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String fileName = fileDescriptorProto.getName();
        String expectedSchema = ProtobufTestCaseReader.getRawSchema(fileName);

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema, fileName).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema, fileName).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto, fileName);
        //We are comparing the generated fileDescriptor against the original fileDescriptorProto generated by Protobuf compiler.
        //TODO: Square library doesn't respect the ordering of OneOfs and Proto3 optionals.
        //This will be fixed in upcoming square version, https://github.com/square/wire/pull/2046

        // This assertion is not working for json_name as the generation of FileDescriptorProto will always contain
        // the json_name field as long as it is specifies (no matter it is default or non default)
//        assertThat(expectedFileDescriptorProto).ignoringRepeatedFieldOrder().isEqualTo(fileDescriptorProto);
    }
}