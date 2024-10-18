package io.apicurio.tests.serdes.apicurio.nats;

import io.apicurio.registry.serde.avro.nats.client.streaming.consumers.NatsConsumer;
import io.apicurio.registry.serde.avro.nats.client.streaming.consumers.NatsConsumerImpl;
import io.apicurio.registry.serde.avro.nats.client.streaming.consumers.NatsConsumerRecord;
import io.apicurio.registry.serde.avro.nats.client.streaming.producers.NatsProducer;
import io.apicurio.registry.serde.avro.nats.client.streaming.producers.NatsProducerImpl;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamConfiguration;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class AvroNatsSerdeIT extends ApicurioRegistryBaseIT {

    private static final String SCHEMAV1 = "{\"type\":\"record\",\"name\":\"test\",\"namespace\":\"com.github"
            + ".sourabhagrawal\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";

    private GenericContainer<?> nats;

    public static final Integer NATS_PORT = 4222;

    public static final Integer NATS_MNTR_PORT = 8222;

    @BeforeAll
    void setupEnvironment() {
        if (nats == null || !nats.isRunning()) {
            nats = new GenericContainer<>("nats:2.10.20").withExposedPorts(NATS_PORT, NATS_MNTR_PORT)
                    .withCommand("--jetstream");
            nats.start();
        }
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        if (nats != null && nats.isRunning()) {
            nats.stop();
        }
    }

    @Test
    public void testNatsJsonSchema() throws IOException, InterruptedException, JetStreamApiException {
        String subjectId = generateArtifactId();
        Schema schema = new Schema.Parser().parse(SCHEMAV1);
        GenericRecord record = new GenericData.Record(schema);
        Date now = new Date();
        record.put("Message", "Hello!");
        record.put("Time", now.getTime());

        JetStreamManagement jsm;
        try (Connection connection = Nats.connect(new Options.Builder()
                .server("nats://" + nats.getHost() + ":" + nats.getMappedPort(NATS_PORT)).build())) {
            jsm = connection.jetStreamManagement();

            StreamConfiguration stream = new StreamConfiguration.Builder().subjects(subjectId).name(subjectId)
                    .retentionPolicy(RetentionPolicy.WorkQueue).build();

            ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder().durable(subjectId)
                    .durable(subjectId).filterSubject(subjectId).ackWait(2000).build();

            jsm.addStream(stream); // Create Stream in advance
            jsm.addOrUpdateConsumer(stream.getName(), consumerConfiguration); // Create Consumer in advance

            PullSubscribeOptions options = PullSubscribeOptions.builder().bind(true).stream(stream.getName())
                    .durable(consumerConfiguration.getDurable()).build();

            Map<String, Object> configs = Map.of(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true",
                    SerdeConfig.REGISTRY_URL, ApicurioRegistryBaseIT.getRegistryV3ApiUrl(),
                    SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());

            NatsProducer<GenericRecord> producer = new NatsProducerImpl<>(connection, subjectId, configs);
            NatsConsumer<GenericRecord> consumer = new NatsConsumerImpl<>(connection, subjectId, options,
                    configs);

            producer.publish(record);

            NatsConsumerRecord<GenericRecord> message = consumer.fetch();

            if (message.getPayload() != null) {
                GenericRecord event1 = message.getPayload();
                Assertions.assertEquals(record, event1);
            }

            message.ack();

            producer.publish(record);
            consumer.fetch().nak(); // Nak will redeliver the message until ack'd so message should be left in
                                    // stream
            Assertions.assertTrue(jsm.getStreamInfo(stream.getName()).getStreamState().getMsgCount() == 1);

            jsm.purgeStream(stream.getName());
            producer.publish(record);
            consumer.fetch().term(); // this will terminate the message, since there was only one message in
                                     // stream and after calling terminate we should not have any message left
                                     // in stream
            Assertions.assertTrue(jsm.getStreamInfo(stream.getName()).getStreamState().getMsgCount() == 0);
            producer.publish(record);

            NatsConsumerRecord<GenericRecord> newMessage = consumer.fetch();
            Thread.sleep(1000); // Ack wait is set to 2second for consumer,after 2 second if ack is not
                                // received consumer will redeliver the message
            newMessage.inProgress(); // with this we are resetting the ackwait to 2 second again so consumer
                                     // do not redeliver the message. we should have a message in ack pending
                                     // state
            Assertions.assertTrue(jsm.getConsumerInfo(stream.getName(), consumerConfiguration.getDurable())
                    .getNumAckPending() == 1);
        }
    }
}
