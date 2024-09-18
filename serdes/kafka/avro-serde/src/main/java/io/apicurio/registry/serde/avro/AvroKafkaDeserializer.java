package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.util.Map;

public class AvroKafkaDeserializer<U> extends AbstractKafkaDeserializer<Schema, U> {

    private AvroSerdeHeaders avroHeaders;
    private AvroDeserializer<U> avroDeserializer;

    public AvroKafkaDeserializer() {
        super();
    }

    public AvroKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        avroHeaders = new AvroSerdeHeaders(isKey);
        AvroSerdeConfig avroSerdeConfig = new AvroSerdeConfig(configs);
        this.avroDeserializer = new AvroDeserializer<>();
        if (getSchemaResolver() != null) {
            this.avroDeserializer.setSchemaResolver(getSchemaResolver());
        }
        avroDeserializer.configure(avroSerdeConfig, isKey);

        super.configure(configs, isKey);
    }

    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return avroDeserializer.schemaParser();
    }

    @Override
    protected U readData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        return avroDeserializer.readData(schema, buffer, start, length);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        AvroEncoding encoding = null;
        if (headers != null) {
            String encodingHeader = avroHeaders.getEncoding(headers);
            if (encodingHeader != null) {
                encoding = AvroEncoding.valueOf(encodingHeader);
            }
        }
        if (encoding != null) {
            avroDeserializer.setEncoding(encoding);
        }

        return super.deserialize(topic, headers, data);
    }
}
