package io.apicurio.registry.kafka.utils;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Properties;
import java.util.function.Consumer;

/**
 * @author Ales Justin
 */
public class DynamicConsumerContainer<K, V> extends ConsumerContainer<K, V> implements ConsumerActions.DynamicAssignment<K, V> {
    public DynamicConsumerContainer(
        Properties consumerProperties,
        Deserializer<K> keyDeserializer,
        Deserializer<V> valueDeserializer,
        Oneof2<Consumer<? super ConsumerRecord<K, V>>, Consumer<? super ConsumerRecords<K, V>>> recordOrRecordsConsumer
    ) {
        super(consumerProperties, keyDeserializer, valueDeserializer, recordOrRecordsConsumer);
    }

    public DynamicConsumerContainer(
        Properties consumerProperties,
        Deserializer<K> keyDeserializer,
        Deserializer<V> valueDeserializer,
        long consumerPollTimeout, Oneof2<Consumer<? super ConsumerRecord<K, V>>, Consumer<? super ConsumerRecords<K, V>>> recordOrRecordsConsumer,
        long idlePingTimeout,
        Consumer<? super TopicPartition> idlePingConsumer
    ) {
        super(consumerProperties, keyDeserializer, valueDeserializer, consumerPollTimeout, recordOrRecordsConsumer, idlePingTimeout, idlePingConsumer);
    }
}
