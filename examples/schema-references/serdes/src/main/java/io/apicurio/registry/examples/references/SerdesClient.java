package io.apicurio.registry.examples.references;

import io.apicurio.registry.examples.references.proto.Price;
import io.apicurio.registry.examples.references.proto.ShipmentCreated;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step 13: the serializers register nested types as separate artifacts, referenced by the parent,
 * and the deserializers resolve those references when they read. Prints one ok/FAIL line per check
 * and exits 1 if one fails. Usage: {@code SerdesClient <registry v3 URL> <bootstrap servers>}.
 */
public final class SerdesClient {

    /** One schema defining three named types: Invoice, and the nested Money and Customer records. */
    private static final Schema INVOICE = new Schema.Parser().parse("""
            {"type": "record", "name": "Invoice", "namespace": "com.example.billing",
             "fields": [
               {"name": "invoiceId", "type": "string"},
               {"name": "total", "type": {"type": "record", "name": "Money", "namespace": "com.example.common",
                 "fields": [{"name": "amount", "type": "double"}, {"name": "currency", "type": "string"}]}},
               {"name": "customer", "type": {"type": "record", "name": "Customer", "namespace": "com.example.billing",
                 "fields": [{"name": "name", "type": "string"}]}}]}""");

    private static String registryUrl;
    private static String bootstrap;
    private static int failures;

    private SerdesClient() {
    }

    public static void main(String[] args) {
        registryUrl = args.length > 0 ? args[0] : "http://localhost:8081/apis/registry/v3";
        bootstrap = args.length > 1 ? args[1] : "localhost:9092";
        try {
            avro();
            protobuf();
        } catch (Exception | Error e) {
            System.out.println("  FAIL  the client stopped: " + e);
            System.exit(1);
        }
        System.out.println(failures > 0 ? failures + " check(s) failed." : "All serdes checks passed.");
        System.exit(failures > 0 ? 1 : 0);
    }

    private static void avro() {
        GenericRecord money = new GenericData.Record(INVOICE.getField("total").schema());
        money.put("amount", 99.5);
        money.put("currency", "EUR");
        GenericRecord customer = new GenericData.Record(INVOICE.getField("customer").schema());
        customer.put("name", "Ada");
        GenericRecord invoice = new GenericData.Record(INVOICE);
        invoice.put("invoiceId", "i-1");
        invoice.put("total", money);
        invoice.put("customer", customer);

        produce("invoices", "io.apicurio.registry.serde.avro.AvroKafkaSerializer", invoice);
        List<Object> received = consume("invoices", "io.apicurio.registry.serde.avro.AvroKafkaDeserializer", Map.of());
        GenericRecord read = received.isEmpty() ? null : (GenericRecord) received.get(0);
        check("Avro: the invoice is read back, nested records included", "i-1/99.5/Ada",
                read == null ? "nothing" : read.get("invoiceId") + "/" + ((GenericRecord) read.get("total")).get("amount")
                        + "/" + ((GenericRecord) read.get("customer")).get("name"));
    }

    private static void protobuf() {
        ShipmentCreated shipment = ShipmentCreated.newBuilder().setShipmentId("s-1")
                .setCost(Price.newBuilder().setAmount(7.5).setCurrency("EUR")).build();
        produce("shipments", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer", shipment);
        List<Object> received = consume("shipments", "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer",
                Map.of("apicurio.protobuf.derive.class", "true"));
        check("Protobuf: the shipment is read back, imported message included", shipment,
                received.isEmpty() ? "nothing" : received.get(0));
    }

    private static <V> void produce(String topic, String serializer, V value) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "20000");
        config.put("apicurio.registry.url", registryUrl);
        config.put("apicurio.registry.auto-register", "true");
        try (KafkaProducer<String, V> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), value)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Sending to " + topic + " failed", e);
        }
    }

    private static List<Object> consume(String topic, String deserializer, Map<String, Object> serdeConfig) {
        Map<String, Object> config = new HashMap<>(serdeConfig);
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "references-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        config.put("apicurio.registry.url", registryUrl);
        List<Object> values = new ArrayList<>();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (values.isEmpty() && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, Object> consumed : consumer.poll(Duration.ofMillis(500))) {
                    values.add(consumed.value());
                }
            }
        }
        return values;
    }

    private static void check(String description, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.printf("  ok    %s%n", description);
        } else {
            System.out.printf("  FAIL  %s: got '%s', expected '%s'%n", description, actual, expected);
            failures++;
        }
    }
}
