package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import io.apicurio.registry.resolver.ParsedSchema;

import java.io.IOException;
import java.util.Set;
/**
 * @author Carles Arnal
*/
public class JsonSchemaValidationUtil {

    /**
     * @param schema the schema to test the data.
     * @param data the data to test.
     * @param mapper the object mapper to be used to read the data.
     * @throws IOException In case of validation errors, a IO exception is thrown.
     */
    protected static void validateDataWithSchema(ParsedSchema<JsonSchema> schema, byte[] data, ObjectMapper mapper) throws IOException {
        final Set<ValidationMessage> validationMessages = schema.getParsedSchema().validate(mapper.readTree(data));
        if (validationMessages != null && !validationMessages.isEmpty()) {
            //There are validation failures
            StringBuilder message = new StringBuilder();
            for (ValidationMessage validationMessage: validationMessages) {
                message.append(validationMessage.getMessage()).append(" ");
            }
            throw new IOException(String.format("Error validating data against json schema with message: %s", message));
        }
    }
}
