package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Where {@link SnapshotMarkerSeek} starts the journal consumer once partitions are assigned.
 */
public class SnapshotMarkerSeekTest {

    private static final String TOPIC = "journal";
    private static final TopicPartition P0 = new TopicPartition(TOPIC, 0);
    private static final TopicPartition P1 = new TopicPartition(TOPIC, 1);

    /** Thrown by endOffsets once set; MockConsumer.setOffsetsException only fails beginningOffsets here. */
    private KafkaException endOffsetsFailure;

    private final MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST) {
        @Override
        public synchronized Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
            if (endOffsetsFailure != null) {
                throw endOffsetsFailure;
            }
            return super.endOffsets(partitions);
        }
    };
    private final AtomicReference<JournalPosition> marker = new AtomicReference<>(new JournalPosition(1, 40));

    @Test
    void testSeeksToTheMarkerWhenOtherPartitionsAreEmpty() {
        assign(Map.of(P0, 0L, P1, 50L));

        listener(false).onPartitionsAssigned(List.of(P0, P1));

        Assertions.assertEquals(40, consumer.position(P1));
        Assertions.assertEquals(0, consumer.position(P0));
        Assertions.assertEquals(new JournalPosition(1, 40), marker.get(), "The seek must still be checked");
    }

    @Test
    void testSeeksToTheMarkerOnASinglePartitionJournal() {
        marker.set(new JournalPosition(0, 40));
        assign(Map.of(P0, 50L));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(40, consumer.position(P0));
    }

    @Test
    void testScansTheWholeJournalWhenAnotherPartitionHoldsRecords() {
        assign(Map.of(P0, 3L, P1, 50L));

        listener(false).onPartitionsAssigned(List.of(P0, P1));

        Assertions.assertEquals(0, consumer.position(P1));
        Assertions.assertNull(marker.get(), "Without a seek there is no position to check");
    }

    @Test
    void testDoesNotRewindOnceTheMarkerWasProcessed() {
        assign(Map.of(P0, 0L, P1, 50L));
        consumer.seek(P1, 45);

        listener(true).onPartitionsAssigned(List.of(P0, P1));

        Assertions.assertEquals(45, consumer.position(P1));
    }

    @Test
    void testScansWhenTheMarkerOffsetIsAtTheEndOfThePartition() {
        // A seek there would leave an idle journal with no record to check.
        marker.set(new JournalPosition(0, 50));
        assign(Map.of(P0, 50L));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(0, consumer.position(P0));
        Assertions.assertNull(marker.get(), "Without a seek there is no position to check");
    }

    @Test
    void testScansWhenTheMarkerOffsetIsBelowTheStartOfThePartition() {
        marker.set(new JournalPosition(0, 5));
        assign(Map.of(P0, 50L));
        consumer.updateBeginningOffsets(Map.of(P0, 10L));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(10, consumer.position(P0));
        Assertions.assertNull(marker.get());
    }

    @Test
    void testSeeksToTheMarkerAtTheLastRecordOfThePartition() {
        marker.set(new JournalPosition(0, 49));
        assign(Map.of(P0, 50L));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(49, consumer.position(P0));
        Assertions.assertEquals(new JournalPosition(0, 49), marker.get(), "The seek must still be checked");
    }

    @Test
    void testSeeksToTheMarkerAtTheFirstRecordOfAPartitionThatStartsAboveZero() {
        marker.set(new JournalPosition(0, 10));
        assign(Map.of(P0, 50L));
        consumer.updateBeginningOffsets(Map.of(P0, 10L));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(10, consumer.position(P0));
        Assertions.assertEquals(new JournalPosition(0, 10), marker.get(), "The seek must still be checked");
    }

    @Test
    void testScansWhenTheBeginningOffsetsLookupTimesOut() {
        marker.set(new JournalPosition(0, 40));
        assign(Map.of(P0, 50L));
        consumer.setOffsetsException(new TimeoutException("beginning offsets"));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(0, consumer.position(P0));
        Assertions.assertNull(marker.get(), "Without a seek there is no position to check");
    }

    @Test
    void testScansWhenTheEndOffsetsLookupTimesOut() {
        marker.set(new JournalPosition(0, 40));
        assign(Map.of(P0, 50L));
        endOffsetsFailure = new TimeoutException("end offsets");

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(0, consumer.position(P0));
        Assertions.assertNull(marker.get(), "Without a seek there is no position to check");
    }

    @Test
    void testPropagatesAWakeupDuringTheOffsetsLookup() {
        marker.set(new JournalPosition(0, 40));
        assign(Map.of(P0, 50L));
        endOffsetsFailure = new WakeupException();

        SnapshotMarkerSeek listener = listener(false);
        Assertions.assertThrows(WakeupException.class, () -> listener.onPartitionsAssigned(List.of(P0)));

        Assertions.assertEquals(0, consumer.position(P0));
        Assertions.assertEquals(new JournalPosition(0, 40), marker.get());
    }

    @Test
    void testPropagatesAnInterruptDuringTheOffsetsLookup() {
        marker.set(new JournalPosition(0, 40));
        assign(Map.of(P0, 50L));
        try {
            endOffsetsFailure = new InterruptException("end offsets");

            SnapshotMarkerSeek listener = listener(false);
            Assertions.assertThrows(InterruptException.class, () -> listener.onPartitionsAssigned(List.of(P0)));
        } finally {
            // Creating an InterruptException interrupts the current thread; clear it for the next test.
            Thread.interrupted();
        }

        Assertions.assertEquals(0, consumer.position(P0));
        Assertions.assertEquals(new JournalPosition(0, 40), marker.get());
    }

    @Test
    void testIgnoresAnAssignmentWithoutTheMarkerPartition() {
        assign(Map.of(P0, 50L));

        listener(false).onPartitionsAssigned(List.of(P0));

        Assertions.assertEquals(0, consumer.position(P0));
    }

    private SnapshotMarkerSeek listener(boolean snapshotProcessed) {
        return new SnapshotMarkerSeek(consumer, TOPIC, marker, () -> snapshotProcessed);
    }

    /** Assigns the partitions, each starting at offset 0 and ending at the given offset. */
    private void assign(Map<TopicPartition, Long> endOffsets) {
        consumer.assign(endOffsets.keySet());
        endOffsets.keySet().forEach(p -> consumer.updateBeginningOffsets(Map.of(p, 0L)));
        consumer.updateEndOffsets(endOffsets);
    }
}
