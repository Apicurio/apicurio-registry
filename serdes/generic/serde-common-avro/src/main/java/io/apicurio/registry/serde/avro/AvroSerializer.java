package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

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

    public AvroSerializer(RegistryClient client) {
        super(client);
    }

    public AvroSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    public AvroSerializer(RegistryClient client, SchemaResolver<Schema, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AvroSerializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<Schema, U> artifactResolverStrategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
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
     * @see io.apicurio.registry.serde.AbstractSerDe#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return parser;
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
