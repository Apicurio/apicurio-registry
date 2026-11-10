package io.apicurio.registry.examples.serdes;

import io.apicurio.registry.examples.serdes.proto.Payment;
import io.apicurio.registry.examples.serdes.proto.Refund;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A tour of the Apicurio Registry Kafka serdes. Each step produces and consumes records on its own
 * topic and checks the result, printing one ok/FAIL line per check. GUIDE.md explains each step.
 *
 * <p>Usage: {@code Tour <registry v3 URL> <bootstrap servers> [all | step,step,...]}. Exits 1 if a
 * check fails. Every property is spelled out as a string, so it can be copied into any client.
 */
public final class Tour {

    private static final String AVRO_SERIALIZER = "io.apicurio.registry.serde.avro.AvroKafkaSerializer";
    private static final String AVRO_DESERIALIZER = "io.apicurio.registry.serde.avro.AvroKafkaDeserializer";
    private static final String JSON_SERIALIZER = "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer";
    private static final String JSON_DESERIALIZER = "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer";
    private static final String PROTOBUF_SERIALIZER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer";
    private static final String PROTOBUF_DESERIALIZER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer";

    private static final Schema ORDER_V1 = avro("""
            {"type": "record", "name": "OrderPlaced", "namespace": "com.example.orders",
             "fields": [{"name": "orderId", "type": "string"}, {"name": "amount", "type": "double"}]}""");
    private static final Schema ORDER_V2 = avro("""
            {"type": "record", "name": "OrderPlaced", "namespace": "com.example.orders",
             "fields": [{"name": "orderId", "type": "string"}, {"name": "amount", "type": "double"},
                        {"name": "currency", "type": "string", "default": "EUR"}]}""");
    private static final Schema ORDER_AMOUNT_AS_TEXT = avro("""
            {"type": "record", "name": "OrderPlaced", "namespace": "com.example.orders",
             "fields": [{"name": "orderId", "type": "string"}, {"name": "amount", "type": "string"},
                        {"name": "currency", "type": "string", "default": "EUR"}]}""");
    private static final Schema ORDER_CANCELLED = avro("""
            {"type": "record", "name": "OrderCancelled", "namespace": "com.example.orders",
             "fields": [{"name": "orderId", "type": "string"}, {"name": "reason", "type": "string"}]}""");
    private static final Schema STOCK_LEVEL = avro("""
            {"type": "record", "name": "StockLevel", "namespace": "com.example.inventory",
             "fields": [{"name": "sku", "type": "string"}, {"name": "quantity", "type": "int"}]}""");
    private static final Schema GREETING = avro("""
            {"type": "record", "name": "Greeting", "namespace": "com.example.greetings",
             "fields": [{"name": "message", "type": "string"}, {"name": "time", "type": "long"}]}""");

    private static final List<String> STEPS = List.of("avro", "headers", "json", "protobuf",
            "protobuf-find-latest", "reflect", "record-strategy", "evolution", "rules", "custom-strategy",
            "custom-resolver", "preregistered");

    private static String registryUrl;
    private static String bootstrap;
    private static int failures;

    private Tour() {
    }

    public static void main(String[] args) {
        // Always exit explicitly: the registry client's threads would otherwise keep the JVM alive
        // after an unexpected error, and demo.sh would wait forever
        try {
            run(args);
        } catch (Exception | Error t) {
            System.out.println("  FAIL  the tour stopped: " + describe(t));
            System.exit(1);
        }
        System.exit(failures > 0 ? 1 : 0);
    }

    private static void run(String[] args) throws Exception {
        registryUrl = args.length > 0 ? args[0] : "http://localhost:8081/apis/registry/v3";
        bootstrap = args.length > 1 ? args[1] : "localhost:9092";
        String selection = args.length > 2 ? args[2] : "all";
        List<String> steps = "all".equals(selection) ? STEPS : List.of(selection.split(","));

        for (String step : steps) {
            System.out.println("Tour step: " + step);
            switch (step) {
                case "avro" -> avro();
                case "headers" -> headers();
                case "json" -> json();
                case "protobuf" -> protobuf();
                case "protobuf-find-latest" -> protobufFindLatest();
                case "reflect" -> reflect();
                case "record-strategy" -> recordStrategy();
                case "evolution" -> evolution();
                case "rules" -> rules();
                case "custom-strategy" -> customStrategy();
                case "custom-resolver" -> customResolver();
                case "preregistered" -> preregistered();
                default -> throw new IllegalArgumentException("Unknown step " + step + "; steps: " + STEPS);
            }
        }
        System.out.println(failures > 0 ? failures + " tour check(s) failed." : "All tour checks passed.");
    }

    // ------------------------------------------------------------------------------------------ steps

