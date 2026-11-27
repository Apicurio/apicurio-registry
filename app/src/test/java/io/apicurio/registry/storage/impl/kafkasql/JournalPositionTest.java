package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The journal position a snapshots-topic record carries in its headers.
 */
public class JournalPositionTest {

    @Test
    void testHeadersRoundTrip() {
        Headers headers = new RecordHeaders(new JournalPosition(2, 100_052L).toHeaders());

        Assertions.assertEquals("2", value(headers, "journal-partition"));
        Assertions.assertEquals("100052", value(headers, "journal-offset"));
        Assertions.assertEquals(new JournalPosition(2, 100_052L), JournalPosition.fromHeaders(headers));
    }

    @Test
    void testRecordWithoutPositionHasNone() {
        // Written by a registry that predates the position, or carrying unrelated headers.
        Headers headers = new RecordHeaders().add("other", "1".getBytes(StandardCharsets.UTF_8));

        Assertions.assertNull(JournalPosition.fromHeaders(headers));
    }

    @Test
    void testSnapshotRecordWithoutKeyHasNoPosition() {
        // Without the snapshot id there is no marker to check the position against.
        Headers headers = new RecordHeaders(new JournalPosition(0, 7L).toHeaders());

        Assertions.assertNull(JournalPosition.ofSnapshotRecord(snapshotRecord(null, headers)));
        Assertions.assertEquals(new JournalPosition(0, 7L),
                JournalPosition.ofSnapshotRecord(snapshotRecord("snapshot-id", headers)));
    }

    @Test
    void testInvalidPositionIsRejected() {
        assertRejected(headers("0", null));
        assertRejected(headers(null, "5"));
        assertRejected(headers("zero", "5"));
        assertRejected(headers("0", "5x"));
        assertRejected(headers("-1", "5"));
        assertRejected(headers("0", "-5"));
        assertRejected(new RecordHeaders().add("journal-partition", null).add("journal-offset",
                "5".getBytes(StandardCharsets.UTF_8)));
    }

    private static ConsumerRecord<String, String> snapshotRecord(String key, Headers headers) {
        return new ConsumerRecord<>("snapshots", 0, 0L, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE, key, "/store/snapshot.sql.gz", headers,
                Optional.empty());
    }

    private static void assertRejected(Headers headers) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> JournalPosition.fromHeaders(headers),
                () -> "Accepted " + headers);
    }

    private static Headers headers(String partition, String offset) {
        RecordHeaders headers = new RecordHeaders();
        if (partition != null) {
            headers.add("journal-partition", partition.getBytes(StandardCharsets.UTF_8));
        }
        if (offset != null) {
            headers.add("journal-offset", offset.getBytes(StandardCharsets.UTF_8));
        }
        return headers;
    }

    private static String value(Headers headers, String name) {
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
