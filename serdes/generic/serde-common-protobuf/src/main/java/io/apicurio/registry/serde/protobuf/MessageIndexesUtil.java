package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MessageIndexesUtil {

    private static IllegalArgumentException illegalVarintException(int value) {
        throw new IllegalArgumentException("Varint is too long, the most significant bit in the 5th byte is set, " +
                "converted value: " + Integer.toHexString(value));
    }

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
    static int readUnsignedVarInt(InputStream in) throws IOException {
        byte tmp = (byte) in.read();
        if (tmp >= 0) {
            return tmp;
        } else {
            int result = tmp & 127;
            if ((tmp = (byte) in.read()) >= 0) {
                result |= tmp << 7;
            } else {
                result |= (tmp & 127) << 7;
                if ((tmp = (byte) in.read()) >= 0) {
                    result |= tmp << 14;
                } else {
                    result |= (tmp & 127) << 14;
                    if ((tmp = (byte) in.read()) >= 0) {
                        result |= tmp << 21;
                    } else {
                        result |= (tmp & 127) << 21;
                        result |= (tmp = (byte) in.read()) << 28;
                        if (tmp < 0) {
                            throw illegalVarintException(result);
                        }
                    }
                }
            }
            return result;
        }
    }

    public static int readVarInt(InputStream in) throws IOException {
        int value = readUnsignedVarInt(in);
        return (value >>> 1) ^ -(value & 1);
    }

    public static List<Integer> readFrom(InputStream in) throws IOException {
        int size = readVarInt(in);
        if (size == 0) {
            return List.of(0);
        }
        List<Integer> indexes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indexes.add(readVarInt(in));
        }
        return indexes;
    }

    public static <T extends Message> List<Integer> getMessageIndexes(ParsedSchema<ProtobufSchema> schema, T object) {
        String name = object.getDescriptorForType().getFullName();

        List<Integer> indexes = new ArrayList<>();
        String[] parts = name.split("\\.");

        // Start with top-level messages from the FileDescriptor
        List<Descriptors.Descriptor> messages = schema.getParsedSchema().getFileDescriptor().getMessageTypes();

        for (String part : parts) {
            int i = 0;
            for (Descriptors.Descriptor message : messages) {
                if (message.getName().equals(part)) {
                    indexes.add(i);
                    // Move to nested types for next iteration
                    messages = message.getNestedTypes();
                    break;
                }
                i++;
            }
        }
        return indexes;
    }
}
