package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDescriptorUtilsTest {

    @Test
    public void fileDescriptorToProtoFile_ParsesJsonNameOptionCorrectly() {
        Descriptors.FileDescriptor fileDescriptor = TestOrdering.getDescriptor().getFile();
        String expectedFieldWithJsonName = "required string street = 1 [json_name = \"Address_Street\"];\n";
        String expectedFieldWithoutJsonName = "optional int32 zip = 2;\n";

        ProtoFileElement protoFile = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptor.toProto());

        String actualSchema = protoFile.toSchema();

        //TODO: Need a better way to compare schema strings.
        assertTrue(actualSchema.contains(expectedFieldWithJsonName));
        assertTrue(actualSchema.contains(expectedFieldWithoutJsonName));
    }

}