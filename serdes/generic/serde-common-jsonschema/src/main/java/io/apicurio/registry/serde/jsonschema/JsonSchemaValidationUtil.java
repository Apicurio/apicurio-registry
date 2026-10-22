package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import io.apicurio.registry.resolver.ParsedSchema;

import java.io.IOException;
import java.util.Set;

/**
 * Utility class for validating JSON data against JSON schemas.
 *
 * @author Carles Arnal
 */
public class JsonSchemaValidationUtil {

    /**
     * Validates the data against the schema by parsing the byte array to a JsonNode.
     * This method parses the data internally and should be avoided when the data
     * has already been parsed to a JsonNode.
     *
     * @param schema the schema to test the data against
     * @param data the data to test as a byte array
     * @param mapper the object mapper to be used to read the data
     * @throws IOException if validation errors occur or if the data cannot be parsed
     */
    protected static void validateDataWithSchema(ParsedSchema<JsonSchema> schema, byte[] data,
            ObjectMapper mapper) throws IOException {
        final JsonNode jsonNode = mapper.readTree(data);
        validateDataWithSchema(schema, jsonNode);
    }

    /**
     * Validates a JsonNode against the schema. This method is more efficient when the data
     * has already been parsed to a JsonNode, as it avoids redundant parsing.
     *
     * @param schema the schema to test the data against
     * @param jsonNode the parsed JSON data to validate
     * @throws IOException if validation errors occur
     */
    protected static void validateDataWithSchema(ParsedSchema<JsonSchema> schema, JsonNode jsonNode)
            throws IOException {
        final Set<ValidationMessage> validationMessages = schema.getParsedSchema()
                .validate(jsonNode);
        if (validationMessages != null && !validationMessages.isEmpty()) {
            // There are validation failures
            StringBuilder message = new StringBuilder();
            for (ValidationMessage validationMessage : validationMessages) {
                message.append(validationMessage.getMessage()).append(" ");
            }
            throw new IOException(
                    String.format("Error validating data against json schema with message: %s", message));
        }
    }
}
