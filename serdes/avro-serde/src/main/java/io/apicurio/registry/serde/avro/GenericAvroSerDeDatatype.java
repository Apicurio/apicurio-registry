package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.serde.generic.GenericConfig;
import io.apicurio.registry.serde.generic.GenericSerDeDatatype;
import lombok.Setter;
import org.apache.avro.Schema;
import org.apache.avro.io.*;
import org.apache.kafka.common.header.Headers;

import java.io.*;
import java.nio.ByteBuffer;

import static io.apicurio.registry.serde.generic.Utils.castOr;

public class GenericAvroSerDeDatatype<DATA> implements GenericSerDeDatatype<Schema, DATA> {

    private final EncoderFactory encoderFactory = EncoderFactory.get();
    private final DecoderFactory decoderFactory = DecoderFactory.get();

    @Setter
    private AvroDatumProvider<DATA> avroDatumProvider;

    private SchemaParser<Schema, DATA> parser;

    private AvroEncoding encoding;

    private AvroSerdeHeaders avroHeaders;


    @Override
    public void configure(GenericConfig config) {
        var config2 = castOr(config, GenericAvroSerDeConfig.class, () -> new GenericAvroSerDeConfig(config));

        encoding = config2.getAvroEncoding();

        avroDatumProvider = (AvroDatumProvider<DATA>) config2.getAvroDatumProvider();
        avroDatumProvider.configure(new AvroKafkaSerdeConfig(config.getRawConfig())); // TODO

        avroHeaders = new AvroSerdeHeaders(config2.isKey());

        //important to instantiate the SchemaParser before calling super.configure
        parser = new AvroSchemaParser<>(avroDatumProvider);
    }


    @Override
    public void writeData(Headers headers, ParsedSchema<Schema> schema, DATA data, OutputStream out) throws Exception {
        if (headers != null) {
            avroHeaders.addEncodingHeader(headers, encoding.name());
        }

        var encoder = createEncoder(schema.getParsedSchema(), out);

        // TODO What is NonRecordContainer?
        // I guess this can happen if generics are lost with reflection ...
        if (data instanceof NonRecordContainer) {
            // noinspection unchecked
            data = (DATA) ((NonRecordContainer) data).getValue();
        }

        DatumWriter<DATA> writer = avroDatumProvider.createDatumWriter(data, schema.getParsedSchema());
        writer.write(data, encoder);
        encoder.flush();
    }


    @Override
    public DATA readData(Headers headers, ParsedSchema<Schema> schema, ByteBuffer buffer) {
        AvroEncoding encoding = null;
        if (headers != null){
            String encodingHeader = avroHeaders.getEncoding(headers);
            if (encodingHeader != null) {
                encoding = AvroEncoding.valueOf(encodingHeader);
            }
        }
        if (encoding == null) {
            // no encoding in header or no headers so use config
            encoding = this.encoding;
        }
        try {
            DatumReader<DATA> reader = avroDatumProvider.createDatumReader(schema.getParsedSchema());

            byte[] msgData = new byte[buffer.remaining()];
            buffer.get(msgData);

            if( encoding == AvroEncoding.JSON) {
                return reader.read(null, decoderFactory.jsonDecoder(schema.getParsedSchema(), new ByteArrayInputStream(msgData)));
            } else {
                return reader.read(null, decoderFactory.binaryDecoder(msgData, null));
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private Encoder createEncoder(Schema schema, OutputStream os) throws IOException {
        if(encoding == AvroEncoding.JSON) {
            return encoderFactory.jsonEncoder(schema, os);
        } else {
            return encoderFactory.directBinaryEncoder(os, null);
        }
    }


    @Override
    public SchemaParser<Schema, DATA> getSchemaParser() {
        return parser;
    }
}
