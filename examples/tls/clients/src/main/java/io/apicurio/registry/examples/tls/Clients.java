package io.apicurio.registry.examples.tls;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Steps 7 and 9: the Java SDK and the Kafka serdes over TLS and mutual TLS. Prints one ok/FAIL line
 * per check and exits 1 if one fails. Usage: {@code Clients <registry URL> <registry URL by IP>
 * <mTLS URL> <certificates directory> <bootstrap servers>}; the URLs are those of the v3 API.
 */
public final class Clients {

    private static final String PASSWORD = "apicurio";
    private static final String TOPIC = "tls-greetings";
    private static final Schema GREETING = new Schema.Parser().parse(
            "{\"type\": \"record\", \"name\": \"Greeting\", \"namespace\": \"com.example.tls\","
                    + " \"fields\": [{\"name\": \"message\", \"type\": \"string\"}]}");

    private static int failures;

    private Clients() {
    }

    public static void main(String[] args) {
        String registryUrl = args[0];
        String registryIpUrl = args[1];
        String mtlsUrl = args[2];
        // Normalized: the serdes refuse any path with a ".." element, even an absolute one
        String certs = Path.of(args[3]).toAbsolutePath().normalize().toString();
        String bootstrap = args[4];
        try {
            tls(registryUrl, registryIpUrl, certs);
            mtls(mtlsUrl, certs);
            serdes(mtlsUrl, certs, bootstrap);
        } catch (Exception | Error e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            System.out.println("  FAIL  the clients stopped: " + e + ", caused by: " + root);
            System.exit(1);
        } finally {
            DefaultVertxInstance.close();
        }
        System.out.println(failures > 0 ? failures + " check(s) failed." : "All client checks passed.");
        System.exit(failures > 0 ? 1 : 0);
    }

    /** registry: the server certificate is checked; the caller authenticates with HTTP basic. */
    private static void tls(String url, String ipUrl, String certs) {
        System.out.println("Java SDK, TLS (registry):");
        RegistryClient pem = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .trustStorePem(certs + "/ca-cert.pem")
                .basicAuth("developer", "developer"));
        check("trust store PEM + basic credentials: system info", "Apicurio Registry (SQL)",
                status(() -> pem.system().info().get().getName()));

