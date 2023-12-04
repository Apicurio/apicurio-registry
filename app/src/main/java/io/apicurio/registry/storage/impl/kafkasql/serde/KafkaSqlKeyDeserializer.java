package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageTypeToKeyClass;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Kafka deserializer responsible for deserializing the key of a KSQL Kafka message.
 */
public class KafkaSqlKeyDeserializer implements Deserializer<MessageKey> {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlKeyDeserializer.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    /**
     * @see Deserializer#deserialize(String, byte[])
     */
    @Override
    public MessageKey deserialize(String topic, byte[] data) {
        try {
            byte msgTypeOrdinal = data[0];
            Class<? extends MessageKey> keyClass = MessageTypeToKeyClass.ordToKeyClass(msgTypeOrdinal);
            UnsynchronizedByteArrayInputStream in = new UnsynchronizedByteArrayInputStream(data, 1);
            MessageKey key = mapper.readValue(in, keyClass);
            return key;
        } catch (IOException e) {
            log.error("Error deserializing a Kafka+SQL message (key).", e);
            return null;
        }
    }

}
