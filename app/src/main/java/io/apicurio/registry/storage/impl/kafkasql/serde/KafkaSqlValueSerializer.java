package io.apicurio.registry.storage.impl.kafkasql.serde;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;

/**
 * Responsible for serializing the message key to bytes.
 */
public class KafkaSqlValueSerializer implements Serializer<KafkaSqlMessage> {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    /**
     * @see Serializer#serialize(String, Object)
     */
    @Override
    public byte[] serialize(String topic, KafkaSqlMessage message) {
        if (message == null) {
            return null;
        }

        try (UnsynchronizedByteArrayOutputStream out = UnsynchronizedByteArrayOutputStream.builder().get()) {
            mapper.writeValue(out, message);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
