package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public class AvroDeserializer<U> extends AbstractDeserializer<Schema, U> {

    private final DecoderFactory decoderFactory = DecoderFactory.get();
    private AvroSchemaParser<U> parser;
    private AvroDatumProvider<U> avroDatumProvider;
    private AvroEncoding encoding;

    public AvroDeserializer() {
        super();
    }

    public AvroDeserializer(RegistryClient client) {
        super(client);
    }

    public AvroDeserializer(SchemaResolver<Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    public AvroDeserializer(RegistryClient client, SchemaResolver<Schema, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AvroDeserializer(RegistryClient client, ArtifactReferenceResolverStrategy<Schema, U> strategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(client, strategy, schemaResolver);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(SerdeConfig configs, boolean isKey) {
        AvroSerdeConfig config = new AvroSerdeConfig(configs.originals());
        encoding = config.getAvroEncoding();

        Class adp = config.getAvroDatumProvider();
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

    @Override
    protected U readData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        AvroEncoding encoding = null;
        if (this.encoding == null) {
            // no encoding in header or no headers so use config
            encoding = this.encoding;
        }
        try {
            DatumReader<U> reader = avroDatumProvider.createDatumReader(schema.getParsedSchema());
            if (encoding == AvroEncoding.JSON) {
                // copy the data into a new byte[]
                byte[] msgData = new byte[length];
                System.arraycopy(buffer.array(), start, msgData, 0, length);
                return reader.read(null, decoderFactory.jsonDecoder(schema.getParsedSchema(),
                        new ByteArrayInputStream(msgData)));
            } else {
                return reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null));
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public AvroDeserializer<U> setAvroDatumProvider(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = Objects.requireNonNull(avroDatumProvider);
        return this;
    }

    public void setEncoding(AvroEncoding encoding) {
        this.encoding = encoding;
    }
}
