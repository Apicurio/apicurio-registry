package io.apicurio.registry.serde.kafka;

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

    protected KafkaSerializer(AbstractSerializer<T, U> delegatedSerializer) {
        this.delegatedSerializer = delegatedSerializer;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        delegatedSerializer.configure(new SerdeConfig(configs), isKey);
        this.configure(new BaseKafkaSerDeConfig(configs), isKey);
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
        return delegatedSerializer.serializeData(topic, data);
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
                headersHandler.writeHeaders(headers, schema.toArtifactReference());
                this.serializeData(headers, schema.getParsedSchema(), data, out);
                return out.toByteArray();
            } else {
                return delegatedSerializer.serializeData(topic, data);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
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
