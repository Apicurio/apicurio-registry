package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.utils.protobuf.schema.syntax2.TestOrderingSyntax2;
import io.apicurio.registry.utils.protobuf.schema.syntax2.specified.TestOrderingSyntax2Specified;
import io.apicurio.registry.utils.protobuf.schema.syntax3.TestOrderingSyntax3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDescriptorUtilsTest {

    private static Stream<Arguments> testProtoFileProvider() {
        return
            Stream.of(
            TestOrderingSyntax2.getDescriptor(),
            TestOrderingSyntax2Specified.getDescriptor(),
            TestOrderingSyntax3.getDescriptor()
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

        String fileName = fileDescriptor.toProto().getName();
        String expectedSchema = ProtobufTestCaseReader.getRawSchema(fileName);

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema, fileName).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema, fileName).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto, fileName);
        assertEquals(fileDescriptorProto, expectedFileDescriptorProto, fileName);
    }

    private Descriptors.FileDescriptor schemaTextToFileDescriptor(String schema, String fileName) throws Exception {
        ProtoFileElement protoFileElement = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, schema);
        return FileDescriptorUtils.protoFileToFileDescriptor(protoFileElement, fileName);
    }
}