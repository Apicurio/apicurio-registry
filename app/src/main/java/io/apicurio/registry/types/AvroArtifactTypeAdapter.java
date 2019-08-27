package io.apicurio.registry.types;

import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class AvroArtifactTypeAdapter implements ArtifactTypeAdapter {
    @Override
    public ArtifactWrapper wrapper(String schemaString) {
        Schema.Parser parser = new Schema.Parser();
        Schema schema = parser.parse(schemaString);
        return new ArtifactWrapper(schema, schema.toString());
    }

    @Override
    public boolean isCompatibleWith(String compatibilityLevel, List<String> existingSchemaStrings, String proposedSchemaString) {
        SchemaValidator schemaValidator = validatorFor(compatibilityLevel);

        if (schemaValidator == null) {
            return true;
        }

        List<Schema> existingSchemas = existingSchemaStrings.stream().map(s -> new Schema.Parser().parse(s)).collect(Collectors.toList());
        Collections.reverse(existingSchemas); // the most recent must come first, i.e. reverse-chronological.
        Schema toValidate = new Schema.Parser().parse(proposedSchemaString);

        try {
            schemaValidator.validate(toValidate, existingSchemas);
            return true;
        } catch (SchemaValidationException e) {
            return false;
        }
    }

    private SchemaValidator validatorFor(String compatibilityLevel) {
        switch (compatibilityLevel) {
            case "BACKWARD":
                return new SchemaValidatorBuilder().canReadStrategy().validateLatest();
            case "BACKWARD_TRANSITIVE":
                return new SchemaValidatorBuilder().canReadStrategy().validateAll();
            case "FORWARD":
                return new SchemaValidatorBuilder().canBeReadStrategy().validateLatest();
            case "FORWARD_TRANSITIVE":
                return new SchemaValidatorBuilder().canBeReadStrategy().validateAll();
            case "FULL":
                return new SchemaValidatorBuilder().mutualReadStrategy().validateLatest();
            case "FULL_TRANSITIVE":
                return new SchemaValidatorBuilder().mutualReadStrategy().validateAll();
            default:
                return null;
        }

    }
}
