package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter.MESSAGE_TYPE_HEADER;
import static io.apicurio.registry.utils.StringUtil.toReadableString;
import static java.lang.Math.min;

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
                log.error("Message missing required message type header: " + MESSAGE_TYPE_HEADER);
                return null;
            }

            Class<? extends KafkaSqlMessage> msgClass = KafkaSqlMessageIndex.lookup(messageType);
            if (msgClass == null) {
                throw new Exception("Unknown KafkaSql message class for '" + messageType + "'");
            }
            return mapper.readValue(data, msgClass);
        } catch (Exception ex) {
            log.error("Error deserializing a KafkaSQL message value: {}. First 32 bytes of the message value are: {}",
                    ex.getMessage(), toReadableString(Arrays.copyOf(data, min(32, data.length))));
            return null;
        }
    }

    /**
     * Extracts the UUID from the message. The UUID should be found in a message header.
     */
    private static String extractMessageType(Headers headers) {
        return Optional.ofNullable(headers.headers(MESSAGE_TYPE_HEADER)).map(Iterable::iterator).map(it -> {
            return it.hasNext() ? it.next() : null;
        }).map(Header::value).map(String::new).orElse(null);
    }
}
