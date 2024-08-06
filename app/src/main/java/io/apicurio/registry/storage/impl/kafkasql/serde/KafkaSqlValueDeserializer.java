package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Kafka deserializer responsible for deserializing the value of a KSQL Kafka message.
 */
public class KafkaSqlValueDeserializer implements Deserializer<KafkaSqlMessage> {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlValueDeserializer.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    /**
     * @see org.apache.kafka.common.serialization.Deserializer#deserialize(java.lang.String, byte[])
     */
    @Override
    public KafkaSqlMessage deserialize(String topic, byte[] data) {
        // Not supported - must deserialize with headers.
        return null;
    }

    /**
     * @see org.apache.kafka.common.serialization.Deserializer#deserialize(java.lang.String,
     *      org.apache.kafka.common.header.Headers, byte[])
     */
    @Override
    public KafkaSqlMessage deserialize(String topic, Headers headers, byte[] data) {
        // Return null for tombstone messages
        if (data == null) {
            return null;
        }

        try {
            String messageType = extractMessageType(headers);
            if (messageType == null) {
                log.error("Message missing required message type header: mt");
                return null;
            }

            Class<? extends KafkaSqlMessage> msgClass = KafkaSqlMessageIndex.lookup(messageType);
            if (msgClass == null) {
                throw new Exception("Unknown KafkaSql message class for '" + messageType + "'");
            }
            KafkaSqlMessage message = mapper.readValue(data, msgClass);
            return message;
        } catch (Exception e) {
            log.error("Error deserializing a Kafka+SQL message (value).", e);
            return null;
        }
    }

    /**
     * Extracts the UUID from the message. The UUID should be found in a message header.
     */
    private static String extractMessageType(Headers headers) {
        return Optional.ofNullable(headers.headers("mt")).map(Iterable::iterator).map(it -> {
            return it.hasNext() ? it.next() : null;
        }).map(Header::value).map(String::new).orElse(null);
    }

}
