package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.headers.HeadersHandler;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public abstract class AbstractKafkaDeserializer<T, U> extends AbstractDeserializer<T, U>
        implements Deserializer<U> {

    protected HeadersHandler headersHandler;

    public AbstractKafkaDeserializer() {
        super();
    }

    public AbstractKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaDeserializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaDeserializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AbstractKafkaDeserializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
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

    @Override
    public U deserialize(String topic, byte[] data) {
        return deserializeData(topic, data);
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
                return readData(topic, data, artifactReference);
            }
        }
        if (data[0] == MAGIC_BYTE) {
            return deserialize(topic, data);
        } else if (headers == null) {
            throw new IllegalStateException("Headers cannot be null");
        } else {
            // try to read data even if artifactReference has no value, maybe there is a
            // fallbackArtifactProvider configured
            return readData(topic, data, artifactReference);
        }
    }
}
