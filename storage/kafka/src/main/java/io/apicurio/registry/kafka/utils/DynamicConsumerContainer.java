/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
