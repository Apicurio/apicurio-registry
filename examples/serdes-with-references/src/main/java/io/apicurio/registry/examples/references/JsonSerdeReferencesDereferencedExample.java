package io.apicurio.registry.examples.references;

import io.apicurio.registry.examples.references.model.Citizen;
import io.apicurio.registry.examples.references.model.CitizenIdentifier;
import io.apicurio.registry.examples.references.model.City;
import io.apicurio.registry.examples.references.model.CityQualification;
import io.apicurio.registry.examples.references.model.IdentifierQualification;
import io.apicurio.registry.examples.references.model.Qualification;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
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
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class JsonSerdeReferencesDereferencedExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = JsonSerdeReferencesExample.class.getSimpleName();
    private static final String SUBJECT_NAME = "Defeference";

    public static void main(String[] args) throws Exception {

        System.out.println("Starting example " + JsonSerdeReferencesExample.class.getSimpleName());
        String topicName = TOPIC_NAME;

        RegistryClient client = createRegistryClient(REGISTRY_URL);

        InputStream citizenSchema = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/citizen.json");
        InputStream citySchema = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/types/city/city.json");
        InputStream citizenIdentifier = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/types/identifier/citizenIdentifier.json");
        InputStream qualificationSchema = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/qualification.json");
        InputStream addressSchema = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/sample.address.json");
        InputStream identifierQualificationSchema = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/types/identifier/qualification.json");
        InputStream cityQualificationSchema = JsonSerdeReferencesDereferencedExample.class.getClassLoader().getResourceAsStream("serde/json/types/city/qualification.json");

        //Creates the city qualification schema.
        final ArtifactMetaData amdCityQualification = client.createArtifact("default", "cityQualification", ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, cityQualificationSchema);

        final ArtifactReference cityQualificationReference = new ArtifactReference();
        cityQualificationReference.setVersion("1");
        cityQualificationReference.setGroupId(amdCityQualification.getGroupId());
        cityQualificationReference.setArtifactId(amdCityQualification.getId());
        cityQualificationReference.setName("qualification.json");

        //Creates the identifier qualification schema.
        final ArtifactMetaData amdIdentifierQualification = client.createArtifact("default", "identifierQualification", ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, identifierQualificationSchema);

        final ArtifactReference identifierQualificationReference = new ArtifactReference();
        identifierQualificationReference.setVersion("1");
        identifierQualificationReference.setGroupId(amdIdentifierQualification.getGroupId());
        identifierQualificationReference.setArtifactId(amdIdentifierQualification.getId());
        identifierQualificationReference.setName("qualification.json");

        //Creates the city schema, with a reference to its qualification.
        final ArtifactMetaData amdCity = client.createArtifact("default", "city", null, ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, false, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, citySchema, List.of(cityQualificationReference));

        final ArtifactReference cityReference = new ArtifactReference();
        cityReference.setVersion("1");
        cityReference.setGroupId(amdCity.getGroupId());
        cityReference.setArtifactId(amdCity.getId());
        cityReference.setName("types/city/city.json");

        //Creates the citizen identifier schema
        final ArtifactMetaData amdCitizenIdentifier = client.createArtifact("default", "citizenIdentifier", null, ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, false, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, citizenIdentifier,
                List.of(identifierQualificationReference));

        final ArtifactReference citizenIdentifierReference = new ArtifactReference();
        citizenIdentifierReference.setVersion("1");
        citizenIdentifierReference.setGroupId(amdCitizenIdentifier.getGroupId());
        citizenIdentifierReference.setArtifactId(amdCitizenIdentifier.getId());
        citizenIdentifierReference.setName("types/identifier/citizenIdentifier.json");

        //Creates the main qualification schema, used for the citizen
        final ArtifactMetaData amdQualification = client.createArtifact("default", "qualification", ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, qualificationSchema);

        final ArtifactReference citizenQualificationReference = new ArtifactReference();
        citizenQualificationReference.setVersion("1");
        citizenQualificationReference.setGroupId(amdQualification.getGroupId());
        citizenQualificationReference.setArtifactId(amdQualification.getId());
        citizenQualificationReference.setName("qualification.json");

        //Creates the address schema, used for the citizen
        final ArtifactMetaData amdAddress = client.createArtifact("default", "address", ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, addressSchema);

        final ArtifactReference addressReference = new ArtifactReference();
        addressReference.setVersion("1");
        addressReference.setGroupId(amdAddress.getGroupId());
        addressReference.setArtifactId(amdAddress.getId());
        addressReference.setName("sample.address.json");

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = TOPIC_NAME;

        //Creates the citizen schema, with references to qualification, city, identifier and address
        final ArtifactMetaData amdCitizen = client.createArtifact("default", artifactId, null, ArtifactType.JSON,
                IfExists.RETURN_OR_UPDATE, false, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, citizenSchema,
                List.of(citizenQualificationReference, cityReference, citizenIdentifierReference, addressReference));

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();
        // Produce 5 messages.
        int producedMessages = 0;
        try {
            System.out.println("Producing (5) messages.");
            for (int idx = 0; idx < 5; idx++) {
                // Create the message to send
                City city = new City("New York", 10001);
                city.setQualification(new CityQualification("city_qualification", 11));

                CitizenIdentifier identifier = new CitizenIdentifier(123456789);
                identifier.setIdentifierQualification(new IdentifierQualification("test_subject", 20));
                Citizen citizen = new Citizen("Carles", "Arnal", 23, city, identifier,
                        List.of(new Qualification(UUID.randomUUID().toString(), 6), new Qualification(UUID.randomUUID().toString(), 7),
                                new Qualification(UUID.randomUUID().toString(), 8)));

                // Send/produce the message on the Kafka Producer
                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, SUBJECT_NAME,
                        citizen);
                producer.send(producedRecord);

                Thread.sleep(100);
            }
            System.out.println("Messages successfully produced.");
        }
        finally {
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
                }
                else
                    records.forEach(record -> {
                        Citizen msg = record.value();
                        System.out.println("Consumed a message: " + msg + " @ " + msg.getCity());
                    });
            }
        }
        finally {
            consumer.close();
        }

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
        props.putIfAbsent(SchemaResolverConfig.SERIALIZER_DEREFERENCE_SCHEMA, "true");

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);

        //Just if security values are present, then we configure them.
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
        props.putIfAbsent(SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA, "true");
        props.putIfAbsent(SerdeConfig.VALIDATION_ENABLED, true);

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload.  So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        //Just if security values are present, then we configure them.
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

            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required "
                            + "  oauth.client.id=\"%s\" " + "  oauth.client.secret=\"%s\" "
                            + "  oauth.token.endpoint.uri=\"%s\" ;", authClient, authSecret, tokenEndpoint));
        }
    }

    /**
     * Creates the registry client
     */
    private static RegistryClient createRegistryClient(String registryUrl) {
        final String tokenEndpoint = System.getenv(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT);

        //Just if security values are present, then we configure them.
        if (tokenEndpoint != null) {
            final String authClient = System.getenv(SchemaResolverConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SchemaResolverConfig.AUTH_CLIENT_SECRET);
            ApicurioHttpClient httpClient = ApicurioHttpClientFactory.create(tokenEndpoint,
                    new AuthErrorHandler());
            OidcAuth auth = new OidcAuth(httpClient, authClient, authSecret);
            return RegistryClientFactory.create(registryUrl, Collections.emptyMap(), auth);
        }
        else {
            return RegistryClientFactory.create(registryUrl);
        }
    }
}
