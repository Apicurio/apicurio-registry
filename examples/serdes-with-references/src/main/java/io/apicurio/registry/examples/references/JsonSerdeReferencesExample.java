package io.apicurio.registry.examples.references;

import io.apicurio.registry.examples.references.model.Citizen;
import io.apicurio.registry.examples.references.model.City;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;

public class JsonSerdeReferencesExample {
    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = JsonSerdeReferencesExample.class.getSimpleName();
    private static final String SUBJECT_NAME = "Greeting";

    private static final Vertx vertx = Vertx.vertx();

    public static void main(String[] args) throws Exception {
        System.out.println("Starting example " + JsonSerdeReferencesExample.class.getSimpleName());
        String topicName = TOPIC_NAME;
        String subjectName = SUBJECT_NAME;

        RegistryClient client = createRegistryClient(REGISTRY_URL);

        InputStream citySchema = JsonSerdeReferencesExample.class.getClassLoader()
                .getResourceAsStream("city.json");

        InputStream citizenSchema = JsonSerdeReferencesExample.class.getClassLoader()
                .getResourceAsStream("citizen.json");

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId("city");
        createArtifact.setArtifactType(ArtifactType.JSON);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(IoUtil.toString(citySchema));
        createArtifact.getFirstVersion().getContent().setContentType("application/json");

        final io.apicurio.registry.rest.client.models.VersionMetaData amdCity = client.groups()
                .byGroupId("default").artifacts().post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                }).getVersion();

        final ArtifactReference reference = new ArtifactReference();
        reference.setVersion(amdCity.getVersion());
        reference.setGroupId(amdCity.getGroupId());
        reference.setArtifactId(amdCity.getArtifactId());
        reference.setName("city.json");

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = TOPIC_NAME;
        // use the topic name as the artifactId because we're going to map topic name to artifactId later on.

        CreateArtifact citizenCreateArtifact = new CreateArtifact();
        citizenCreateArtifact.setArtifactId(artifactId);
        citizenCreateArtifact.setArtifactType(ArtifactType.JSON);
        citizenCreateArtifact.setFirstVersion(new CreateVersion());
        citizenCreateArtifact.getFirstVersion().setContent(new VersionContent());
        citizenCreateArtifact.getFirstVersion().getContent().setContent(IoUtil.toString(citizenSchema));
        citizenCreateArtifact.getFirstVersion().getContent().setContentType("application/json");
        citizenCreateArtifact.getFirstVersion().getContent()
                .setReferences(Collections.singletonList(reference));

        client.groups().byGroupId("default").artifacts().post(citizenCreateArtifact, config -> {
            config.queryParameters.ifExists = io.apicurio.registry.rest.client.models.IfArtifactExists.FIND_OR_CREATE_VERSION;
        });

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();
        // Produce 5 messages.
        int producedMessages = 0;
        try {
            System.out.println("Producing (5) messages.");
            for (int idx = 0; idx < 5; idx++) {
                // Create the message to send
                Citizen citizen = new Citizen();
                City city = new City();
                city.setZipCode(45676);
                city.setName(UUID.randomUUID().toString());
                citizen.setCity(city);
                citizen.setAge(producedMessages + 20);
                citizen.setFirstName(UUID.randomUUID().toString());
                citizen.setLastName(UUID.randomUUID().toString());

                // Send/produce the message on the Kafka Producer
                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, subjectName,
                        citizen);
                producer.send(producedRecord);

                Thread.sleep(100);
            }
            System.out.println("Messages successfully produced.");
        } finally {
            System.out.println("Closing the producer.");
            producer.flush();
            producer.close();
        }

        // Create the consumer
        System.out.println("Creating the consumer.");
        KafkaConsumer<Long, Citizen> consumer = createKafkaConsumer();

        // Subscribe to the topic
        System.out.println("Subscribing to topic " + topicName);
        consumer.subscribe(Collections.singletonList(topicName));

        // Consume the 5 messages.
        try {
            int messageCount = 0;
            System.out.println("Consuming (5) messages.");
            while (messageCount < 5) {
                final ConsumerRecords<Long, Citizen> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else
                    records.forEach(record -> {
                        Citizen msg = record.value();
                        System.out.println("Consumed a message: " + msg + " @ " + msg.getCity());
                    });
            }
        } finally {
            consumer.close();
        }

        vertx.close();
        System.out.println("Done (success).");
    }

    /**
     * Creates the Kafka producer.
     */
    private static Producer<Object, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + TOPIC_NAME);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Use the Apicurio Registry provided Kafka Serializer for Avro
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSchemaKafkaSerializer.class.getName());
        props.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
        props.putIfAbsent(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);

        // Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka producer
        Producer<Object, Object> producer = new KafkaProducer<>(props);
        return producer;
    }

    /**
     * Creates the Kafka consumer.
     */
    private static KafkaConsumer<Long, Citizen> createKafkaConsumer() {
        Properties props = new Properties();

        // Configure Kafka
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + TOPIC_NAME);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use the Apicurio Registry provided Kafka Deserializer for Avro
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonSchemaKafkaDeserializer.class.getName());
        props.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
        props.putIfAbsent(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");
        props.putIfAbsent(SerdeConfig.VALIDATION_ENABLED, true);

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload. So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        // Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka Consumer
        KafkaConsumer<Long, Citizen> consumer = new KafkaConsumer<>(props);
        return consumer;
    }

    public static void configureSecurityIfPresent(Properties props) {
        final String tokenEndpoint = System.getenv(SerdeConfig.AUTH_TOKEN_ENDPOINT);
        if (tokenEndpoint != null) {

            final String authClient = System.getenv(SerdeConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SerdeConfig.AUTH_CLIENT_SECRET);

            props.putIfAbsent(SerdeConfig.AUTH_CLIENT_SECRET, authSecret);
            props.putIfAbsent(SerdeConfig.AUTH_CLIENT_ID, authClient);
            props.putIfAbsent(SerdeConfig.AUTH_TOKEN_ENDPOINT, tokenEndpoint);
            props.putIfAbsent(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                    "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
            props.putIfAbsent("security.protocol", "SASL_SSL");

            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG,
                    String.format(
                            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required "
                                    + "  oauth.client.id=\"%s\" " + "  oauth.client.secret=\"%s\" "
                                    + "  oauth.token.endpoint.uri=\"%s\" ;",
                            authClient, authSecret, tokenEndpoint));
        }
    }

    /**
     * Creates the registry client
     */
    private static RegistryClient createRegistryClient(String registryUrl) {
        final String tokenEndpoint = System.getenv(SerdeConfig.AUTH_TOKEN_ENDPOINT);

        // Just if security values are present, then we configure them.
        if (tokenEndpoint != null) {
            final String authClient = System.getenv(SerdeConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SerdeConfig.AUTH_CLIENT_SECRET);
            var adapter = new VertXRequestAdapter(
                    buildOIDCWebClient(vertx, tokenEndpoint, authClient, authSecret));
            adapter.setBaseUrl(registryUrl);
            return new RegistryClient(adapter);
        } else {
            VertXRequestAdapter vertXRequestAdapter = new VertXRequestAdapter(vertx);
            vertXRequestAdapter.setBaseUrl(REGISTRY_URL);
            return new RegistryClient(vertXRequestAdapter);
        }
    }
}