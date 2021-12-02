/*
 * Copyright 2021 Red Hat
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

package io.apicurio.tests.serdes.apicurio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.KafkaFacade;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

/**
 * @author Fabian Martinez
 */
public class SerdesTester<K, P, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerdesTester.class);

    private static final int MILLIS_PER_MESSAGE = 700;
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private boolean autoClose = true;

    public SerdesTester() {
        //empty
    }

    /**
     * @param autoClose the autoClose to set
     */
    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    public Producer<K, P> createProducer(Class<?> keySerializer, Class<?> valueSerializer, String topicName, Class<?> artifactIdStrategy) {
        return createProducer(new Properties(), keySerializer, valueSerializer, topicName, artifactIdStrategy);
    }

    public Producer<K, P> createProducer(Properties props, Class<?> keySerializerClass, Class<?> valueSerializerClass, String topicName, Class<?> artifactIdStrategy) {
        connectionProperties().forEach((k, v) -> {
            props.putIfAbsent(k, v);
        });

        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topicName);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializerClass.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializerClass.getName());
        // Schema Registry location.
        if (valueSerializerClass.getName().contains("confluent")) {
            props.putIfAbsent(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, TestUtils.getRegistryApiUrl() + "/ccompat/v6");
            props.putIfAbsent(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, "false");
            props.putIfAbsent(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, artifactIdStrategy.getName());
        } else {
            props.putIfAbsent(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl());
            props.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, artifactIdStrategy.getName());
        }

        return new KafkaProducer<>(props);
    }

    public Consumer<K, C> createConsumer(Class<?> keyDeserializer, Class<?> valueDeserializer, String topicName) {
        return createConsumer(new Properties(), keyDeserializer, valueDeserializer, topicName);
    }

    public Consumer<K, C> createConsumer(Properties props, Class<?> keyDeserializer, Class<?> valueDeserializer, String topicName) {
        connectionProperties().forEach((k, v) -> {
            props.putIfAbsent(k, v);
        });

        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + topicName);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "500");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
        //Schema registry location.
        if (valueDeserializer.getName().contains("confluent")) {
            props.putIfAbsent(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, TestUtils.getRegistryApiUrl() + "/ccompat/v6");
        } else {
            props.putIfAbsent(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl());
        }

        return new KafkaConsumer<>(props);
    }

    public void produceMessages(Producer<K, P> producer, String topicName, DataGenerator<P> dataGenerator, int messageCount) throws Exception {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {

            int producedMessages = 0;

            try {
                while (producedMessages < messageCount) {
                    P data = dataGenerator.generate(producedMessages);
                    LOGGER.info("Sending message {} to topic {}", data, topicName);
                    ProducerRecord<K, P> producedRecord = new ProducerRecord<>(topicName, data);
                    Future<RecordMetadata> fr = producer.send(producedRecord);

                    if (fr.get(MILLIS_PER_MESSAGE * 2, TimeUnit.MILLISECONDS) != null) {
                        producedMessages++;
                    }

                }
                LOGGER.info("Produced {} messages", producedMessages);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.info("Error with producer future, throwing exception");
                throw new RuntimeException(e);
            } finally {
                producer.flush();
                if (autoClose) {
                    producer.close();
                }
            }

            return producedMessages;
        });

        try {
            Integer messagesSent = resultPromise.get((MILLIS_PER_MESSAGE * messageCount) + 2000, TimeUnit.MILLISECONDS);
            assertEquals(messageCount, messagesSent.intValue());
        } catch (Exception e) {
            throw e;
        }
    }

    public void consumeMessages(Consumer<K, C> consumer, String topicName, int messageCount, Predicate<C> dataValidator) throws Exception {
        CompletableFuture<Integer> resultPromise = CompletableFuture.supplyAsync(() -> {

            consumer.subscribe(Collections.singletonList(topicName));

            AtomicInteger consumedMessages = new AtomicInteger();

            try {
                while (consumedMessages.get() < messageCount) {

                    final ConsumerRecords<K, C> records = consumer.poll(Duration.ofSeconds(1));
                    if (records.count() == 0) {
                        LOGGER.info("None found");
                    } else records.forEach(record -> {

                        if (dataValidator != null) {
                            assertTrue(dataValidator.test(record.value()), "Consumed record validation failed");
                        }

                        consumedMessages.getAndIncrement();
                        LOGGER.info("{} {} {} {}", record.topic(),
                                record.partition(), record.offset(), record.value());
                    });
                }

                LOGGER.info("Consumed {} messages", consumedMessages.get());
            } finally {
                if (autoClose) {
                    consumer.close();
                }
            }

            return consumedMessages.get();
        });

        try {
            Integer messagesConsumed = resultPromise.get((MILLIS_PER_MESSAGE * messageCount) + 2000, TimeUnit.MILLISECONDS);
            assertEquals(messageCount, messagesConsumed.intValue());
        } catch (Exception e) {
            throw e;
        }

    }

    @FunctionalInterface
    public static interface DataGenerator<T> {

        public T generate(int count);

    }

    @FunctionalInterface
    public static interface Validator {

        public boolean validate() throws Exception;

    }

    private static Properties connectionProperties() {
        String bootsrapServers = KafkaFacade.getInstance().bootstrapServers();
        if (bootsrapServers == null) {
            Properties props = new Properties();
            props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            return props;
        } else {
            return KafkaFacade.getInstance().connectionProperties();
        }
    }

}