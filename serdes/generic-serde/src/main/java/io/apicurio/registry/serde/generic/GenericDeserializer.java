package io.apicurio.registry.serde.generic;

import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.util.Objects;

public class GenericDeserializer<SCHEMA, DATA> extends GenericSerDe<SCHEMA, DATA> {


    protected FallbackArtifactProvider fallbackArtifactProvider;

    /**
     * @param serDeDatatype  required
     * @param schemaResolver may be null
     */
    public GenericDeserializer(GenericSerDeDatatype<SCHEMA, DATA> serDeDatatype, SchemaResolver<SCHEMA, DATA> schemaResolver) {
        super(serDeDatatype, schemaResolver);
    }


    @Override
    public void configure(GenericConfig config) {
        Objects.requireNonNull(config);
        serDeConfig = new GenericSerDeConfig(config);

        super.configure(serDeConfig);

        fallbackArtifactProvider = serDeConfig.getFallbackArtifactProvider();
        fallbackArtifactProvider.configure(serDeConfig.getRawConfig(), serDeConfig.isKey());
        if (fallbackArtifactProvider instanceof DefaultFallbackArtifactProvider) {
            if (!((DefaultFallbackArtifactProvider) fallbackArtifactProvider).isConfigured()) {
                //it's not configured, just remove it so it's not executed
                fallbackArtifactProvider = null;
            }
        }
    }


    public DATA deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        var buffer = ByteBuffer.wrap(data).asReadOnlyBuffer();
        ArtifactReference artifactReference = null;

        if (headersHandler != null && headers != null) {
            artifactReference = headersHandler.readHeaders(headers);
        }

        if (artifactReference == null || !artifactReference.hasValue()) {
            if (buffer.get(0) == MAGIC_BYTE) {
                buffer.get(); // Move position

                artifactReference = getIdHandler().readId(buffer);

            } else if (headers == null) {
                throw new IllegalStateException("Headers cannot be null");
            }
        }

        //try to read data even if artifactReference has no value, maybe there is a fallbackArtifactProvider configured
        SchemaLookupResult<SCHEMA> schema = resolve(topic, headers, data, artifactReference);
        return serDeDatatype.readData(headers, schema.getParsedSchema(), buffer);
    }


    private SchemaLookupResult<SCHEMA> resolve(String topic, Headers headers, byte[] data, ArtifactReference artifactReference) {
        try {
            return schemaResolver.resolveSchemaByArtifactReference(artifactReference);
        } catch (RuntimeException ex) {
            if (fallbackArtifactProvider == null) {
                throw ex;
            } else {
                try {
                    ArtifactReference fallbackReference = fallbackArtifactProvider.get(topic, headers, data);
                    return schemaResolver.resolveSchemaByArtifactReference(fallbackReference);
                } catch (RuntimeException fex) {
                    fex.addSuppressed(ex);
                    throw fex;
                }
            }
        }
    }
}
