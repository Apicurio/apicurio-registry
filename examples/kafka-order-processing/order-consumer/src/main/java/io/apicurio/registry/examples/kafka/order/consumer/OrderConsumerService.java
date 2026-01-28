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

package io.apicurio.registry.examples.kafka.order.consumer;

import io.apicurio.registry.examples.kafka.order.Order;
import io.apicurio.registry.examples.kafka.order.OrderItem;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for consuming order events from Kafka. This service runs in a separate thread and
 * continuously polls for new orders, processing them as they arrive.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class OrderConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderConsumerService.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.topic.name")
    String topicName;

    @ConfigProperty(name = "kafka.consumer.group.id")
    String consumerGroupId;

    @ConfigProperty(name = "apicurio.registry.url")
    String registryUrl;

    private KafkaConsumer<String, Order> consumer;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong orderCount = new AtomicLong(0);

    /**
     * Initializes and starts the Kafka consumer when the application starts.
     *
     * @param event the startup event
     */
    void onStart(@Observes StartupEvent event) {
        LOGGER.info("Initializing Kafka consumer...");
        consumer = createKafkaConsumer();
        consumer.subscribe(Collections.singletonList(topicName));
        LOGGER.info("Kafka consumer initialized. Subscribed to topic: {}", topicName);

        running.set(true);
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this::consumeOrders);
        LOGGER.info("Consumer thread started");
    }

    /**
     * Stops the Kafka consumer when the application shuts down.
     *
     * @param event the shutdown event
     */
    void onShutdown(@Observes ShutdownEvent event) {
        LOGGER.info("Shutting down Kafka consumer...");
        running.set(false);

        if (executorService != null) {
            executorService.shutdown();
        }

        if (consumer != null) {
            consumer.close();
            LOGGER.info("Total orders consumed: {}", orderCount.get());
        }
    }

    /**
     * Main consumer loop that continuously polls for and processes orders.
     */
    private void consumeOrders() {
        LOGGER.info("Starting to consume orders from topic: {}", topicName);

        while (running.get()) {
            try {
                ConsumerRecords<String, Order> records = consumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, Order> record : records) {
                    processOrder(record);
                }

            } catch (Exception e) {
                LOGGER.error("Error while consuming orders", e);
            }
        }

        LOGGER.info("Consumer loop stopped");
    }

    /**
     * Processes a single order record.
     *
     * @param record the consumer record containing the order
     */
    private void processOrder(ConsumerRecord<String, Order> record) {
        try {
            Order order = record.value();
            long count = orderCount.incrementAndGet();

            LOGGER.info("========================================");
            LOGGER.info("Received order #{}", count);
            LOGGER.info("Order ID: {}", order.getOrderId());
            LOGGER.info("Customer: {} ({})", order.getCustomerName(), order.getCustomerId());
            LOGGER.info("Email: {}", order.getCustomerEmail());
            LOGGER.info("Order Date: {}", new Date(order.getOrderTimestamp()));
            LOGGER.info("Status: {}", order.getStatus());
            LOGGER.info("Items:");

            for (OrderItem item : order.getItems()) {
                LOGGER.info("  - {} x {} @ ${} each = ${}",
                    item.getQuantity(),
                    item.getItemName(),
                    String.format("%.2f", item.getUnitPrice()),
                    String.format("%.2f", item.getQuantity() * item.getUnitPrice()));
            }

            LOGGER.info("Total Amount: ${}", String.format("%.2f", order.getTotalAmount()));
            LOGGER.info("========================================");

            // Simulate order processing
            processOrderLogic(order);

        } catch (Exception e) {
            LOGGER.error("Error processing order from partition {} offset {}",
                record.partition(), record.offset(), e);
        }
    }

    /**
     * Simulates business logic for processing an order. In a real application, this would include steps
     * like inventory validation, payment processing, shipping arrangement, etc.
     *
     * @param order the order to process
     */
    private void processOrderLogic(Order order) {
        // Simulate processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LOGGER.info("Order {} processed successfully", order.getOrderId());
    }

    /**
     * Creates and configures a Kafka consumer with Apicurio Registry integration.
     *
     * @return configured KafkaConsumer instance
     */
    private KafkaConsumer<String, Order> createKafkaConsumer() {
        Properties props = new Properties();

        // Kafka configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "order-consumer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());

        // Apicurio Registry configuration
        props.put(SerdeConfig.REGISTRY_URL, registryUrl);
        // Use Java reflection as the Avro Datum Provider
        props.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());

        LOGGER.info("Creating Kafka consumer with bootstrap servers: {}", bootstrapServers);
        LOGGER.info("Consumer group ID: {}", consumerGroupId);
        LOGGER.info("Apicurio Registry URL: {}", registryUrl);

        return new KafkaConsumer<>(props);
    }
}
