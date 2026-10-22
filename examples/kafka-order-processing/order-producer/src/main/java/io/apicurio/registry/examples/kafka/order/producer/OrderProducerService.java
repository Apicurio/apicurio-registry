/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.examples.kafka.order.producer;

import io.apicurio.registry.examples.kafka.order.Order;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for producing order events to Kafka. This service automatically generates and sends
 * random orders at regular intervals using Quarkus Scheduler.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class OrderProducerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderProducerService.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.name")
    String topicName;

    @ConfigProperty(name = "apicurio.registry.url")
    String registryUrl;

    @Inject
    OrderGenerator orderGenerator;

    private Producer<String, Order> producer;
    private final AtomicLong orderCount = new AtomicLong(0);

    /**
     * Initializes the Kafka producer when the application starts.
     *
     * @param event the startup event
     */
    void onStart(@Observes StartupEvent event) {
        LOGGER.info("Initializing Kafka producer...");
        producer = createKafkaProducer();
        LOGGER.info("Kafka producer initialized successfully. Will produce orders to topic: {}", topicName);
    }

    /**
     * Closes the Kafka producer when the application shuts down.
     *
     * @param event the shutdown event
     */
    void onShutdown(@Observes ShutdownEvent event) {
        if (producer != null) {
            LOGGER.info("Closing Kafka producer...");
            producer.close();
            LOGGER.info("Total orders produced: {}", orderCount.get());
        }
    }

    /**
     * Scheduled method that generates and sends an order every 5 seconds.
     */
    @Scheduled(every = "5s")
    void produceOrder() {
        try {
            Order order = orderGenerator.generateOrder();
            sendOrder(order);
        } catch (Exception e) {
            LOGGER.error("Error producing order", e);
        }
    }

    /**
     * Sends an order to the Kafka topic.
     *
     * @param order the order to send
     */
    public void sendOrder(Order order) {
        try {
            ProducerRecord<String, Order> record = new ProducerRecord<>(
                topicName,
                order.getOrderId(),
                order
            );

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOGGER.error("Error sending order {} to Kafka", order.getOrderId(), exception);
                } else {
                    long count = orderCount.incrementAndGet();
                    LOGGER.info("Successfully sent order #{} - ID: {}, Customer: {}, Total: ${}, Items: {}",
                        count,
                        order.getOrderId(),
                        order.getCustomerName(),
                        String.format("%.2f", order.getTotalAmount()),
                        order.getItems().size());
                    LOGGER.debug("Order sent to partition {} with offset {}",
                        metadata.partition(),
                        metadata.offset());
                }
            });

        } catch (Exception e) {
            LOGGER.error("Failed to send order {}", order.getOrderId(), e);
        }
    }

    /**
     * Creates and configures a Kafka producer with Apicurio Registry integration.
     *
     * @return configured KafkaProducer instance
     */
    private Producer<String, Order> createKafkaProducer() {
        Properties props = new Properties();

        // Kafka configuration
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "order-producer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

        // Apicurio Registry configuration
        props.put(SerdeConfig.REGISTRY_URL, registryUrl);
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT_IF_EXISTS, "FIND_OR_CREATE_VERSION");
        // Use Java reflection as the Avro Datum Provider
        props.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());

        LOGGER.info("Creating Kafka producer with bootstrap servers: {}", bootstrapServers);
        LOGGER.info("Apicurio Registry URL: {}", registryUrl);

        return new KafkaProducer<>(props);
    }
}
