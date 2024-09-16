package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.data.SerdeRecord;
import io.apicurio.registry.serde.headers.HeadersHandler;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;

public abstract class AbstractKafkaSerializer<T, U> extends AbstractSerializer<T, U>
        implements Serializer<U> {

    protected HeadersHandler headersHandler;

    public AbstractKafkaSerializer() {
        super();
    }

    public AbstractKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaSerializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AbstractKafkaSerializer(RegistryClient client, ArtifactReferenceResolverStrategy strategy,
            SchemaResolver<T, U> schemaResolver) {
        super(client, strategy, schemaResolver);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(new SerdeConfig(configs), isKey);
        this.configure(new BaseKafkaSerDeConfig(configs), isKey);
    }

    protected void configure(BaseKafkaSerDeConfig config, boolean isKey) {
        super.configure(config.originals(), isKey, schemaParser());

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

    protected abstract void serializeData(Headers headers, ParsedSchema<T> schema, U data, OutputStream out)
            throws IOException;

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            if (headersHandler != null && headers != null) {
                KafkaSerdeMetadata resolverMetadata = new KafkaSerdeMetadata(topic, isKey(), headers);
                SchemaLookupResult<T> schema = getSchemaResolver()
                        .resolveSchema(new SerdeRecord<>(resolverMetadata, data));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                headersHandler.writeHeaders(headers, schema.toArtifactReference());
                serializeData(headers, schema.getParsedSchema(), data, out);
                return out.toByteArray();
            } else {
                return serializeData(topic, data);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] serialize(String topic, U data) {
        return serialize(topic, null, data);
    }
}
