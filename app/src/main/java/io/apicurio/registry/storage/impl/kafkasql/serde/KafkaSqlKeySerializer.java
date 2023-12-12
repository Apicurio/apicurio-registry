package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * Responsible for serializing the message key to bytes.
 */
public class KafkaSqlKeySerializer implements Serializer<MessageKey> {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    /**
     * @see Serializer#serialize(String, Object)
     */
    @Override
    public byte[] serialize(String topic, MessageKey messageKey) {
        try {
            UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream();
            out.write(ByteBuffer.allocate(1).put((byte) messageKey.getType().getOrd()).array());
            mapper.writeValue(out, messageKey);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
