package io.apicurio.registry.streams.utils;

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.kafka.SelfSerde;
import org.apache.kafka.streams.KafkaStreams;

/**
 * KafkaStream state serde.
 *
 * @author Ales Justin
 */
public class StateSerde extends SelfSerde<KafkaStreams.State> {
    @Override
    public KafkaStreams.State deserialize(String topic, byte[] data) {
        return KafkaStreams.State.valueOf(IoUtil.toString(data));
    }

    @Override
    public byte[] serialize(String topic, KafkaStreams.State data) {
        return IoUtil.toBytes(data.toString());
    }
}
