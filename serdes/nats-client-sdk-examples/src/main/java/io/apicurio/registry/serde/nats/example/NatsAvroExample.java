package io.apicurio.registry.serde.nats.example;

import io.apicurio.registry.serde.nats.client.exceptions.ApicurioNatsException;
import io.nats.client.JetStreamApiException;

import java.io.IOException;

public class NatsAvroExample {
    private static final String SCHEMAV1 = "{\"type\":\"record\",\"name\":\"test\",\"namespace\":\"com.github"
            + ".sourabhagrawal\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";

    public static void main(String[] args)
            throws ApicurioNatsException, InterruptedException, IOException,
            JetStreamApiException {
        /*
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
            NatsConsumerRecord message = consumer.receive();
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

         */
    }
}
