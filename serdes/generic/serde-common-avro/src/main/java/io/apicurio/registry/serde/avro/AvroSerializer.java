package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AvroSerializer<U> extends AbstractSerializer<Schema, U> {

    private static final int VALIDATED_SCHEMA_PAIRS_MAX_SIZE = 64;

    private final EncoderFactory encoderFactory = EncoderFactory.get();
    private final ConcurrentHashMap<ValidatedSchemaPair, Boolean> validatedSchemaPairs = new ConcurrentHashMap<>();
    private AvroSchemaParser<U> parser;
    private AvroDatumProvider<U> avroDatumProvider;
    private AvroEncoding encoding;
    private boolean validateWriterSchemaEnabled = AvroSerdeConfig.AVRO_VALIDATE_WRITER_SCHEMA_DEFAULT;

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
        validateWriterSchemaEnabled = config.validateWriterSchema();

        Class<?> adp = config.getAvroDatumProvider();
        Consumer<AvroDatumProvider> consumer = this::setAvroDatumProvider;
        Utils.instantiate(AvroDatumProvider.class, adp, consumer);
        avroDatumProvider.configure(config);

        // important to instantiate the SchemaParser before calling super.configure
        parser = new AvroSchemaParser<>(avroDatumProvider, config.getSchemaCacheSize());

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
        Schema dataSchema = (data instanceof GenericContainer) ? ((GenericContainer) data).getSchema()
            : null;

        if (data instanceof NonRecordContainer) {
            // noinspection unchecked
            data = (U) NonRecordContainer.class.cast(data).getValue();
        }

        if (validateWriterSchemaEnabled) {
            if (dataSchema == null && schema.getParsedSchema().getType() == Schema.Type.RECORD) {
                // Datums that do not carry a schema, such as reflect provider POJOs
                dataSchema = avroDatumProvider.toSchema(data);
            }
            if (dataSchema != null) {
                validateWriterSchema(dataSchema, schema.getParsedSchema());
            }
        }

        Encoder encoder = createEncoder(schema.getParsedSchema(), out);
        DatumWriter<U> writer = avroDatumProvider.createDatumWriter(data, schema.getParsedSchema());
        writer.write(data, encoder);
        encoder.flush();
    }

    /**
     * The Avro datum writer extracts record values by field position of the writer schema, so a
     * resolved schema that differs structurally from the record's schema silently writes values
     * under the wrong fields, or writes null into optional fields. Schemas are compared by parsing
     * canonical form: properties such as connect.* metadata do not affect the binary layout and are
     * therefore tolerated. Successfully validated schema pairs are remembered by identity, so
     * producers reusing schema instances (the common case, including one serializer shared across
     * several event types) pay only a lock-free cache lookup per record.
     */
    private void validateWriterSchema(Schema recordSchema, Schema writerSchema) {
        if (recordSchema == writerSchema) {
            return;
        }
        ValidatedSchemaPair pair = new ValidatedSchemaPair(recordSchema, writerSchema);
        if (validatedSchemaPairs.containsKey(pair)) {
            return;
        }
        if (!SchemaNormalization.toParsingForm(recordSchema)
                .equals(SchemaNormalization.toParsingForm(writerSchema))) {
            throw new IllegalStateException(String.format(
                    "The schema resolved from the registry does not structurally match the schema of the "
                            + "record being serialized [%s]. Writing the record with the resolved schema "
                            + "would silently misalign field values. This usually means the artifact's "
                            + "latest version is out of sync with the data producer, for example when "
                            + "%s=true is used without %s=true. Set %s=false to restore the previous "
                            + "behavior.",
                    recordSchema.getFullName(), SchemaResolverConfig.FIND_LATEST_ARTIFACT,
                    SchemaResolverConfig.AUTO_REGISTER_ARTIFACT,
                    AvroSerdeConfig.AVRO_VALIDATE_WRITER_SCHEMA));
        }
        // Crude bound: validation is cheap to redo, so clearing beats LRU bookkeeping on the hot path.
        if (validatedSchemaPairs.size() >= VALIDATED_SCHEMA_PAIRS_MAX_SIZE) {
            validatedSchemaPairs.clear();
        }
        validatedSchemaPairs.put(pair, Boolean.TRUE);
    }

    /**
     * Cache key comparing schemas by identity. Schema instances are stable per resolved version and
     * per producer, and identity comparison avoids Avro's deep Schema.equals on the hot path.
     */
    private static final class ValidatedSchemaPair {

        private final Schema recordSchema;
        private final Schema writerSchema;

        private ValidatedSchemaPair(Schema recordSchema, Schema writerSchema) {
            this.recordSchema = recordSchema;
            this.writerSchema = writerSchema;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ValidatedSchemaPair)) {
                return false;
            }
            ValidatedSchemaPair other = (ValidatedSchemaPair) o;
            return recordSchema == other.recordSchema && writerSchema == other.writerSchema;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(recordSchema) * 31 + System.identityHashCode(writerSchema);
        }
    }

    private Encoder createEncoder(Schema schema, OutputStream os) throws IOException {
        if (encoding == AvroEncoding.JSON) {
            return encoderFactory.jsonEncoder(schema, os);
        } else {
            return encoderFactory.directBinaryEncoder(os, null);
        }
    }
}
