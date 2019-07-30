package io.apicurio.registry.types;

import org.apache.avro.Schema;

/**
 * @author Ales Justin
 */
public class AvroSchemaTypeAdapter implements SchemaTypeAdapter {
    @Override
    public SchemaWrapper wrapper(String schemaString) {
        Schema.Parser parser = new Schema.Parser();
        Schema schema = parser.parse(schemaString);
        return new SchemaWrapper(schema, schema.toString());
    }
}
