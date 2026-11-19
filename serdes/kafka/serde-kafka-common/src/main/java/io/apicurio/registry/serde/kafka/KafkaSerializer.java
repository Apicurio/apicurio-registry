package io.apicurio.registry.serde.kafka;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.Default4ByteIdHandler;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.data.SerdeRecord;
import io.apicurio.registry.serde.kafka.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.kafka.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.kafka.headers.DefaultHeadersHandler;
import io.apicurio.registry.serde.kafka.headers.HeadersHandler;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;

public class KafkaSerializer<T, U> implements Serializer<U> {

    protected final AbstractSerializer<T, U> delegatedSerializer;

    protected HeadersHandler headersHandler;

    private KafkaDeadLetterQueue deadLetterQueue;

    protected KafkaSerializer(AbstractSerializer<T, U> delegatedSerializer) {
        this.delegatedSerializer = delegatedSerializer;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        delegatedSerializer.configure(new SerdeConfig(configs), isKey);
        this.configure(new BaseKafkaSerDeConfig(configs), isKey);
        Object dlqTopic = configs.get(SerdeConfig.CONTRACT_RULES_DLQ_TOPIC);
        if (dlqTopic != null && !dlqTopic.toString().isBlank()) {
            deadLetterQueue = new KafkaDeadLetterQueue(dlqTopic.toString(), configs);
            delegatedSerializer.setDeadLetterQueue(deadLetterQueue);
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

    protected void serializeData(Headers headers, ParsedSchema<T> schema, U data, OutputStream out)
            throws IOException {
        delegatedSerializer.serializeData(schema, data, out);
    }

    @Override
    public byte[] serialize(String topic, U data) {
        try {
            return delegatedSerializer.serializeData(topic, data);
        } catch (RuntimeException e) {
            throw toSerializationException(e);
        }
    }

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            if (headersHandler != null && headers != null) {
                KafkaSerdeMetadata resolverMetadata = new KafkaSerdeMetadata(topic,
                        delegatedSerializer.getSerdeConfigurer().isKey(), headers);

                // Check if schema should be read from headers
                if (headersHandler instanceof DefaultHeadersHandler) {
                    DefaultHeadersHandler defaultHandler = (DefaultHeadersHandler) headersHandler;
                    if (defaultHandler.isUseSchemaFromHeaders()) {
                        String schemaContent = defaultHandler.readSchemaFromHeaders(headers);
                        String schemaType = defaultHandler.readSchemaTypeFromHeaders(headers);
                        resolverMetadata.setExplicitSchemaContent(schemaContent);
                        resolverMetadata.setExplicitSchemaType(schemaType);
                    }
                }

                SchemaLookupResult<T> schema = delegatedSerializer.getSerdeConfigurer().getSchemaResolver()
                        .resolveSchema(new SerdeRecord<>(resolverMetadata, data));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                U toWrite = delegatedSerializer.applyContractRulesForWrite(topic, schema, data);
                headersHandler.writeHeaders(headers, schema.toArtifactReference());
                this.serializeData(headers, schema.getParsedSchema(), toWrite, out);
                return out.toByteArray();
            } else {
                return delegatedSerializer.serializeData(topic, data);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw toSerializationException(e);
        }
    }

    private static final int MAX_CAUSE_DEPTH = 32;

    /**
     * Converts a registry API failure (for example a rule violation on auto-register) into a Kafka
     * {@link SerializationException} whose message carries the registry's reason. Other runtime
     * exceptions are rethrown unchanged.
     */
    static RuntimeException toSerializationException(RuntimeException e) {
        if (e instanceof SerializationException) {
            return e;
        }
        Throwable previous = null;
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ApiException) {
                String message = previous != null && previous.getMessage() != null ? previous.getMessage()
                        : "Registry request failed";
                return new SerializationException(message, e);
            }
            previous = current;
            current = current.getCause();
        }
        return e;
    }

    @Override
    public void close() {
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
        delegatedSerializer.getSerdeConfigurer().close();
    }

    public void as4ByteId() {
        delegatedSerializer.getSerdeConfigurer().setIdHandler(new Default4ByteIdHandler());
    }

    public SchemaResolver<T, U> getSchemaResolver() {
        return delegatedSerializer.getSerdeConfigurer().getSchemaResolver();
    }

    public void setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        delegatedSerializer.getSerdeConfigurer().setSchemaResolver(schemaResolver);
    }
}
