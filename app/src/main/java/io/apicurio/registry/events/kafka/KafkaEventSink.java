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
package io.apicurio.registry.events.kafka;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.events.EventSink;
import io.apicurio.registry.utils.RegistryProperties;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class KafkaEventSink implements EventSink {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventSink.class);

    @Inject
    @RegistryProperties(
            value = {"registry.events.kafka.config"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties producerProperties;

    private ProducerActions<UUID, byte[]> producer;
    private Integer partition;

    @ConfigProperty(name = "registry.events.kafka.topic")
    Optional<String> eventsTopic;

    @ConfigProperty(name = "registry.events.kafka.topic-partition")
    Optional<Integer> eventsTopicPartition;

    @PostConstruct
    void init() {
        partition = eventsTopicPartition.orElse(0);
    }

    @Override
    public String name() {
        return "Kafka Sink";
    }

    @Override
    public boolean isConfigured() {
        return eventsTopic.isPresent();
    }

    @Override
    public void handle(Message<Buffer> message) {
        String type = message.headers().get("type");

        log.info("Firing event " + type);

        UUID uuid = UUID.randomUUID();

        Headers headers = new RecordHeaders();
        headers.add("ce_id", uuid.toString().getBytes());
        headers.add("ce_specversion", "1.0".getBytes());
        headers.add("ce_source", "apicurio-registry".getBytes());
        headers.add("ce_type", type.getBytes());
        headers.add("ce_time", Instant.now().toString().getBytes());
        headers.add("content-type", "application/json".getBytes());

        getProducer()
            .apply(new ProducerRecord<UUID, byte[]>(
                    eventsTopic.get(),
                    partition,
                    uuid,
                    message.body().getBytes(),
                    headers));

    }

    public synchronized ProducerActions<UUID, byte[]> getProducer() {
        if (producer == null) {
            producer = new AsyncProducer<UUID, byte[]>(
                    producerProperties,
                    Serdes.UUID().serializer(),
                    Serdes.ByteArray().serializer()
                );
        }
        return producer;
    }

}
