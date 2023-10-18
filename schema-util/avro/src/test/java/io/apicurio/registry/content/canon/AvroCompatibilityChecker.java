package io.apicurio.registry.content.canon;

import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AvroCompatibilityChecker {
    public static final AvroCompatibilityChecker BACKWARD_CHECKER;
    public static final AvroCompatibilityChecker FORWARD_CHECKER;
    public static final AvroCompatibilityChecker FULL_CHECKER;
    public static final AvroCompatibilityChecker BACKWARD_TRANSITIVE_CHECKER;
    public static final AvroCompatibilityChecker FORWARD_TRANSITIVE_CHECKER;
    public static final AvroCompatibilityChecker FULL_TRANSITIVE_CHECKER;
    public static final AvroCompatibilityChecker NO_OP_CHECKER;
    private static final SchemaValidator BACKWARD_VALIDATOR = (new SchemaValidatorBuilder()).canReadStrategy().validateLatest();
    private static final SchemaValidator FORWARD_VALIDATOR;
    private static final SchemaValidator FULL_VALIDATOR;
    private static final SchemaValidator BACKWARD_TRANSITIVE_VALIDATOR;
    private static final SchemaValidator FORWARD_TRANSITIVE_VALIDATOR;
    private static final SchemaValidator FULL_TRANSITIVE_VALIDATOR;
    private static final SchemaValidator NO_OP_VALIDATOR;

    static {
        BACKWARD_CHECKER = new AvroCompatibilityChecker(BACKWARD_VALIDATOR);
        FORWARD_VALIDATOR = (new SchemaValidatorBuilder()).canBeReadStrategy().validateLatest();
        FORWARD_CHECKER = new AvroCompatibilityChecker(FORWARD_VALIDATOR);
        FULL_VALIDATOR = (new SchemaValidatorBuilder()).mutualReadStrategy().validateLatest();
        FULL_CHECKER = new AvroCompatibilityChecker(FULL_VALIDATOR);
        BACKWARD_TRANSITIVE_VALIDATOR = (new SchemaValidatorBuilder()).canReadStrategy().validateAll();
        BACKWARD_TRANSITIVE_CHECKER = new AvroCompatibilityChecker(BACKWARD_TRANSITIVE_VALIDATOR);
        FORWARD_TRANSITIVE_VALIDATOR = (new SchemaValidatorBuilder()).canBeReadStrategy().validateAll();
        FORWARD_TRANSITIVE_CHECKER = new AvroCompatibilityChecker(FORWARD_TRANSITIVE_VALIDATOR);
        FULL_TRANSITIVE_VALIDATOR = (new SchemaValidatorBuilder()).mutualReadStrategy().validateAll();
        FULL_TRANSITIVE_CHECKER = new AvroCompatibilityChecker(FULL_TRANSITIVE_VALIDATOR);
        NO_OP_VALIDATOR = new SchemaValidator() {
            public void validate(Schema schema, Iterable<Schema> schemas) throws SchemaValidationException {
            }
        };
        NO_OP_CHECKER = new AvroCompatibilityChecker(NO_OP_VALIDATOR);
    }

    private final SchemaValidator validator;

    private AvroCompatibilityChecker(SchemaValidator validator) {
        this.validator = validator;
    }

    public boolean isCompatible(Schema newSchema, Schema latestSchema) {
        return this.isCompatible(newSchema, Collections.singletonList(latestSchema));
    }

    public boolean isCompatible(Schema newSchema, List<Schema> previousSchemas) {
        List<Schema> previousSchemasCopy = new ArrayList<>(previousSchemas);

        try {
            Collections.reverse(previousSchemasCopy);
            this.validator.validate(newSchema, previousSchemasCopy);
            return true;
        } catch (SchemaValidationException var5) {
            return false;
        }
    }
}