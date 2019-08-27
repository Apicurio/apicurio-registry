package io.apicurio.registry.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class JsonArtifactTypeAdapter implements ArtifactTypeAdapter {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ArtifactWrapper wrapper(String schemaString) {
        try {
            JsonNode node = objectMapper.readTree(schemaString);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance();
            JsonSchema jsonSchema = factory.getSchema(node);
            String reconstitutedCanonicalForm = objectMapper.writeValueAsString(node);
            return new ArtifactWrapper(jsonSchema, reconstitutedCanonicalForm);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isCompatibleWith(String compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        return existingSchemas.isEmpty() || existingSchemas.get(0).equals(proposedSchema);
    }
}
