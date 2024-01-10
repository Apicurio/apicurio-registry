package io.apicurio.registry.serde.nats.example;

import io.apicurio.registry.serde.nats.client.exceptions.NatsClientException;
import io.apicurio.registry.serde.nats.client.streaming.consumers.ConnectionFactory;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumer;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsConsumerImpl;
import io.apicurio.registry.serde.nats.client.streaming.consumers.NatsReceiveMessage;
import io.apicurio.registry.serde.nats.client.streaming.producers.NatsProducer;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import java.util.Date;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

public class NatsAvroExample {
    private static final String SCHEMAV1 = "{\"type\":\"record\",\"name\":\"test\",\"namespace\":\"com.github"
            + ".sourabhagrawal\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";

    public static void main(String[] args)
            throws NatsClientException, InterruptedException, IOException,
            JetStreamApiException {
        Schema schema = new Schema.Parser().parse(SCHEMAV1);
        GenericRecord record = new GenericData.Record(schema);
        Date now = new Date();
        record.put("Message", "Hello!");
        record.put("Time", now.getTime());

        Connection connection = ConnectionFactory.getConnection();
        JetStreamManagement jsm = connection.jetStreamManagement();

        StreamConfiguration stream = new StreamConfiguration.Builder().subjects("test").name("test").build();
        ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder().durable("test")
                .durable("test").filterSubject("test").build();

        jsm.addStream(stream); //Create Stream in advance
        jsm.addOrUpdateConsumer(stream.getName(), consumerConfiguration); //Create Consumer in advance

        PullSubscribeOptions options = PullSubscribeOptions.builder().bind(true).stream(stream.getName())
                .durable(consumerConfiguration.getDurable()).build();

        NatsProducer producer = NatsProducer.defaultOf("test");
        NatsConsumer consumer = new NatsConsumerImpl("test", options);
        producer.sendMessage(record);
        while(true) {
            NatsReceiveMessage message = consumer.receive();
            if(message==null){
                continue;
            }
            if (message.getPayload() instanceof GenericRecord) {
                GenericRecord event1 = (GenericRecord)message.getPayload();
                System.out.println(event1);
                System.out.println(NatsAvroExample.class.getName());
            }
            message.ack();
        }
    }
}
