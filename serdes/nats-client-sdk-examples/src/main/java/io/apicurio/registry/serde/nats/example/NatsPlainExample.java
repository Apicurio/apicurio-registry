package io.apicurio.registry.serde.nats.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.NatsMessage;
import lombok.*;

import java.io.Serializable;

public class NatsPlainExample {

    public static final String SUBJECT_NAME = "test-subject";

    public static final String SCHEMA = "{" +
            "    \"$id\": \"https://example.com/message.schema.json\"," +
            "    \"$schema\": \"http://json-schema.org/draft-07/schema#\"," +
            "    \"required\": [" +
            "        \"firstName\"," +
            "        \"age\"" +
            "    ]," +
            "    \"type\": \"object\"," +
            "    \"properties\": {" +
            "        \"firstName\": {" +
            "            \"description\": \"\"," +
            "            \"type\": \"string\"" +
            "        }," +
            "        \"age\": {" +
            "            \"description\": \"\"," +
            "            \"type\": \"number\"" +
            "        }" +
            "    }" +
            "}";

    private static ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        System.out.println("Starting example " + NatsPlainExample.class.getSimpleName());

        // Connect to NATS
        try (Connection natsConnection = Nats.connect(Options.DEFAULT_URL)) {


            var jetStream = natsConnection.jetStream();

            JetStreamManagement jsm = natsConnection.jetStreamManagement();
            StreamConfiguration sc = StreamConfiguration.builder()
                    .name("schema-registry-2")
                    .storageType(StorageType.File)
                    .subjects(SUBJECT_NAME).build();
            jsm.addStream(sc);

            User user = new User("first", "last", 27);
            System.out.println("Sending: " + user);
            String userSerialized = MAPPER.writeValueAsString(user);
            Message msg = NatsMessage.builder().subject(SUBJECT_NAME).data(userSerialized).build();

            PublishAck pa = jetStream.publish(msg);
            pa.throwOnHasError();
            System.out.println(pa);

            // Subscribe and Deserialize

            Subscription sub = jetStream.subscribe(SUBJECT_NAME);
            Message receivedMsg = sub.nextMessage(1000);
            User receivedUser = MAPPER.readValue(receivedMsg.getData(), User.class);
            receivedMsg.ack();

            System.out.println("Received: " + receivedUser);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class User implements Serializable {

        public String firstName;

        public String lastName;

        public int age;
    }
}
