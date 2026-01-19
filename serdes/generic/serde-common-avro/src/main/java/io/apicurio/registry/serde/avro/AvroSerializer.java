package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Consumer;

public class AvroSerializer<U> extends AbstractSerializer<Schema, U> {

    private final EncoderFactory encoderFactory = EncoderFactory.get();
    private AvroSchemaParser<U> parser;
    private AvroDatumProvider<U> avroDatumProvider;
    private AvroEncoding encoding;

    public AvroSerializer() {
        super();
    }

    public AvroSerializer(RegistryClientFacade clientFacade) {
        super(clientFacade);
    }

    public AvroSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    public AvroSerializer(RegistryClientFacade clientFacade, SchemaResolver<Schema, U> schemaResolver) {
        super(clientFacade, schemaResolver);
    }

    public AvroSerializer(RegistryClientFacade clientFacade,
                          ArtifactReferenceResolverStrategy<Schema, U> artifactResolverStrategy,
                          SchemaResolver<Schema, U> schemaResolver) {
        super(clientFacade, artifactResolverStrategy, schemaResolver);
    }

    private AvroSerializer<U> setAvroDatumProvider(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = Objects.requireNonNull(avroDatumProvider);
        return this;
    }

    public void setEncoding(AvroEncoding encoding) {
        this.encoding = encoding;
    }

    public AvroEncoding getEncoding() {
        return this.encoding;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(SerdeConfig configs, boolean isKey) {
        AvroSerdeConfig config = new AvroSerdeConfig(configs.originals());
        encoding = config.getAvroEncoding();

        Class<?> adp = config.getAvroDatumProvider();
        Consumer<AvroDatumProvider> consumer = this::setAvroDatumProvider;
        Utils.instantiate(AvroDatumProvider.class, adp, consumer);
        avroDatumProvider.configure(config);

        // important to instantiate the SchemaParser before calling super.configure
        parser = new AvroSchemaParser<>(avroDatumProvider);

        super.configure(config, isKey);
    }

    /**
     * @see AvroSerializer#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return parser;
    }

    /**
     * For Avro SpecificRecord, the schema is tied to the class, so we use the class as cache key.
     * For GenericRecord/GenericContainer, caching by Schema is not safe because:
     * 1. Schema.hashCode() is based only on type and props (name, namespace), not on fields
     * 2. This causes hash collisions for evolved schemas with the same name but different fields
     * 3. Schema evolution tests fail when the wrong cached result is returned
     */
    @Override
    protected Object getSchemaCacheKey(U data) {
        if (data instanceof SpecificRecord) {
            return data.getClass();
        }
        // Don't cache GenericRecord - schema evolution scenarios require fresh resolution
        return null;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void serializeData(ParsedSchema<Schema> schema, U data, OutputStream out) throws IOException {
        Encoder encoder = createEncoder(schema.getParsedSchema(), out);

        if (data instanceof NonRecordContainer) {
            // noinspection unchecked
            data = (U) NonRecordContainer.class.cast(data).getValue();
        }

        DatumWriter<U> writer = avroDatumProvider.createDatumWriter(data, schema.getParsedSchema());
        writer.write(data, encoder);
        encoder.flush();
    }

    private Encoder createEncoder(Schema schema, OutputStream os) throws IOException {
        if (encoding == AvroEncoding.JSON) {
            return encoderFactory.jsonEncoder(schema, os);
        } else {
            return encoderFactory.directBinaryEncoder(os, null);
        }
    }
}
