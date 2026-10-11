package io.apicurio.registry.serde.protobuf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageIndexesUtilTest {

    @Test
    void roundTripsIndexes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageIndexesUtil.writeTo(List.of(1, 300, 70000), out);

        List<Integer> indexes = MessageIndexesUtil.readFrom(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(List.of(1, 300, 70000), indexes);
    }

    @Test
    void emptyStreamThrowsEofException() {
        assertThrows(EOFException.class,
                () -> MessageIndexesUtil.readFrom(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void truncatedMultiByteVarintThrowsEofException() {
        // 0x80 has the continuation bit set but no following byte
        assertThrows(EOFException.class,
                () -> MessageIndexesUtil.readUnsignedVarInt(new ByteArrayInputStream(new byte[] { (byte) 0x80 })));
    }

    @Test
    void truncatedIndexListThrowsEofException() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageIndexesUtil.writeTo(List.of(1, 2), out);
        byte[] full = out.toByteArray();
        byte[] truncated = new byte[full.length - 1];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        assertThrows(EOFException.class, () -> MessageIndexesUtil.readFrom(new ByteArrayInputStream(truncated)));
    }
}
