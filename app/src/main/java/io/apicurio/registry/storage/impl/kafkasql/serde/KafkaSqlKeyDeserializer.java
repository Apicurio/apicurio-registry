package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessageKey;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

import static io.apicurio.registry.utils.StringUtil.toReadableString;
import static java.lang.Math.min;

/**
 * Kafka deserializer responsible for deserializing the key of a KSQL Kafka message.
 */
public class KafkaSqlKeyDeserializer implements Deserializer<KafkaSqlMessageKey> {

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
    public KafkaSqlMessageKey deserialize(String topic, byte[] data) {
        try {
            return mapper.readValue(data, KafkaSqlMessageKey.class);
        } catch (IOException ex) {
            log.error("Error deserializing a KafkaSQL message key: {}. First 32 bytes of the message key are: {}",
                    ex.getMessage(), toReadableString(Arrays.copyOf(data, min(32, data.length))));
            return null;
        }
    }
}
