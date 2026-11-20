package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Starts the journal partition that holds a restored snapshot's marker at that marker, instead of at
 * the beginning of the journal. The seek is done only when two preconditions hold at assignment time:
 * <ul>
 * <li>Every other assigned journal partition is empty. The snapshot records the marker's position only,
 * so records there could predate the marker, and skipping the scan would apply them on top of a
 * snapshot that already contains them. Every journal message uses the same partition key, so in
 * practice only one partition holds records.</li>
 * <li>The recorded offset lies inside the marker partition's offsets, from its beginning offset
 * (inclusive) to its end offset (exclusive). Only there is a record that the consumer can check.</li>
 * </ul>
 * <p>
 * Clearing the marker reference makes the consumer read the whole journal and scan for the marker, as
 * it does for snapshots without a recorded position. That is also what happens when the partition
 * offsets cannot be looked up. Runs on the consumer thread, inside poll().
 */
final class SnapshotMarkerSeek implements ConsumerRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(SnapshotMarkerSeek.class);

    private final Consumer<?, ?> consumer;
    private final String topic;
    private final AtomicReference<JournalPosition> unverifiedMarker;
    private final BooleanSupplier snapshotProcessed;

    SnapshotMarkerSeek(Consumer<?, ?> consumer, String topic, AtomicReference<JournalPosition> unverifiedMarker,
            BooleanSupplier snapshotProcessed) {
        this.consumer = consumer;
        this.topic = topic;
        this.unverifiedMarker = unverifiedMarker;
        this.snapshotProcessed = snapshotProcessed;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        JournalPosition marker = unverifiedMarker.get();
        // After the marker is processed, a later rebalance must not rewind the consumer.
        if (marker == null || snapshotProcessed.getAsBoolean()) {
            return;
        }
        TopicPartition markerPartition = new TopicPartition(topic, marker.partition());
        if (!partitions.contains(markerPartition)) {
            return;
        }
        Map<TopicPartition, Long> start;
        Map<TopicPartition, Long> end;
        try {
            start = consumer.beginningOffsets(partitions);
            end = consumer.endOffsets(partitions);
        } catch (WakeupException | InterruptException e) {
            // Both extend KafkaException, but they must reach poll() so the consumer can stop.
            throw e;
        } catch (KafkaException e) {
            log.warn("Reading the journal from the beginning: could not look up the offsets of journal "
                    + "partitions {}.", partitions, e);
            unverifiedMarker.set(null);
            return;
        }
        List<TopicPartition> others = partitions.stream().filter(p -> !p.equals(markerPartition)).toList();
        if (others.stream().anyMatch(p -> end.get(p) > start.get(p))) {
            log.info("Reading the journal from the beginning: partitions other than {} hold records, "
                    + "and the snapshot recorded the marker position for partition {} only.",
                    markerPartition, marker.partition());
            unverifiedMarker.set(null);
            return;
        }
        // A real marker is a journal record, so its offset lies in [start, end). A seek at or past the end
        // would leave the consumer polling an idle journal: no record arrives, the check of the first
        // record never runs, and the registry never becomes ready. Below the start the record has been
        // removed: the journal is verified at startup to keep records forever, so that only follows a
        // manual record deletion or a retention set with the topic verification override. Both cases scan
        // instead, which finds the marker if it is elsewhere in the partition. If the marker is not in the
        // journal at all, startup waits for it, as it does for a snapshot without a recorded position.
        long markerStart = start.get(markerPartition);
        long markerEnd = end.get(markerPartition);
        if (marker.offset() < markerStart || marker.offset() >= markerEnd) {
            log.warn("Reading the journal from the beginning: the snapshot recorded marker offset {} for "
                    + "partition {}, outside the partition's offsets [{}, {}).", marker.offset(),
                    marker.partition(), markerStart, markerEnd);
            unverifiedMarker.set(null);
            return;
        }
        log.info("Starting journal partition {} at the snapshot marker, offset {}.", marker.partition(),
                marker.offset());
        consumer.seek(markerPartition, marker.offset());
    }
}
