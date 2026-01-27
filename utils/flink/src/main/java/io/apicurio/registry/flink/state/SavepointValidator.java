package io.apicurio.registry.flink.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates state schemas against savepoint for compatibility.
 *
 * <p>
 * Compares the schemas stored in the registry (from previous job runs)
 * with the current application schemas to detect incompatible state
 * evolution before restarting a job from a savepoint.
 */
public final class SavepointValidator {

    /** The state schema service. */
    private final StateSchemaService stateService;

    /** The compatibility checker. */
    private final SchemaCompatibilityChecker checker;

    /**
     * Creates a validator with default BACKWARD compatibility.
     *
     * @param service the state schema service
     */
    public SavepointValidator(final StateSchemaService service) {
        this(service, SchemaCompatibilityChecker.backward());
    }

    /**
     * Creates a validator with custom checker.
     *
     * @param service       the state schema service
     * @param compatChecker the compatibility checker
     */
    public SavepointValidator(
            final StateSchemaService service,
            final SchemaCompatibilityChecker compatChecker) {
        this.stateService = service;
        this.checker = compatChecker;
    }

    /**
     * Validates current schemas against registered state schemas.
     *
     * <p>
     * For each operator with a registered state schema, this method
     * compares the registered schema with the current application schema.
     *
     * @param currentSchemas map of operatorId to current schema
     * @return the validation result
     */
    public ValidationResult validate(
            final Map<String, String> currentSchemas) {
        final List<String> compatible = new ArrayList<>();
        final Map<String, String> incompatible = new HashMap<>();

        for (Map.Entry<String, String> entry : currentSchemas.entrySet()) {
            final String operatorId = entry.getKey();
            final String currentSchema = entry.getValue();

            if (!stateService.hasStateSchema(operatorId)) {
                compatible.add(operatorId);
                continue;
            }

            try {
                final String savedSchema =
                        stateService.getLatestStateSchema(operatorId);
                final CompatibilityResult result =
                        checker.checkAvroCompatibility(
                        savedSchema, currentSchema);

                if (result.isCompatible()) {
                    compatible.add(operatorId);
                } else {
                    incompatible.put(
                            operatorId,
                            result.getMessage() + ": "
                                    + result.getDifferences());
                }
            } catch (Exception e) {
                incompatible.put(operatorId,
                        "Failed to check: " + e.getMessage());
            }
        }

        if (incompatible.isEmpty()) {
            return ValidationResult.valid(compatible);
        }
        return ValidationResult.invalid(compatible, incompatible);
    }

    /**
     * Validates a single operator's schema.
     *
     * @param operatorId    the operator ID
     * @param currentSchema the current schema
     * @return the compatibility result
     */
    public CompatibilityResult validateOperator(
            final String operatorId,
            final String currentSchema) {
        if (!stateService.hasStateSchema(operatorId)) {
            return CompatibilityResult.compatible();
        }

        final String savedSchema =
                        stateService.getLatestStateSchema(operatorId);
        return checker.checkAvroCompatibility(
                savedSchema, currentSchema);
    }

    /**
     * Registers current schemas and returns validation result.
     *
     * <p>
     * This is a convenience method that validates and then registers
     * the schemas if validation passes.
     *
     * @param currentSchemas map of operatorId to current schema
     * @return the validation result
     */
    public ValidationResult validateAndRegister(
            final Map<String, String> currentSchemas) {
        final ValidationResult result = validate(currentSchemas);

        if (result.isValid()) {
            for (Map.Entry<String, String> entry : currentSchemas.entrySet()) {
                stateService.registerStateSchema(
                        entry.getKey(), entry.getValue());
            }
        }

        return result;
    }
}
