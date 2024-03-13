package io.apicurio.registry.storage.impl.kafkasql.serde;

import java.io.IOException;

import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessageKey;

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
            KafkaSqlMessageKey key = mapper.readValue(data, KafkaSqlMessageKey.class);
            return key;
        } catch (IOException e) {
            log.error("Error deserializing a Kafka+SQL message (key).", e);
            return null;
        }
    }

}
