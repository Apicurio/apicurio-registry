package io.apicurio.registry.rules.compatibility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class AvroArtifactTypeAdapter implements ArtifactTypeAdapter {

    @Override
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemaStrings, String proposedSchemaString) {
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

    private SchemaValidator validatorFor(CompatibilityLevel compatibilityLevel) {
        switch (compatibilityLevel) {
            case BACKWARD:
                return new SchemaValidatorBuilder().canReadStrategy().validateLatest();
            case BACKWARD_TRANSITIVE:
                return new SchemaValidatorBuilder().canReadStrategy().validateAll();
            case FORWARD:
                return new SchemaValidatorBuilder().canBeReadStrategy().validateLatest();
            case FORWARD_TRANSITIVE:
                return new SchemaValidatorBuilder().canBeReadStrategy().validateAll();
            case FULL:
                return new SchemaValidatorBuilder().mutualReadStrategy().validateLatest();
            case FULL_TRANSITIVE:
                return new SchemaValidatorBuilder().mutualReadStrategy().validateAll();
            default:
                return null;
        }

    }
}
