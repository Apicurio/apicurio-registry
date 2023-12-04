package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentValue;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageTypeToValueClass;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Kafka deserializer responsible for deserializing the value of a KSQL Kafka message.
 */
public class KafkaSqlValueDeserializer implements Deserializer<MessageValue> {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlValueDeserializer.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    /**
     * @see Deserializer#deserialize(String, byte[])
     */
    @Override
    public MessageValue deserialize(String topic, byte[] data) {
        // Return null for tombstone messages
        if (data == null) {
            return null;
        }

        try {
            byte msgTypeOrdinal = data[0];
            if (msgTypeOrdinal == MessageType.Content.getOrd()) {
                return this.deserializeContent(topic, data);
            }
            Class<? extends MessageValue> keyClass = MessageTypeToValueClass.ordToValue(msgTypeOrdinal);
            UnsynchronizedByteArrayInputStream in = new UnsynchronizedByteArrayInputStream(data, 1);
            MessageValue key = mapper.readValue(in, keyClass);
            return key;
        } catch (Exception e) {
            log.error("Error deserializing a Kafka+SQL message (value).", e);
            return null;
        }
    }

    /**
     * Special case deserialize of a {@link ContentValue} value.
     * @param topic
     * @param data
     */
    private ContentValue deserializeContent(String topic, byte[] data) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.get(); // the first byte is the message type ordinal, skip that
        byte actionOrdinal = byteBuffer.get();

        // Canonical hash (length of string + string bytes)
        String canonicalHash = null;
        int hashLen = byteBuffer.getInt();
        if (hashLen > 0) {
            byte[] bytes = new byte[hashLen];
            byteBuffer.get(bytes);
            canonicalHash = new String(bytes, StandardCharsets.UTF_8);
        }

        // Content (length of content + content bytes)
        ContentHandle contentHandle = null;
        int numContentBytes = byteBuffer.getInt();
        if (numContentBytes > 0) {
            byte[] contentBytes = new byte[numContentBytes];
            byteBuffer.get(contentBytes);
            contentHandle = ContentHandle.create(contentBytes);
        }

        String serializedReferences = null;
        //When deserializing from other storage versions, the references byte count might not be there, so we first check if there are anything remaining in the buffer
        if (byteBuffer.hasRemaining()) {
            // References (length of references + references bytes)
            int referencesLen = byteBuffer.getInt();
            if (referencesLen > 0) {
                byte[] bytes = new byte[referencesLen];
                byteBuffer.get(bytes);
                serializedReferences = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        ActionType action = ActionType.fromOrd(actionOrdinal);

        return ContentValue.create(action, canonicalHash, contentHandle, serializedReferences);
    }

}