        RegistryClient jks = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .trustStoreJks(certs + "/truststore.jks", PASSWORD)
                .basicAuth("developer", "developer"));
        check("trust store JKS + basic credentials: list groups", "ok",
                status(() -> jks.groups().get() != null ? "ok" : "none"));

        RegistryClient noTrust = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .basicAuth("developer", "developer"));
        check("no trust store (the JVM's CAs): the handshake fails", "error",
                status(() -> noTrust.groups().get() != null ? "ok" : "none"));

        RegistryClient rogue = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .trustStorePem(certs + "/rogue-ca-cert.pem")
                .basicAuth("developer", "developer"));
        check("trust store with another CA: the handshake fails", "error",
                status(() -> rogue.groups().get() != null ? "ok" : "none"));

        RegistryClient wrongPassword = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .trustStorePem(certs + "/ca-cert.pem")
                .basicAuth("developer", "wrong"));
        check("TLS fine, wrong password: 401", "401",
                status(() -> wrongPassword.groups().get() != null ? "ok" : "none"));

        RegistryClient byIp = RegistryClientFactory.create(RegistryClientOptions.create(ipUrl)
                .trustStorePem(certs + "/ca-cert.pem")
                .basicAuth("developer", "developer"));
        check("127.0.0.1 is not in the certificate: hostname verification fails", "error",
                status(() -> byIp.groups().get() != null ? "ok" : "none"));

        RegistryClient insecure = RegistryClientFactory.create(RegistryClientOptions.create(ipUrl)
                .trustAll(true).verifyHost(false)
                .basicAuth("developer", "developer"));
        check("trustAll + verifyHost(false) (development only): accepted", "ok",
                status(() -> insecure.groups().get() != null ? "ok" : "none"));
    }

    /** mtls-proxy: the client presents a certificate signed by the example CA. */
    private static void mtls(String url, String certs) throws Exception {
        System.out.println("Java SDK, mutual TLS (mtls-proxy):");
        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .keystorePkcs12(certs + "/client-keystore.p12", PASSWORD)
                .trustStorePkcs12(certs + "/truststore.p12", PASSWORD));
        String artifactId = "sdk-mtls-" + UUID.randomUUID();
        CreateArtifact create = new CreateArtifact();
        create.setArtifactId(artifactId);
        create.setArtifactType("AVRO");
        create.setFirstVersion(new CreateVersion());
        create.getFirstVersion().setContent(new VersionContent());
        create.getFirstVersion().getContent().setContent(GREETING.toString());
        create.getFirstVersion().getContent().setContentType("application/json");
        check("PKCS12 key store and trust store: create an artifact", "1",
                status(() -> client.groups().byGroupId("sdk-tls").artifacts()
                        .post(create, c -> c.queryParameters.ifExists = IfArtifactExists.FAIL)
                        .getVersion().getVersion()));
        String content;
        try (InputStream in = client.groups().byGroupId("sdk-tls").artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").content().get()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        check("... read it back", "Greeting", new Schema.Parser().parse(content).getName());
        client.groups().byGroupId("sdk-tls").artifacts().byArtifactId(artifactId).delete();
        check("... and delete it", "404", status(() -> client.groups().byGroupId("sdk-tls").artifacts()
                .byArtifactId(artifactId).get().getArtifactId()));

        RegistryClient pem = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .keystorePem(certs + "/client-cert.pem", certs + "/client-key.pem")
                .trustStorePem(certs + "/ca-cert.pem"));
        check("PEM client certificate and key: system info", "Apicurio Registry (SQL)",
                status(() -> pem.system().info().get().getName()));

        RegistryClient jks = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .keystoreJks(certs + "/client-keystore.jks", PASSWORD)
                .trustStoreJks(certs + "/truststore.jks", PASSWORD));
        check("JKS key store and trust store: system info", "Apicurio Registry (SQL)",
                status(() -> jks.system().info().get().getName()));

        RegistryClient noCert = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .trustStorePem(certs + "/ca-cert.pem"));
        check("no client certificate: the proxy answers 400", "400",
                status(() -> noCert.system().info().get().getName()));

        RegistryClient rogue = RegistryClientFactory.create(RegistryClientOptions.create(url)
                .keystorePkcs12(certs + "/rogue-client-keystore.p12", PASSWORD)
                .trustStorePem(certs + "/ca-cert.pem"));
        check("client certificate from an untrusted CA: the proxy answers 400", "400",
                status(() -> rogue.system().info().get().getName()));
    }

    /** The Kafka serdes call the registry through mtls-proxy, configured with apicurio.registry.tls.*. */
    private static void serdes(String url, String certs, String bootstrap) {
        System.out.println("Kafka serdes, mutual TLS (mtls-proxy):");
        // The serializer: PKCS12 stores
        Map<String, Object> producerConfig = new HashMap<>(Map.of(
                "apicurio.registry.url", url,
                "apicurio.registry.tls.truststore.location", certs + "/truststore.p12",
                "apicurio.registry.tls.truststore.password", PASSWORD,
                "apicurio.registry.tls.truststore.type", "PKCS12",
                "apicurio.registry.tls.keystore.location", certs + "/client-keystore.p12",
                "apicurio.registry.tls.keystore.password", PASSWORD,
                "apicurio.registry.tls.keystore.type", "PKCS12",
                "apicurio.registry.auto-register", "true"));
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
        producerConfig.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "20000");
        String message = "hello over mTLS " + UUID.randomUUID();
        GenericRecord greeting = new GenericData.Record(GREETING);
        greeting.put("message", message);
        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(producerConfig)) {
            producer.send(new ProducerRecord<>(TOPIC, message, greeting)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("The producer failed", e);
        }
        System.out.println("  ok    the serializer registered " + TOPIC + "-value (PKCS12 stores) and sent a record");

        // The deserializer: PEM files (paths; the PEM content itself works too)
        Map<String, Object> consumerConfig = new HashMap<>(Map.of(
                "apicurio.registry.url", url,
                "apicurio.registry.tls.certificates", certs + "/ca-cert.pem",
                "apicurio.registry.tls.client-certificate", certs + "/client-cert.pem",
                "apicurio.registry.tls.client-key", certs + "/client-key.pem"));
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "tls-" + UUID.randomUUID());
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.avro.AvroKafkaDeserializer");
        List<Object> values = new ArrayList<>();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(consumerConfig)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 30_000;
            while (values.isEmpty() && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, Object> consumed : consumer.poll(Duration.ofMillis(500))) {
                    if (message.equals(consumed.key())) {
                        values.add(consumed.value());
                    }
                }
            }
        }
        check("the deserializer fetched the schema (PEM files) and read the record", message,
                values.isEmpty() ? "nothing" : String.valueOf(((GenericRecord) values.get(0)).get("message")));
    }

    /**
     * Runs a call and reports its outcome: its result, the HTTP status of an error response (a
     * registry problem, the 401 of a wrong password, the 400 of the mTLS proxy), or "error" when there
     * is no HTTP response at all, such as a failed TLS handshake.
     */
    private static String status(Supplier<String> call) {
        try {
            return call.get();
        } catch (ProblemDetails e) {
            return String.valueOf(e.getStatus());
        } catch (ApiException e) {
            return String.valueOf(e.getResponseStatusCode());
        } catch (RuntimeException e) {
            return "error";
        }
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
