package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.resolver.ParsedSchema;

import java.io.IOException;
public class JsonSchemaValidationUtil {

    /**
     * @param schema the schema to test the data.
     * @param data the data to test.
     * @param mapper the object mapper to be used to read the data.
     * @throws IOException In case of validation errors, a IO exception is thrown.
     */
    protected static void validateDataWithSchema(ParsedSchema<JsonSchema> schema, byte[] data, ObjectMapper mapper) throws IOException {
        schema.getParsedSchema().validate(mapper.readTree(data));
    }
}
