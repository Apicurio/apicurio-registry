package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MessageIndexesUtil {

    private static byte[] bytes(byte value) {
        byte[] rval = { value };
        return rval;
    }

    public static void writeUnsignedVarInt(int value, OutputStream out) throws IOException {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            out.write(bytes((byte) value));
        } else {
            out.write(bytes((byte) (value & 0x7F | 0x80)));
            if ((value & (0xFFFFFFFF << 14)) == 0) {
                out.write(bytes((byte) ((value >>> 7) & 0xFF)));
            } else {
                out.write(bytes((byte) ((value >>> 7) & 0x7F | 0x80)));
                if ((value & (0xFFFFFFFF << 21)) == 0) {
                    out.write(bytes((byte) ((value >>> 14) & 0xFF)));
                } else {
                    out.write(bytes((byte) ((value >>> 14) & 0x7F | 0x80)));
                    if ((value & (0xFFFFFFFF << 28)) == 0) {
                        out.write(bytes((byte) ((value >>> 21) & 0xFF)));
                    } else {
                        out.write(bytes((byte) ((value >>> 21) & 0x7F | 0x80)));
                        out.write(bytes((byte) ((value >>> 28) & 0xFF)));
                    }
                }
            }
        }
    }

    private static void writeVarInt(int value, OutputStream out) throws IOException {
        writeUnsignedVarInt((value << 1) ^ (value >> 31), out);
    }

    public static void writeTo(List<Integer> indexes, OutputStream out) throws IOException {
        writeVarInt(indexes.size(), out);

        for (Integer index : indexes) {
            writeVarInt(index, out);
        }
    }

    public static <T extends Message> List<Integer> getMessageIndexes(ParsedSchema<ProtobufSchema> schema, T object) {
        String name = object.getDescriptorForType().getFullName();

        List<Integer> indexes = new ArrayList<>();
        String[] parts = name.split("\\.");
        List<TypeElement> types = schema.getParsedSchema().getProtoFileElement().getTypes();
        for (String part : parts) {
            int i = 0;
            for (TypeElement type : types) {
                if (type instanceof MessageElement) {
                    if (type.getName().equals(part)) {
                        indexes.add(i);
                        types = type.getNestedTypes();
                        break;
                    }
                    i++;
                }
            }
        }
        return indexes;
    }
}
