package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.utils.ByteBufferInputStream;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
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

    public AvroDeserializer(RegistryClientFacade clientFacade) {
        super(clientFacade);
    }

    public AvroDeserializer(SchemaResolver<Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    public AvroDeserializer(RegistryClientFacade clientFacade, SchemaResolver<Schema, U> schemaResolver) {
        super(clientFacade, schemaResolver);
    }

    public AvroDeserializer(RegistryClientFacade clientFacade, ArtifactReferenceResolverStrategy<Schema, U> strategy,
                            SchemaResolver<Schema, U> schemaResolver) {
        super(clientFacade, strategy, schemaResolver);
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
        parser = new AvroSchemaParser<>(avroDatumProvider, config.getSchemaCacheSize());

        super.configure(config, isKey);
    }

    /**
     * @see AbstractDeserializer#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return parser;
    }

    @Override
    protected U readData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        AvroEncoding encoding = null;
        if (this.encoding != null) {
            // no encoding in header or no headers so use config
            encoding = this.encoding;
        }
        try {
            DatumReader<U> reader = avroDatumProvider.createDatumReader(schema.getParsedSchema());
            if (encoding == AvroEncoding.JSON) {
                // Create a ByteBuffer slice to avoid copying data
                ByteBuffer slice = buffer.duplicate();
                slice.position(start);
                slice.limit(start + length);
                return reader.read(null, decoderFactory.jsonDecoder(schema.getParsedSchema(),
                        new ByteBufferInputStream(slice)));
            } else {
                return reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null));
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Map<String, Object> toRuleRecord(U data) {
        if (data instanceof GenericRecord) {
            return AvroRuleRecordConverter.toMap((GenericRecord) data);
        }
        return super.toRuleRecord(data);
    }

    /**
     * Only generic records: a specific record class is generated for one schema version and cannot
     * hold a record of the migration target version.
     */
    @Override
    protected boolean supportsMigrationResult(U data) {
        return data instanceof GenericRecord && !(data instanceof SpecificRecord);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected U fromRuleRecord(Map<String, Object> record, ParsedSchema<Schema> targetSchema, U original) {
        return (U) AvroRuleRecordConverter.fromMap(record, targetSchema.getParsedSchema());
    }

    public AvroDeserializer<U> setAvroDatumProvider(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = Objects.requireNonNull(avroDatumProvider);
        return this;
    }

    public void setEncoding(AvroEncoding encoding) {
        this.encoding = encoding;
    }
}
