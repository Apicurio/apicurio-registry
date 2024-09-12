package io.apicurio.registry.storage.impl.kafkasql.serde;

import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessageKey;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.clients.producer.internals.StickyPartitionCache;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;

import java.util.List;
import java.util.Map;

/**
 * A custom Kafka partitioner that uses the ArtifactId (when available) as the key to proper partitioning. The
 * ArtifactId is extractable from the key in most cases. For some keys (e.g. global rule related messages) no
 * ArtifactId is available. In those cases, a constant unique string is used instead, which ensures that those
 * messages are all put on the same partition.
 */
public class KafkaSqlPartitioner implements Partitioner {

    private final StickyPartitionCache stickyPartitionCache = new StickyPartitionCache();

    public void configure(Map<String, ?> configs) {
    }

    /**
     * Compute the partition for the given record. Do this by extracting the ArtifactId from the key object.
     *
     * @param topic The topic name
     * @param key The key to partition on (or null if no key)
     * @param keyBytes serialized key to partition on (or null if no key)
     * @param value The value to partition on or null
     * @param valueBytes serialized value to partition on or null
     * @param cluster The current cluster metadata
     */
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes,
            Cluster cluster) {
        if (keyBytes == null) {
            return stickyPartitionCache.partition(topic, cluster);
        }

        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();

        // hash the partition key to choose a partition
        KafkaSqlMessageKey msgKey = (KafkaSqlMessageKey) key;
        String partitionKey = msgKey.getPartitionKey();
        return Utils.toPositive(Utils.murmur2(partitionKey.getBytes())) % numPartitions;
    }

    public void close() {
    }

    /**
     * If a batch completed for the current sticky partition, change the sticky partition. Alternately, if no
     * sticky partition has been determined, set one.
     */
    public void onNewBatch(String topic, Cluster cluster, int prevPartition) {
        stickyPartitionCache.nextPartition(topic, cluster, prevPartition);
    }
}
