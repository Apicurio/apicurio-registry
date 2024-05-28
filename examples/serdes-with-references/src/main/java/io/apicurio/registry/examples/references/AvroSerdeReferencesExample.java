package io.apicurio.registry.examples.references;

import com.kubetrade.schema.common.Exchange;
import com.kubetrade.schema.trade.TradeKey;
import com.kubetrade.schema.trade.TradeRaw;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
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

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class AvroSerdeReferencesExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = AvroSerdeReferencesExample.class.getSimpleName();
    private static final String SUBJECT_NAME = "Trade";

    public static void main(String [] args) throws Exception {
        System.out.println("Starting example " + AvroSerdeReferencesExample.class.getSimpleName());
        String topicName = TOPIC_NAME;
        String subjectName = SUBJECT_NAME;

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();
        // Produce 5 messages.
        int producedMessages = 0;
        try {
            System.out.println("Producing (5) messages.");
            for (int idx = 0; idx < 5; idx++) {
                TradeRaw tradeRaw = new TradeRaw();
                TradeKey tradeKey = new TradeKey();
                tradeKey.setKey(String.valueOf(producedMessages++));
                tradeKey.setExchange(Exchange.GEMINI);
                tradeRaw.setTradeKey(tradeKey);
                tradeRaw.setPayload("Hello (" + producedMessages++ + ")!");
                tradeRaw.setSymbol("testSymbol");

                // Send/produce the message on the Kafka Producer
                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, subjectName, tradeRaw);
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
        KafkaConsumer<Long, TradeRaw> consumer = createKafkaConsumer();

        // Subscribe to the topic
        System.out.println("Subscribing to topic " + topicName);
        consumer.subscribe(Collections.singletonList(topicName));

        // Consume the 5 messages.
        try {
            int messageCount = 0;
            System.out.println("Consuming (5) messages.");
            while (messageCount < 5) {
                final ConsumerRecords<Long, TradeRaw> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else records.forEach(record -> {
                    TradeRaw tradeRaw = record.value();
                    System.out.println("Consumed a message: " + tradeRaw.getPayload() + " @ " + tradeRaw.getTradeKey().getKey());
                });
            }
        } finally {
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
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // Register the artifact if not found in the registry.
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT_IF_EXISTS, IfExists.RETURN.name());
        props.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_JSON);


        //Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka producer
        Producer<Object, Object> producer = new KafkaProducer<>(props);
        return producer;
    }

    /**
     * Creates the Kafka consumer.
     */
    private static KafkaConsumer<Long, TradeRaw> createKafkaConsumer() {
        Properties props = new Properties();

        // Configure Kafka
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + TOPIC_NAME);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use the Apicurio Registry provided Kafka Deserializer for Avro
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
        props.putIfAbsent(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_JSON);
        props.putIfAbsent(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload.  So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        //Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka Consumer
        KafkaConsumer<Long, TradeRaw> consumer = new KafkaConsumer<>(props);
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
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
            props.putIfAbsent("security.protocol", "SASL_SSL");

            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG, String.format("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                    "  oauth.client.id=\"%s\" "+
                    "  oauth.client.secret=\"%s\" "+
                    "  oauth.token.endpoint.uri=\"%s\" ;", authClient, authSecret, tokenEndpoint));
        }
    }
}