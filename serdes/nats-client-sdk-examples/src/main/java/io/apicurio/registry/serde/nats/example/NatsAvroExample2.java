package io.apicurio.registry.serde.nats.example;

import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.nats.client.NatsClientManager;
import io.apicurio.registry.serde.config.nats.NatsConsumerConfig;
import io.apicurio.registry.serde.config.nats.NatsProducerConfig;
import io.apicurio.registry.serde.config.nats.NatsSerdeConfig;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumer;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumerImpl;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumerRecord;
import io.apicurio.registry.serde.nats.client.streaming.producers.NatsProducer;
import io.apicurio.registry.serde.nats.client.streaming.producers.NatsProducerImpl;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StreamConfiguration;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.time.Instant;
import java.util.Properties;

public class NatsAvroExample2 {

    private static final String SCHEMAV1 = "{\"type\":\"record\",\"name\":\"test\",\"namespace\":\"com.github"
            + ".sourabhagrawal\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";

    private static final String STREAM = "test";
    private static final String SUBJECT = "test";

    public static void main(String[] args) throws Exception {


        Connection connection = NatsClientManager.getDefaultConnection();

        // Create stream

        JetStreamManagement jsm = connection.jetStreamManagement();

        StreamConfiguration stream = StreamConfiguration.builder()
                .name(STREAM)
                .subjects(SUBJECT)
                .build();

        jsm.addStream(stream);

        // Produce some messages

        NatsProducer<GenericRecord> producer = createNATSProducer(connection);

        Schema schema = new Schema.Parser().parse(SCHEMAV1);

        for (int i = 0; i < 10; i++) {

            GenericRecord record = new GenericData.Record(schema);
            record.put("Message", "Hello " + i + "!");
            record.put("Time", Instant.now());
            producer.send(record);
        }

        // Create a durable consumer

        ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                .durable(STREAM + "-consumer-1") // Name for the durable consumer
                .filterSubject(SUBJECT)
                .build();

        jsm.addOrUpdateConsumer(stream.getName(), consumerConfiguration); // TODO This can be done by the manager

        // Consume some messages

        PullSubscribeOptions options = PullSubscribeOptions.builder()
                .bind(true)
                .stream(stream.getName())
                .durable(consumerConfiguration.getDurable())
                .build();

        NatsConsumer<GenericRecord> consumer = createNATSConsumer(connection, options);

        var i = 0;
        while (i < 10) {
            NatsConsumerRecord<GenericRecord> message = consumer.receive();
            if (message != null) {
                GenericRecord event1 = message.getPayload();
                System.out.println(event1);
                System.out.println(NatsAvroExample2.class.getName());
                message.ack();
                i++;
            }
        }

    }


    private static <T> NatsProducer<T> createNATSProducer(Connection connection) throws Exception {

        /*
apicurio.registry.url=http://localhost:8080/apis/registry/v3
apicurio.registry.artifact-resolver-strategy=io.apicurio.registry.serde.avro.strategy.NatsSubjectSchemaIdStrategy
apicurio.registry.auto-register=true
apicurio.registry.use-id=contentId
apicurio.registry.use-specific-avro-reader=true
         */

        Properties props = new Properties();

        // Configure NATS settings?
        // ...

        // Use the Apicurio Registry provided Kafka Serializer for Avro
        props.putIfAbsent(NatsProducerConfig.SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

        props.putIfAbsent(NatsSerdeConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        props.putIfAbsent(NatsSerdeConfig.AUTO_REGISTER_ARTIFACT, true);

        // Create NATS producer
        return new NatsProducerImpl<>(connection, SUBJECT, props);
    }


    private static <T> NatsConsumer<T> createNATSConsumer(Connection connection, PullSubscribeOptions options) throws Exception {

        /*
apicurio.registry.url=http://localhost:8080/apis/registry/v3
apicurio.registry.artifact-resolver-strategy=io.apicurio.registry.serde.avro.strategy.NatsSubjectSchemaIdStrategy
apicurio.registry.auto-register=true
apicurio.registry.use-id=contentId
apicurio.registry.use-specific-avro-reader=true
         */

        Properties props = new Properties();

        // Configure NATS settings?
        // ...

        // Use the Apicurio Registry provided Kafka Serializer for Avro
        props.putIfAbsent(NatsConsumerConfig.DESERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

        props.putIfAbsent(NatsSerdeConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        props.putIfAbsent(NatsSerdeConfig.AUTO_REGISTER_ARTIFACT, true);

        // Create NATS producer
        return new NatsConsumerImpl<>(connection, SUBJECT, options, props);
    }
}
