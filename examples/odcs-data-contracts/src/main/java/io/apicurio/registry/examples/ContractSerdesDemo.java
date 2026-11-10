package io.apicurio.registry.examples;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Step 9 of GUIDE.md: produces three order events with the Apicurio Avro serializer, with contract
 * rules enabled. The serializer fetches the contract rules of orders/OrderEvent from the registry
 * (global, artifact and version rules) and runs them before writing each record: the valid order is
 * sent, while the order with a negative amount and the order without an ID are rejected before they
 * reach Kafka. A consumer then reads back what was sent.
 *
 * <p>Requires the example to be running (docker compose up -d) and ./demo.sh to have been run.
 * Override the endpoints with the REGISTRY_URL and KAFKA_BOOTSTRAP environment variables.
 */
public class ContractSerdesDemo {

    private static final String REGISTRY_URL = env("REGISTRY_URL", "http://localhost:8081/apis/registry/v3");
    private static final String KAFKA_BOOTSTRAP = env("KAFKA_BOOTSTRAP", "localhost:9092");
    private static final String TOPIC = "orders";

    public static void main(String[] args) throws IOException, InterruptedException {
        quietLogging();
        Schema schema = new Schema.Parser().parse(Files.readString(Path.of("schemas/order-event-v1.avsc")));

        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(producerConfig())) {
            send(producer, order(schema, "ORD-1001", 99.99));
            send(producer, order(schema, "ORD-1002", -5.00));
            // Rejected by the global rule of step 8, if it is still set
            send(producer, order(schema, "", 25.00));
        }

        try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(consumerConfig())) {
            consumer.subscribe(List.of(TOPIC));
            int received = 0;
            for (int attempt = 0; attempt < 10 && received == 0; attempt++) {
                for (ConsumerRecord<String, GenericRecord> record : consumer.poll(Duration.ofSeconds(1))) {
                    System.out.println("consumed  " + record.value());
                    received++;
                }
            }
            System.out.println(received + " message(s) on topic '" + TOPIC + "'");
        }
    }

    private static void send(KafkaProducer<String, GenericRecord> producer, GenericRecord order)
            throws InterruptedException {
        try {
            producer.send(new ProducerRecord<>(TOPIC, order.get("orderId").toString(), order)).get();
            System.out.println("sent      " + order);
        } catch (ExecutionException | RuntimeException e) {
            // The serializer fails before the record is handed to Kafka
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.out.println("rejected  " + order + "\n          " + cause.getMessage());
        }
    }

    private static GenericRecord order(Schema schema, String orderId, double totalAmount) {
        GenericRecord order = new GenericData.Record(schema);
        order.put("orderId", orderId);
        order.put("customerEmail", "customer@example.com");
        order.put("totalAmount", totalAmount);
        return order;
    }

    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        props.put("apicurio.registry.url", REGISTRY_URL);
        // Use the schema registered by demo.sh: orders/OrderEvent, version 1
        props.put("apicurio.registry.artifact.group-id", "orders");
        props.put("apicurio.registry.artifact.artifact-id", "OrderEvent");
        props.put("apicurio.registry.artifact.version", "1");
        // Run the artifact's contract rules (mode WRITE) before serializing; reject on failure
        props.put("apicurio.registry.serde.contract-rules.enabled", "true");
        props.put("apicurio.registry.serde.contract-rules.fail-on-error", "true");
        return props;
    }

    private static Properties consumerConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "contracts-demo-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
        props.put("apicurio.registry.url", REGISTRY_URL);
        // Run the artifact's READ-mode contract rules after deserializing
        props.put("apicurio.registry.serde.contract-rules.enabled", "true");
        return props;
    }

    /** Keeps the client library logs out of the demo output. */
    private static void quietLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.SEVERE);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(Level.SEVERE);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
