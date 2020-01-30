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

package io.apicurio.tests.serdes;

import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaDeserializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.serde.strategy.RecordIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicRecordIdStrategy;
import io.apicurio.tests.RegistryFacade;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaClients {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClients.class);
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";
    private final static String TOPIC = "new-employees";

    public static Producer<Object, Object> createProducer(String keySerializer, String valueSerializer, String topicName, String artifactIdStrategy) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topicName);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        // Schema Registry location.
        if (valueSerializer.contains("confluent")) {
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT + "/ccompat");
            props.put(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, artifactIdStrategy);
        } else {
            props.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, "http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT);
            props.put(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, artifactIdStrategy);
        }

        return new KafkaProducer<>(props);
    }

    public static Consumer<Long, GenericRecord> createConsumer(String keyDeserializer, String valueDeserializer, String topicName) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + topicName);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        //Use Kafka Avro Deserializer.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        //Schema registry location.
        if (valueDeserializer.contains("confluent")) {
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT + "/ccompat");
        } else {
            props.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, "http://" + RegistryFacade.REGISTRY_URL + ":" + RegistryFacade.REGISTRY_PORT);
        }
        return new KafkaConsumer<>(props);
    }

    public static CompletableFuture<Integer> produceAvroConfluentMessagesTopicStrategy(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), KafkaAvroSerializer.class.getName(), TopicNameStrategy.class.getName(), schemaKeys);
    }

    public static CompletableFuture<Integer> produceAvroConfluentMessagesRecordStrategy(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), KafkaAvroSerializer.class.getName(), RecordNameStrategy.class.getName(), schemaKeys);
    }

    public static CompletableFuture<Integer> produceAvroConfluentMessagesTopicRecordStrategy(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), KafkaAvroSerializer.class.getName(), TopicRecordNameStrategy.class.getName(), schemaKeys);
    }

    public static CompletableFuture<Integer> produceAvroApicurioMessagesTopicStrategy(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), AvroKafkaSerializer.class.getName(), TopicIdStrategy.class.getName(), schemaKeys);
    }

    public static CompletableFuture<Integer> produceAvroApicurioMessagesRecordStrategy(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), AvroKafkaSerializer.class.getName(), RecordIdStrategy.class.getName(), schemaKeys);
    }

    public static CompletableFuture<Integer> produceAvroApicurioMessagesTopicRecordStrategy(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), AvroKafkaSerializer.class.getName(), TopicRecordIdStrategy.class.getName(), schemaKeys);
    }

    // TODO create protobuf tests when it's ready
    public static CompletableFuture<Integer> produceProtobufMessages(String topicName, String subjectName, Schema schema, int messageCount, String... schemaKeys) {
        return produceMessages(topicName, subjectName, schema, messageCount, StringSerializer.class.getName(), ProtobufKafkaSerializer.class.getName(), TopicIdStrategy.class.getName(), schemaKeys);
    }

    private static CompletableFuture<Integer> produceMessages(String topicName, String subjectName, Schema schema, int messageCount, String keySerializer, String valueSerializer, String artifactIdStrategy, String... schemaKeys) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            Producer<Object, Object> producer = KafkaClients.createProducer(keySerializer, valueSerializer, topicName, artifactIdStrategy);

            int producedMessages = 0;

            try {
                while (producedMessages < messageCount) {
                    GenericRecord record = new GenericData.Record(schema);
                    String message = "value-" + producedMessages;
                    for (String schemaKey : schemaKeys) {
                        record.put(schemaKey, message);
                    }
                    LOGGER.info("Sending message {} to topic {}", record, topicName);

                    ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, subjectName, record);
                    producer.send(producedRecord);
                    producedMessages++;
                }

                LOGGER.info("Produced {} messages", producedMessages);

            } finally {
                producer.flush();
                producer.close();
            }

            return producedMessages;
        });

        try {
            resultPromise.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            resultPromise.completeExceptionally(e);
        }

        return resultPromise;
    }

    public static CompletableFuture<Integer> consumeAvroConfluentMessages(String topicName,  int messageCount) {
        return consumeMessages(topicName, messageCount, StringDeserializer.class.getName(), KafkaAvroDeserializer.class.getName());
    }

    public static CompletableFuture<Integer> consumeAvroApicurioMessages(String topicName,  int messageCount) {
        return consumeMessages(topicName, messageCount, StringDeserializer.class.getName(), AvroKafkaDeserializer.class.getName());
    }

    public static CompletableFuture<Integer> consumeProtobufMessages(String topicName,  int messageCount) {
        return consumeMessages(topicName, messageCount, StringDeserializer.class.getName(), ProtobufKafkaDeserializer.class.getName());
    }    

    private static CompletableFuture<Integer> consumeMessages(String topicName, int messageCount, String keyDeserializer, String valueDeserializer) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            final Consumer<Long, GenericRecord> consumer = KafkaClients.createConsumer(keyDeserializer, valueDeserializer, topicName);
            consumer.subscribe(Collections.singletonList(topicName));

            AtomicInteger consumedMessages = new AtomicInteger();

            try {
                while (consumedMessages.get() < messageCount) {

                    final ConsumerRecords<Long, GenericRecord> records = consumer.poll(Duration.ofSeconds(1));
                    if (records.count() == 0) {
                        LOGGER.info("None found");
                    } else records.forEach(record -> {
                        consumedMessages.getAndIncrement();
                        LOGGER.info("{} {} {} {}", record.topic(),
                                record.partition(), record.offset(), record.value());
                    });
                }

                LOGGER.info("Consumed {} messages", consumedMessages.get());
            } finally {
                consumer.close();
            }

            return consumedMessages.get();
        });

        try {
            resultPromise.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            resultPromise.completeExceptionally(e);
        }

        return resultPromise;
    }
}