    /** Avro with auto-register: the serializer registers the schema as default/orders-value. */
    private static void avro() throws Exception {
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "true");
        List<GenericRecord> sent = List.of(order(ORDER_V1, "o-1", 10.0), order(ORDER_V1, "o-2", 20.0),
                order(ORDER_V1, "o-3", 30.0));
        produce("orders", AVRO_SERIALIZER, config, sent);
        List<GenericRecord> received = consume("orders", AVRO_DESERIALIZER, Map.of(), 3, null);
        check("3 Avro orders consumed, in order", "o-1,o-2,o-3", ids(received, "orderId"));
        check("the consumer gets GenericRecord", true, received.get(0) instanceof GenericData.Record);
    }

    /** Headers mode: the schema ID travels in Kafka headers instead of the payload. */
    private static void headers() throws Exception {
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "true",
                "apicurio.registry.headers.enabled", "true");
        produce("orders-headers", AVRO_SERIALIZER, config, List.of(order(ORDER_V1, "o-10", 5.0)));
        List<Set<String>> headerNames = new ArrayList<>();
        // The consumer must enable headers too: it only reads them when told to
        List<GenericRecord> received = consume("orders-headers", AVRO_DESERIALIZER,
                Map.of("apicurio.registry.headers.enabled", "true"), 1, r -> headerNames.add(headerNames(r)));
        check("the record is read back", "o-10", ids(received, "orderId"));
        check("the content ID is in the headers", true,
                headerNames.get(0).contains("apicurio.value.contentId"));
    }

    /** JSON Schema: the schema comes from a file (it cannot be derived from a Java object). */
    private static void json() throws Exception {
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "true",
                "apicurio.registry.artifact.schema.location", "shipment.json");
        Shipment valid = new Shipment("s-1", "o-1", 2);
        produce("shipments", JSON_SERIALIZER, config, List.of(valid));
        Throwable rejected = produceExpectingFailure("shipments", JSON_SERIALIZER, config,
                new Shipment("s-2", "o-2", 0));
        check("a shipment with 0 parcels is rejected by the serializer", true, rejected != null);
        info("validation failure", describe(rejected));
        List<Shipment> received = consume("shipments", JSON_DESERIALIZER,
                Map.of("apicurio.registry.deserializer.value.return-class", Shipment.class.getName()), 1, null);
        check("only the valid shipment is on the topic, as a Shipment", List.of(valid), received);
    }

    /** Protobuf with auto-register; read back as DynamicMessage, then as the generated class. */
    private static void protobuf() throws Exception {
        Payment payment = Payment.newBuilder().setPaymentId("p-1").setOrderId("o-1").setAmount(10.0).build();
        produce("payments", PROTOBUF_SERIALIZER, Map.of("apicurio.registry.auto-register", "true"),
                List.of(payment));
        List<Object> dynamic = consume("payments", PROTOBUF_DESERIALIZER, Map.of(), 1, null);
        check("by default the consumer gets a DynamicMessage", "DynamicMessage",
                dynamic.get(0).getClass().getSimpleName());
        List<Object> typed = consume("payments", PROTOBUF_DESERIALIZER,
                Map.of("apicurio.protobuf.derive.class", "true"), 1, null);
        check("with derive.class it gets the generated Payment", payment, typed.get(0));
    }

    /** Protobuf with a schema registered ahead of time: the serializer only looks it up. */
    private static void protobufFindLatest() throws Exception {
        register("default", "refunds-value", "PROTOBUF", resource("refund.proto"), "application/x-protobuf");
        Refund refund = Refund.newBuilder().setRefundId("r-1").setPaymentId("p-1").build();
        produce("refunds", PROTOBUF_SERIALIZER, Map.of("apicurio.registry.find-latest", "true"), List.of(refund));
        List<Object> received = consume("refunds", PROTOBUF_DESERIALIZER,
                Map.of("apicurio.registry.deserializer.value.return-class", Refund.class.getName()), 1, null);
        check("the refund is sent with the registered schema and read back", refund, received.get(0));
    }

    /** Plain Java classes: the reflect datum provider derives the Avro schema from the fields. */
    private static void reflect() throws Exception {
        Map<String, Object> reflect = Map.of("apicurio.registry.avro-datum-provider",
                "io.apicurio.registry.serde.avro.ReflectAvroDatumProvider");
        Map<String, Object> config = new HashMap<>(reflect);
        config.put("apicurio.registry.auto-register", "true");
        Customer customer = new Customer("c-1", "Ada", 120);
        produce("customers", AVRO_SERIALIZER, config, List.of(customer));
        List<Customer> received = consume("customers", AVRO_DESERIALIZER, reflect, 1, null);
        check("the Customer object is read back", customer, received.get(0));
    }

    /** Two record types on one topic: RecordIdStrategy names each artifact after its record. */
    private static void recordStrategy() throws Exception {
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "true",
                "apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.avro.strategy.RecordIdStrategy");
        GenericRecord cancelled = new GenericData.Record(ORDER_CANCELLED);
        cancelled.put("orderId", "o-2");
        cancelled.put("reason", "changed my mind");
        produce("order-events", AVRO_SERIALIZER, config, List.of(order(ORDER_V1, "o-1", 10.0), cancelled));
        List<GenericRecord> received = consume("order-events", AVRO_DESERIALIZER, Map.of(), 2, null);
        check("both record types are read back from one topic", "OrderPlaced,OrderCancelled",
                received.stream().map(r -> r.getSchema().getName()).collect(Collectors.joining(",")));
    }

    /** A new schema version with a defaulted field; old and new records stay readable. */
    private static void evolution() throws Exception {
        GenericRecord withCurrency = order(ORDER_V2, "o-4", 40.0);
        withCurrency.put("currency", "USD");
        produce("orders", AVRO_SERIALIZER, Map.of("apicurio.registry.auto-register", "true"),
                List.of(withCurrency));
        List<GenericRecord> received = consume("orders", AVRO_DESERIALIZER, Map.of(), 4, null);
        check("old and new orders are read from the same topic", "o-1,o-2,o-3,o-4", ids(received, "orderId"));
        check("each record keeps the schema it was written with", "false,false,false,true",
                received.stream().map(r -> String.valueOf(r.getSchema().getField("currency") != null))
                        .collect(Collectors.joining(",")));
    }

    /** A compatibility rule rejects an incompatible schema when the serializer registers it. */
    private static void rules() throws Exception {
        http("POST", "/groups/default/artifacts/orders-value/rules",
                "{\"ruleType\": \"COMPATIBILITY\", \"config\": \"BACKWARD\"}", 204, 409);
        Throwable rejected = produceExpectingFailure("orders", AVRO_SERIALIZER,
                Map.of("apicurio.registry.auto-register", "true"), order(ORDER_AMOUNT_AS_TEXT, "o-5", "50"));
        check("the incompatible schema is rejected; send() throws", true, rejected != null);
        info("producer exception", describe(rejected));
        // The registry's answer is the cause: a RuleViolationProblemDetails from the Java SDK
        RuleViolationProblemDetails violation = null;
        for (Throwable c = rejected; c != null && violation == null; c = c.getCause()) {
            if (c instanceof RuleViolationProblemDetails details) {
                violation = details;
            }
        }
        check("the cause is the registry's rule violation", true, violation != null);
        if (violation != null) {
            info("rule violation", violation.getTitle());
            check("the violated rule is COMPATIBILITY", "COMPATIBILITY",
                    String.valueOf(violation.getRuleViolation().getRuleType()));
        }
    }

    /** A custom ArtifactReferenceResolverStrategy: the topic's owner team becomes the group. */
    private static void customStrategy() throws Exception {
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "true",
                "apicurio.registry.artifact-resolver-strategy", TeamArtifactStrategy.class.getName());
        GenericRecord stock = new GenericData.Record(STOCK_LEVEL);
        stock.put("sku", "sku-1");
        stock.put("quantity", 7);
        produce("inventory.stock-levels", AVRO_SERIALIZER, config, List.of(stock));
        List<GenericRecord> received = consume("inventory.stock-levels", AVRO_DESERIALIZER, Map.of(), 1, null);
        check("the record is read back", "sku-1", ids(received, "sku"));
    }

    /** A custom SchemaResolver, plugged in with apicurio.registry.schema-resolver. */
    private static void customResolver() throws Exception {
        CountingSchemaResolver.LOOKUPS.set(0);
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "true",
                "apicurio.registry.schema-resolver", CountingSchemaResolver.class.getName());
        produce("orders-audited", AVRO_SERIALIZER, config,
                List.of(order(ORDER_V1, "o-20", 1.0), order(ORDER_V1, "o-21", 2.0), order(ORDER_V1, "o-22", 3.0)));
        check("the custom resolver resolved the schema of each of the 3 records", 3,
                CountingSchemaResolver.LOOKUPS.get());
        check("the records are read back", "o-20,o-21,o-22",
                ids(consume("orders-audited", AVRO_DESERIALIZER, Map.of(), 3, null), "orderId"));
    }

    /** A schema registered ahead of time (by the Maven plugin), and auto-register switched off. */
    private static void preregistered() throws Exception {
        Map<String, Object> config = Map.of("apicurio.registry.auto-register", "false",
                "apicurio.registry.artifact.group-id", "greetings",
                "apicurio.registry.artifact.artifact-id", "greeting",
                "apicurio.registry.find-latest", "true");
        GenericRecord greeting = new GenericData.Record(GREETING);
        greeting.put("message", "hello");
        greeting.put("time", 1L);
        produce("greetings", AVRO_SERIALIZER, config, List.of(greeting));
        check("the greeting is sent with the pre-registered schema and read back", "hello",
                ids(consume("greetings", AVRO_DESERIALIZER, Map.of(), 1, null), "message"));
    }

    // ---------------------------------------------------------------------------------------- Kafka

    private static <V> void produce(String topic, String serializer, Map<String, Object> serdeConfig,
            List<V> values) throws Exception {
        try (KafkaProducer<String, V> producer = new KafkaProducer<>(producerConfig(serializer, serdeConfig))) {
            for (V value : values) {
                producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), value)).get();
            }
        }
    }

    /** Sends one value that should fail, and returns what was thrown (null if it was sent). */
    private static <V> Throwable produceExpectingFailure(String topic, String serializer,
            Map<String, Object> serdeConfig, V value) {
        try (KafkaProducer<String, V> producer = new KafkaProducer<>(producerConfig(serializer, serdeConfig))) {
            producer.send(new ProducerRecord<>(topic, "rejected", value)).get();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (Exception e) {
            return e;
        }
    }

    private static Map<String, Object> producerConfig(String serializer, Map<String, Object> serdeConfig) {
        Map<String, Object> config = new HashMap<>(serdeConfig);
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "20000");
        config.put("apicurio.registry.url", registryUrl);
        return config;
    }

    /** Reads the topic from the start, with a new consumer group, until it has {@code expected} records. */
    @SuppressWarnings("unchecked")
    private static <V> List<V> consume(String topic, String deserializer, Map<String, Object> serdeConfig,
            int expected, Consumer<ConsumerRecord<String, Object>> onRecord) {
        Map<String, Object> config = new HashMap<>(serdeConfig);
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "tour-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        config.put("apicurio.registry.url", registryUrl);
        List<V> values = new ArrayList<>();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (values.size() < expected && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, Object> consumed : consumer.poll(Duration.ofMillis(500))) {
                    values.add((V) consumed.value());
                    if (onRecord != null) {
                        onRecord.accept(consumed);
                    }
                }
            }
        }
        return values;
    }

    private static Set<String> headerNames(ConsumerRecord<String, Object> consumed) {
        Set<String> names = new TreeSet<>();
        for (Header header : consumed.headers()) {
            names.add(header.key());
        }
        return names;
    }

    // ------------------------------------------------------------------------------------- registry

    private static void register(String groupId, String artifactId, String type, String content,
            String contentType) throws IOException, InterruptedException {
        String body = "{\"artifactId\": " + quote(artifactId) + ", \"artifactType\": " + quote(type)
                + ", \"firstVersion\": {\"content\": {\"content\": " + quote(content) + ", \"contentType\": "
                + quote(contentType) + "}}}";
        http("POST", "/groups/" + groupId + "/artifacts?ifExists=FIND_OR_CREATE_VERSION", body, 200);
    }

    private static void http(String method, String resource, String body, int... accepted)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(registryUrl + resource))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        for (int status : accepted) {
            if (response.statusCode() == status) {
                return;
            }
        }
        throw new IllegalStateException(method + " " + resource + " returned " + response.statusCode() + ": "
                + response.body());
    }

    // -------------------------------------------------------------------------------------- helpers

    private static Schema avro(String json) {
        return new Schema.Parser().parse(json);
    }

    private static GenericRecord order(Schema schema, String orderId, Object amount) {
        GenericRecord order = new GenericData.Record(schema);
        order.put("orderId", orderId);
        order.put("amount", amount);
        return order;
    }

    private static String ids(List<GenericRecord> records, String field) {
        return records.stream().map(r -> String.valueOf(r.get(field))).collect(Collectors.joining(","));
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = Tour.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The exception chain, as "Class: message <- Class: message", for the guide. */
    private static String describe(Throwable t) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder();
        for (Throwable c = t; c != null && seen.putIfAbsent(c.toString(), true) == null; c = c.getCause()) {
            if (!out.isEmpty()) {
                out.append(" <- ");
            }
            String message = c.getMessage() == null ? "" : c.getMessage().lines().findFirst().orElse("");
            out.append(c.getClass().getName()).append(message.isEmpty() ? "" : ": " + abbreviate(message));
        }
        return out.toString();
    }

    private static String abbreviate(String s) {
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }

    private static void check(String description, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.printf("  ok    %s%n", description);
        } else {
            System.out.printf("  FAIL  %s: got '%s', expected '%s'%n", description, actual, expected);
            failures++;
        }
    }

    private static void info(String label, String value) {
        System.out.printf("  info  %s: %s%n", label, value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
