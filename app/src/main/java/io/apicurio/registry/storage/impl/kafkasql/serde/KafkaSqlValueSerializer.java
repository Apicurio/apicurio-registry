package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentValue;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Responsible for serializing the message key to bytes.
 */
public class KafkaSqlValueSerializer implements Serializer<MessageValue> {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    /**
     * @see Serializer#serialize(String, Object)
     */
    @Override
    public byte[] serialize(String topic, MessageValue messageValue) {
        if (messageValue == null) {
            return null;
        }

        if (messageValue.getType() == MessageType.Content) {
            return this.serializeContent(topic, (ContentValue) messageValue);
        }
        try (UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream()) {
            out.write(ByteBuffer.allocate(1).put(messageValue.getType().getOrd()).array());
            mapper.writeValue(out, messageValue);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Special case for serializing a {@link ContentValue}.
     * @param topic
     * @param contentValue
     */
    private byte[] serializeContent(String topic, ContentValue contentValue) {
        try (UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream()) {
            out.write(ByteBuffer.allocate(1).put(contentValue.getType().getOrd()).array());
            out.write(ByteBuffer.allocate(1).put(contentValue.getAction().getOrd()).array());
            if (contentValue.getCanonicalHash() != null) {
                byte[] bytes = contentValue.getCanonicalHash().getBytes(StandardCharsets.UTF_8);
                out.write(ByteBuffer.allocate(4).putInt(bytes.length).array());
                out.write(ByteBuffer.allocate(bytes.length).put(bytes).array());
            } else {
                out.write(ByteBuffer.allocate(4).putInt(0).array());
            }

            if (contentValue.getContent() != null) {
                byte[] contentBytes = contentValue.getContent().bytes();
                out.write(ByteBuffer.allocate(4).putInt(contentBytes.length).array());
                out.write(contentBytes);
            } else {
                out.write(ByteBuffer.allocate(4).putInt(0).array());
            }

            //set references bytes and count
            if (null != contentValue.getSerializedReferences()) {
                byte[] bytes = contentValue.getSerializedReferences().getBytes(StandardCharsets.UTF_8);
                out.write(ByteBuffer.allocate(4).putInt(bytes.length).array());
                out.write(ByteBuffer.allocate(bytes.length).put(bytes).array());
            } else {
                out.write(ByteBuffer.allocate(4).putInt(0).array());
            }

            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
