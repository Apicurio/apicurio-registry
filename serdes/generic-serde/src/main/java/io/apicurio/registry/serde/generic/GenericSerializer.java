package io.apicurio.registry.serde.generic;

import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;
import org.apache.kafka.common.header.Headers;

import java.io.ByteArrayOutputStream;

public class GenericSerializer<SCHEMA, DATA> extends GenericSerDe<SCHEMA, DATA> {


    /**
     * @param serDeDatatype  required
     * @param schemaResolver may be null
     */
    public GenericSerializer(GenericSerDeDatatype<SCHEMA, DATA> serDeDatatype, SchemaResolver<SCHEMA, DATA> schemaResolver) {
        super(serDeDatatype, schemaResolver);
    }


    public byte[] serialize(String topic, Headers headers, DATA data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {

            KafkaSerdeMetadata resolverMetadata = new KafkaSerdeMetadata(topic, serDeConfig.isKey(), headers);

            SchemaLookupResult<SCHEMA> schema = getSchemaResolver().resolveSchema(new KafkaSerdeRecord<>(resolverMetadata, data));

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            if (headersHandler != null && headers != null) {
                headersHandler.writeHeaders(headers, schema.toArtifactReference());
                serDeDatatype.writeData(headers, schema.getParsedSchema(), data, out);
            } else {
                out.write(MAGIC_BYTE);
                getIdHandler().writeId(schema.toArtifactReference(), out);
                serDeDatatype.writeData(null, schema.getParsedSchema(), data, out);
            }
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
