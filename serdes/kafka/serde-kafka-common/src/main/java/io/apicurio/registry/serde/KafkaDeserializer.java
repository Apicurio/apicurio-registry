package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.headers.HeadersHandler;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.ByteBuffer;
import java.util.Map;

import static io.apicurio.registry.serde.AbstractSerDe.MAGIC_BYTE;

public abstract class KafkaDeserializer<T, U> implements Deserializer<U> {

    protected final AbstractDeserializer<T, U> delegatedDeserializer;
    protected HeadersHandler headersHandler;

    public KafkaDeserializer(AbstractDeserializer<T, U> delegatedDeserializer) {
        this.delegatedDeserializer = delegatedDeserializer;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        delegatedDeserializer.configure(new SerdeConfig(configs), isKey);
        this.configure(new BaseKafkaSerDeConfig(configs), isKey);
    }

    @Override
    public U deserialize(String topic, byte[] data) {
        return delegatedDeserializer.deserializeData(topic, data);
    }

    protected U readData(ParsedSchema<T> schema, ByteBuffer buffer, int start, int length) {
        return delegatedDeserializer.readData(schema, buffer, start, length);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        ArtifactReference artifactReference = null;
        if (headersHandler != null && headers != null) {
            artifactReference = headersHandler.readHeaders(headers);

            if (artifactReference.hasValue()) {
                return delegatedDeserializer.readData(topic, data, artifactReference);
            }
        }
        if (data[0] == MAGIC_BYTE) {
            return deserialize(topic, data);
        } else if (headers == null) {
            throw new IllegalStateException("Headers cannot be null");
        } else {
            // try to read data even if artifactReference has no value, maybe there is a
            // fallbackArtifactProvider configured
            return delegatedDeserializer.readData(topic, data, artifactReference);
        }
    }

    protected void configure(BaseKafkaSerDeConfig config, boolean isKey) {
        boolean headersEnabled = config.enableHeaders();
        if (headersEnabled) {
            Object headersHandler = config.getHeadersHandler();
            Utils.instantiate(HeadersHandler.class, headersHandler, this::setHeadersHandler);
            this.headersHandler.configure(config.originals(), isKey);
        }
    }

    public void setHeadersHandler(HeadersHandler headersHandler) {
        this.headersHandler = headersHandler;
    }

    @Override
    public void close() {
        delegatedDeserializer.close();
    }

    public void as4ByteId() {
        delegatedDeserializer.as4ByteId();
    }
}
