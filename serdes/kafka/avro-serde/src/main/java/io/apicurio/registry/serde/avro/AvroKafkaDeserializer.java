package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.kafka.common.header.Headers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class AvroKafkaDeserializer<U> extends AbstractKafkaDeserializer<Schema, U> {

    private final DecoderFactory decoderFactory = DecoderFactory.get();
    private AvroSchemaParser<U> parser;
    private AvroDatumProvider<U> avroDatumProvider;
    private AvroEncoding configEncoding;
    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaDeserializer() {
        super();
    }

    public AvroKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    private AvroKafkaDeserializer<U> setAvroDatumProvider(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = Objects.requireNonNull(avroDatumProvider);
        return this;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        AvroKafkaSerdeConfig config = new AvroKafkaSerdeConfig(configs);
        configEncoding = config.getAvroEncoding();

        Class adp = config.getAvroDatumProvider();
        Consumer<AvroDatumProvider> consumer = this::setAvroDatumProvider;
        Utils.instantiate(AvroDatumProvider.class, adp, consumer);
        avroDatumProvider.configure(config);

        avroHeaders = new AvroSerdeHeaders(isKey);

        // important to instantiate the SchemaParser before calling super.configure
        parser = new AvroSchemaParser<>(avroDatumProvider);

        super.configure(config, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return parser;
    }

    @Override
    protected U readData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        return readData(null, schema, buffer, start, length);
    }

    @Override
    protected U readData(Headers headers, ParsedSchema<Schema> schema, ByteBuffer buffer, int start,
            int length) {
        AvroEncoding encoding = null;
        if (headers != null) {
            String encodingHeader = avroHeaders.getEncoding(headers);
            if (encodingHeader != null) {
                encoding = AvroEncoding.valueOf(encodingHeader);
            }
        }
        if (encoding == null) {
            // no encoding in header or no headers so use config
            encoding = configEncoding;
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
}
