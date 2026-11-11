package io.apicurio.registry.examples.authentication;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.ProblemDetails;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Steps 11 and 12: Java clients authenticating to the registry. Prints one ok/FAIL line per check and
 * exits 1 if one fails. Usage: {@code Clients <registry URL> <basic registry URL> <token endpoint>
 * <bootstrap servers>}; the URLs are those of the v3 API.
 */
public final class Clients {

    private static final Schema GREETING = new Schema.Parser().parse(
            "{\"type\": \"record\", \"name\": \"Greeting\", \"namespace\": \"com.example\","
                    + " \"fields\": [{\"name\": \"message\", \"type\": \"string\"}]}");

    private static int failures;

    private Clients() {
    }

    public static void main(String[] args) {
        String registryUrl = args[0];
        String basicUrl = args[1];
        String tokenEndpoint = args[2];
        String bootstrap = args[3];
        try {
            sdk(registryUrl, basicUrl, tokenEndpoint);
            serdes(registryUrl, tokenEndpoint, bootstrap);
        } catch (Exception | Error e) {
            System.out.println("  FAIL  the clients stopped: " + e);
            System.exit(1);
        }
        System.out.println(failures > 0 ? failures + " check(s) failed." : "All client checks passed.");
        System.exit(failures > 0 ? 1 : 0);
    }

    /** The Java SDK: OAuth2 client credentials, a bearer token, and HTTP basic. */
    private static void sdk(String registryUrl, String basicUrl, String tokenEndpoint) throws Exception {
        System.out.println("Java SDK:");
        RegistryClient developer = RegistryClientFactory.create(RegistryClientOptions.create(registryUrl)
                .oauth2(tokenEndpoint, "developer-client", "test1"));
        check("OAuth2 client credentials (developer-client): create a group", "created",
                status(() -> createGroup(developer, "sdk-oauth2")));

        RegistryClient readonly = RegistryClientFactory.create(RegistryClientOptions.create(registryUrl)
                .oauth2(tokenEndpoint, "readonly-client", "test1"));
        check("OAuth2 client credentials (readonly-client): read groups", "ok",
                status(() -> readonly.groups().get() != null ? "ok" : "none"));
        check("OAuth2 client credentials (readonly-client): create a group is forbidden", "403",
                status(() -> createGroup(readonly, "sdk-readonly")));

        RegistryClient wrongSecret = RegistryClientFactory.create(RegistryClientOptions.create(registryUrl)
                .oauth2(tokenEndpoint, "developer-client", "wrong"));
        check("OAuth2 client credentials with a wrong secret: no token, the call fails", "error",
                status(() -> wrongSecret.groups().get() != null ? "ok" : "none"));

        String token = fetchToken(tokenEndpoint, "admin-client", "test1");
        // bearerToken() needs the JDK HTTP adapter; the default (AUTO) picks Vert.x when it is present
        RegistryClient bearer = RegistryClientFactory.create(RegistryClientOptions.create(registryUrl)
                .httpAdapter(HttpAdapterType.JDK).bearerToken(token));
        check("a bearer token obtained elsewhere (admin-client): admin API", "ok",
                status(() -> bearer.admin().config().properties().get() != null ? "ok" : "none"));

        RegistryClient basic = RegistryClientFactory.create(RegistryClientOptions.create(basicUrl)
                .basicAuth("developer", "developer"));
        check("HTTP basic against registry-basic (developer): create a group", "created",
                status(() -> createGroup(basic, "sdk-basic")));
    }

    /** The Kafka serdes: the serializer and deserializer use OAuth2 client credentials. */
    private static void serdes(String registryUrl, String tokenEndpoint, String bootstrap) {
        System.out.println("Kafka serdes:");
        Map<String, Object> auth = Map.of(
                "apicurio.registry.url", registryUrl,
                "apicurio.registry.auth.service.token.endpoint", tokenEndpoint,
                "apicurio.registry.auth.client.id", "developer-client",
                "apicurio.registry.auth.client.secret", "test1");

        Map<String, Object> producerConfig = new HashMap<>(auth);
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
        producerConfig.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "20000");
        producerConfig.put("apicurio.registry.auto-register", "true");
        GenericRecord greeting = new GenericData.Record(GREETING);
        greeting.put("message", "hello");
        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(producerConfig)) {
            producer.send(new ProducerRecord<>("greetings", UUID.randomUUID().toString(), greeting)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("The authenticated producer failed", e);
        }
        System.out.println("  ok    the producer registered greetings-value with client credentials and sent a record");

        Map<String, Object> consumerConfig = new HashMap<>(auth);
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "authn-" + UUID.randomUUID());
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.avro.AvroKafkaDeserializer");
        List<Object> values = new ArrayList<>();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(consumerConfig)) {
            consumer.subscribe(List.of("greetings"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (values.isEmpty() && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, Object> consumed : consumer.poll(Duration.ofMillis(500))) {
                    values.add(consumed.value());
                }
            }
        }
        check("the consumer fetched the schema with client credentials and read the record", "hello",
                values.isEmpty() ? "nothing" : String.valueOf(((GenericRecord) values.get(0)).get("message")));
    }

    private static String createGroup(RegistryClient client, String groupId) {
        CreateGroup group = new CreateGroup();
        group.setGroupId(groupId);
        try {
            client.groups().post(group);
        } catch (ProblemDetails e) {
            if (e.getStatus() != null && e.getStatus() == 409) {
                return "created";
            }
            throw e;
        }
        return "created";
    }

    /** Runs a call and reports its outcome: its result, the HTTP status of a problem, or "error". */
    private static String status(Supplier<String> call) {
        try {
            return call.get();
        } catch (ProblemDetails e) {
            return String.valueOf(e.getStatus());
        } catch (RuntimeException e) {
            return "error";
        }
    }

    private static String fetchToken(String tokenEndpoint, String clientId, String secret) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=" + clientId
                        + "&client_secret=" + secret, StandardCharsets.UTF_8))
                .build();
        String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        int start = body.indexOf("\"access_token\":\"") + 16;
        return body.substring(start, body.indexOf('"', start));
    }

    private static void check(String description, String expected, String actual) {
        if (expected.equals(actual)) {
            System.out.printf("  ok    %s%n", description);
        } else {
            System.out.printf("  FAIL  %s: got '%s', expected '%s'%n", description, actual, expected);
            failures++;
        }
    }
}
