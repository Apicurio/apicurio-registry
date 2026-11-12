package io.apicurio.registry.storage.impl.kafkasql.serde;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.core.JacksonException;

import java.io.UncheckedIOException;

/**
 * Responsible for serializing the message key to bytes.
 */
public class KafkaSqlValueSerializer implements Serializer<KafkaSqlMessage> {

    private static final ObjectMapper mapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(Include.NON_NULL).withValueInclusion(Include.NON_NULL))
            .build();

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
        } catch (JacksonException e) {
            throw new UncheckedIOException(new java.io.IOException(e.getMessage(), e));
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
