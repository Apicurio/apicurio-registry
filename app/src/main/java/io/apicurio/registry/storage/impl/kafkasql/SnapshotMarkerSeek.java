package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Starts the journal partition that holds a restored snapshot's marker at that marker, instead of at
 * the beginning of the journal. The snapshot records the marker's position only, so the seek is done
 * only when every other assigned journal partition is empty: records there could predate the marker,
 * and skipping the scan would apply them on top of a snapshot that already contains them. Every
 * journal message uses the same partition key, so in practice only one partition holds records.
 * <p>
 * Clearing the marker reference makes the consumer read the whole journal and scan for the marker, as
 * it does for snapshots without a recorded position. Runs on the consumer thread, inside poll().
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
        List<TopicPartition> others = partitions.stream().filter(p -> !p.equals(markerPartition)).toList();
        if (!others.isEmpty()) {
            Map<TopicPartition, Long> start = consumer.beginningOffsets(others);
            Map<TopicPartition, Long> end = consumer.endOffsets(others);
            if (others.stream().anyMatch(p -> end.get(p) > start.get(p))) {
                log.info("Reading the journal from the beginning: partitions other than {} hold records, "
                        + "and the snapshot recorded the marker position for partition {} only.",
                        markerPartition, marker.partition());
                unverifiedMarker.set(null);
                return;
            }
        }
        log.info("Starting journal partition {} at the snapshot marker, offset {}.", marker.partition(),
                marker.offset());
        consumer.seek(markerPartition, marker.offset());
    }
}
