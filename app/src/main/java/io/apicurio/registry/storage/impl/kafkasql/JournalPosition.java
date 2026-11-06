package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Where a snapshot marker is in the journal topic. A snapshots-topic record carries it in two
 * optional headers, which readers that predate them ignore.
 */
record JournalPosition(int partition, long offset) {

    static final String PARTITION_HEADER = "journal-partition";
    static final String OFFSET_HEADER = "journal-offset";

    List<Header> toHeaders() {
        return List.of(
                new RecordHeader(PARTITION_HEADER, Integer.toString(partition).getBytes(StandardCharsets.UTF_8)),
                new RecordHeader(OFFSET_HEADER, Long.toString(offset).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * @return the position a snapshots-topic record carries, or {@code null} when it has none or has no
     *         key: without the snapshot id there is no marker to check the position against
     * @throws IllegalArgumentException when the record carries an invalid position
     */
    static JournalPosition ofSnapshotRecord(ConsumerRecord<String, String> snapshotRecord) {
        return snapshotRecord.key() == null ? null : fromHeaders(snapshotRecord.headers());
    }

    /**
     * @return the position in {@code headers}, or {@code null} when they hold none
     * @throws IllegalArgumentException when they hold a missing, non-numeric or negative value
     */
    static JournalPosition fromHeaders(Headers headers) {
        Header partition = headers.lastHeader(PARTITION_HEADER);
        Header offset = headers.lastHeader(OFFSET_HEADER);
        if (partition == null && offset == null) {
            return null;
        }
        JournalPosition position = new JournalPosition(Integer.parseInt(headerValue(partition)),
                Long.parseLong(headerValue(offset)));
        if (position.partition() < 0 || position.offset() < 0) {
            throw new IllegalArgumentException("negative journal position " + position);
        }
        return position;
    }

    private static String headerValue(Header header) {
        return header == null || header.value() == null ? null
                : new String(header.value(), StandardCharsets.UTF_8);
    }
}
