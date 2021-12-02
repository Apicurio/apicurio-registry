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

package io.apicurio.registry.storage.impl.kafkasql;

import java.util.Properties;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlPartitioner;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.utils.RegistryProperties;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class KafkaSqlFactory {

    @Inject
    @ConfigProperty(name = "registry.kafkasql.bootstrap.servers")
    String bootstrapServers;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.topic", defaultValue = "kafkasql-journal")
    String topic;

    @Inject
    @RegistryProperties(value = "registry.kafkasql.topic")
    Properties topicProperties;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.topic.auto-create", defaultValue = "true")
    Boolean topicAutoCreate;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.consumer.startupLag", defaultValue = "1000")
    Integer startupLag;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.consumer.poll.timeout", defaultValue = "1000")
    Integer pollTimeout;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.coordinator.response-timeout", defaultValue = "30000")
    Integer responseTimeout;

    @Inject
    @RegistryProperties(
            value = {"registry.kafka.common", "registry.kafkasql.producer"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties producerProperties;

    @Inject
    @RegistryProperties(
            value = {"registry.kafka.common", "registry.kafkasql.consumer"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties consumerProperties;

    @Inject
    @RegistryProperties(
            value = {"registry.kafka.common", "registry.kafkasql.admin"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties adminProperties;

    @ApplicationScoped
    @Produces
    public KafkaSqlConfiguration createConfiguration() {
        KafkaSqlConfiguration config = new KafkaSqlConfiguration() {
            @Override
            public String bootstrapServers() {
                return bootstrapServers;
            }
            @Override
            public String topic() {
                return topic;
            }
            @Override
            public Properties topicProperties() {
                return topicProperties;
            }
            @Override
            public boolean isTopicAutoCreate() {
                return topicAutoCreate;
            }
            @Override
            public Integer startupLag() {
                return startupLag;
            }
            @Override
            public Integer pollTimeout() {
                return pollTimeout;
            }
            @Override
            public Integer responseTimeout() {
                return responseTimeout;
            }
            @Override
            public Properties producerProperties() {
                return producerProperties;
            }
            @Override
            public Properties consumerProperties() {
                return consumerProperties;
            }
            @Override
            public Properties adminProperties() {
                return adminProperties;
            }

        };
        return config;
    }

    /**
     * Creates the Kafka producer.
     */
    @ApplicationScoped
    @Produces
    public ProducerActions<MessageKey, MessageValue> createKafkaProducer() {
        Properties props = (Properties) producerProperties.clone();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + UUID.randomUUID().toString());
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.putIfAbsent(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaSqlPartitioner.class);

        // Create the Kafka producer
        KafkaSqlKeySerializer keySerializer = new KafkaSqlKeySerializer();
        KafkaSqlValueSerializer valueSerializer = new KafkaSqlValueSerializer();
        return new AsyncProducer<MessageKey, MessageValue>(props, keySerializer, valueSerializer);
    }

    /**
     * Creates the Kafka consumer.
     */
    @ApplicationScoped
    @Produces
    public KafkaConsumer<MessageKey, MessageValue> createKafkaConsumer() {
        Properties props = (Properties) consumerProperties.clone();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Create the Kafka Consumer
        KafkaSqlKeyDeserializer keyDeserializer = new KafkaSqlKeyDeserializer();
        KafkaSqlValueDeserializer valueDeserializer = new KafkaSqlValueDeserializer();
        KafkaConsumer<MessageKey, MessageValue> consumer = new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
        return consumer;
    }

}
