/*
 * Copyright 2020 Red Hat
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

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaStrategyAwareSerDe;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.utils.serde.JsonSchemaKafkaSerializer;
import io.apicurio.registry.utils.serde.JsonSchemaSerDeConstants;
import io.apicurio.registry.utils.serde.ProtobufKafkaDeserializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.serde.strategy.RecordIdStrategy;
import io.apicurio.registry.utils.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.common.serdes.json.Msg;
import io.apicurio.tests.common.serdes.json.ValidMessage;
import io.apicurio.tests.common.serdes.proto.MsgTypes;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
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
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unchecked")
public class KafkaClients {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClients.class);
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";

    private static String bootstrapServers() {
        String bootsrapServers = KafkaFacade.getInstance().bootstrapServers();
        if (bootsrapServers == null) {
            return BOOTSTRAP_SERVERS;
        }
        return bootsrapServers;
    }

    public static Producer<Object, ?> createProducer(String keySerializer,
            String valueSerializer, String topicName, String artifactIdStrategy) {
        return createProducer(new Properties(), keySerializer, valueSerializer, topicName, artifactIdStrategy);
    }
    public static Producer<Object, ?> createProducer(Properties props, String keySerializer,
            String valueSerializer, String topicName, String artifactIdStrategy) {
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topicName);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        // Schema Registry location.
        if (valueSerializer.contains("confluent")) {
            props.putIfAbsent(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, TestUtils.getRegistryApiUrl() + "/ccompat/v6");
            props.putIfAbsent(AbstractKafkaAvroSerDeConfig.AUTO_REGISTER_SCHEMAS, "false");
            props.putIfAbsent(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, artifactIdStrategy);
        } else {
            props.putIfAbsent(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, TestUtils.getRegistryV1ApiUrl());
            props.putIfAbsent(AbstractKafkaStrategyAwareSerDe.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, artifactIdStrategy);
        }

        return new KafkaProducer<>(props);
    }

    public static Consumer<Long, ?> createConsumer(String keyDeserializer,
            String valueDeserializer, String topicName) {
        return createConsumer(new Properties(), keyDeserializer, valueDeserializer, topicName);
    }
    public static Consumer<Long, ?> createConsumer(Properties props, String keyDeserializer,
            String valueDeserializer, String topicName) {
        props.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + topicName);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        //Use Kafka Avro Deserializer.
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        //Schema registry location.
        if (valueDeserializer.contains("confluent")) {
            props.putIfAbsent(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, TestUtils.getRegistryApiUrl() + "/ccompat/v6");
        } else {
            props.putIfAbsent(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, TestUtils.getRegistryV1ApiUrl());
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

    private static CompletableFuture<Integer> produceMessages(String topicName, String subjectName, Schema schema, int messageCount, String keySerializer, String valueSerializer, String artifactIdStrategy, String... schemaKeys) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            Producer<Object, Object> producer = (Producer<Object, Object>) createProducer(keySerializer, valueSerializer, topicName, artifactIdStrategy);

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

    private static CompletableFuture<Integer> consumeMessages(String topicName, int messageCount, String keyDeserializer, String valueDeserializer) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            final Consumer<Long, GenericRecord> consumer = (Consumer<Long, GenericRecord>) KafkaClients.createConsumer(
                    keyDeserializer, valueDeserializer, topicName);
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

    public static CompletableFuture<Integer> produceJsonSchemaApicurioMessages(String topicName, String subjectName,
            int messageCount) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            Properties props = new Properties();
            props.put(JsonSchemaSerDeConstants.REGISTRY_JSON_SCHEMA_VALIDATION_ENABLED, Boolean.TRUE);
            Producer<Object, Msg> producer = (Producer<Object, Msg>) KafkaClients.createProducer(props, StringSerializer.class.getName(),
                    JsonSchemaKafkaSerializer.class.getName(), topicName, SimpleTopicIdStrategy.class.getName());
            LOGGER.debug("++++++++++++++++++ Producer created.");

            int producedMessages = 0;

            try {
                while (producedMessages < messageCount) {
                    // Create the message to send
                    Date now = new Date();
                    Msg message = new ValidMessage();
                    message.setMessage("Hello (" + producedMessages++ + ")!");
                    message.setTime(now.getTime());

                    LOGGER.info("Sending message {} to topic {}", message, topicName);

                    ProducerRecord<Object, Msg> producedRecord = new ProducerRecord<>(topicName, subjectName, message);
                    producer.send(producedRecord);
                    LOGGER.debug("++++++++++++++++++ Produced a message in Kafka!");
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

    public static CompletableFuture<Integer> consumeJsonSchemaApicurioMessages(String topicName, int messageCount) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            Properties props = new Properties();
            props.put(JsonSchemaSerDeConstants.REGISTRY_JSON_SCHEMA_VALIDATION_ENABLED, Boolean.TRUE);
            final Consumer<Long, Msg> consumer = (Consumer<Long, Msg>) KafkaClients.createConsumer(
                    StringDeserializer.class.getName(), JsonSchemaKafkaDeserializer.class.getName(), topicName);
            consumer.subscribe(Collections.singletonList(topicName));

            AtomicInteger consumedMessages = new AtomicInteger();

            try {
                while (consumedMessages.get() < messageCount) {

                    final ConsumerRecords<Long, Msg> records = consumer.poll(Duration.ofSeconds(1));
                    if (records.count() == 0) {
                        LOGGER.info("None found");
                    } else {
                        records.forEach(record -> {
                            consumedMessages.getAndIncrement();
                            LOGGER.info("{} {} {} {}", record.topic(),
                                    record.partition(), record.offset(), record.value().getMessage());
                        });
                    }
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

    public static CompletableFuture<Integer> produceProtobufMessages(String topicName, String subjectName, int messageCount) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            Producer<Object, Message> producer = (Producer<Object, Message>) createProducer(StringSerializer.class.getName(),
                    ProtobufKafkaSerializer.class.getName(), topicName, SimpleTopicIdStrategy.class.getName());
            LOGGER.debug("++++++++++++++++++ Producer created.");

            int producedMessages = 0;

            try {
                while (producedMessages < messageCount) {
                    // Create the message to send
                    Date now = new Date();
                    MsgTypes.Msg msg = MsgTypes.Msg.newBuilder().setWhat("Hello (" + producedMessages++ + ")!").setWhen(now.getTime()).build();

                    LOGGER.info("Sending message {} to topic {}", msg, topicName);

                    ProducerRecord<Object, Message> producedRecord = new ProducerRecord<>(topicName, subjectName, msg);
                    producer.send(producedRecord);
                    LOGGER.debug("++++++++++++++++++ Produced a message in Kafka!");
                }

                LOGGER.info("Produced {} messages", producedMessages);

            } finally {
                producer.flush();
                producer.close();
            }

            return producedMessages;
        });

        try {
            resultPromise.get(90, TimeUnit.SECONDS);
        } catch (Exception e) {
            resultPromise.completeExceptionally(e);
        }

        return resultPromise;

    }

    public static CompletableFuture<Integer> consumeProtobufMessages(String topicName,  int messageCount) {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {
            final Consumer<Long, DynamicMessage> consumer = (Consumer<Long, DynamicMessage>) KafkaClients.createConsumer(
                    StringDeserializer.class.getName(), ProtobufKafkaDeserializer.class.getName(), topicName);
            consumer.subscribe(Collections.singletonList(topicName));

            AtomicInteger consumedMessages = new AtomicInteger();

            try {
                while (consumedMessages.get() < messageCount) {

                    final ConsumerRecords<Long, DynamicMessage> records = consumer.poll(Duration.ofSeconds(1));
                    if (records.count() == 0) {
                        LOGGER.info("None found");
                    } else {
                        records.forEach(record -> {
                            consumedMessages.getAndIncrement();
                            DynamicMessage dm = record.value();
                            Descriptors.Descriptor descriptor = dm.getDescriptorForType();
                            String message = (String) dm.getField(descriptor.findFieldByName("what"));

                            LOGGER.info("{} {} {} {}", record.topic(),
                                    record.partition(), record.offset(), message);
                        });
                    }
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
