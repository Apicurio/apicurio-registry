package io.apicurio.registry.serde.protobuf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for MessageIndexesUtil verifying var-int index (de)serialization and
 * Confluent wire-format compatibility for the default message index.
 */
public class MessageIndexesUtilTest {

    @Test
    public void testWriteDefaultIndexIsConfluentCompatible() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageIndexesUtil.writeTo(List.of(0), out);
        // Confluent encodes the default index [0] (the first message type) as a bare 0x00 var-int.
        assertArrayEquals(new byte[]{0x00}, out.toByteArray());
    }

    @Test
    public void testReadSingleZeroByteIsDefaultIndex() throws IOException {
        // Confluent's optimized encoding for [0] is a single 0x00 byte.
        List<Integer> indexes = MessageIndexesUtil.readFrom(new ByteArrayInputStream(new byte[]{0x00}));
        assertEquals(List.of(0), indexes);
    }

    @Test
    public void testWriteReadRoundTripDefaultIndex() throws IOException {
        roundTrip(List.of(0));
    }

    @Test
    public void testWriteReadRoundTripMultipleIndexes() throws IOException {
        roundTrip(List.of(3, 1, 2, 7));
    }

    @Test
    public void testWriteReadRoundTripNegativeIndexes() throws IOException {
        roundTrip(List.of(0, -1, 42, -100));
    }

    private void roundTrip(List<Integer> indexes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageIndexesUtil.writeTo(indexes, out);

        List<Integer> result = MessageIndexesUtil.readFrom(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(indexes, result, "Read indexes should match written indexes after a round trip");
    }
}
