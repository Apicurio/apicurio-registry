package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;

import java.nio.ByteBuffer;

public class AvroPulsarDeserializer<U> extends AbstractDeserializer<Schema, U> {

    private AvroDeserializer<U> avroDeserializer;

    public AvroPulsarDeserializer() {
        super();
    }

    public AvroPulsarDeserializer(RegistryClient client) {
        super(client);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(SerdeConfig configs, boolean isKey) {
        AvroSerdeConfig avroSerdeConfig = new AvroSerdeConfig(configs.originals());
        this.avroDeserializer = new AvroDeserializer<>();
        if (getSchemaResolver() != null) {
            this.avroDeserializer.setSchemaResolver(getSchemaResolver());
        }
        avroDeserializer.configure(avroSerdeConfig, isKey);

        super.configure(avroSerdeConfig, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractSerDe#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return avroDeserializer.schemaParser();
    }

    @Override
    protected U readData(ParsedSchema<Schema> schema, ByteBuffer buffer, int start, int length) {
        return avroDeserializer.readData(schema, buffer, start, length);
    }
}
