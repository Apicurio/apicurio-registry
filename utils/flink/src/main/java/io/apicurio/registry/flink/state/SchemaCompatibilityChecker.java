package io.apicurio.registry.flink.state;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks schema compatibility for state evolution.
 *
 * <p>
 * Uses Avro's built-in compatibility checking to validate that schema
 * changes are safe for state evolution. Supports BACKWARD, FORWARD, and
 * FULL compatibility modes.
 */
public final class SchemaCompatibilityChecker {

    /** The compatibility mode. */
    private final CompatibilityMode mode;

    /**
     * Creates a checker with the specified mode.
     *
     * @param compatibilityMode the mode
     */
    public SchemaCompatibilityChecker(
            final CompatibilityMode compatibilityMode) {
        this.mode = compatibilityMode;
    }

    /**
     * Creates a checker with BACKWARD compatibility.
     *
     * @return the checker
     */
    public static SchemaCompatibilityChecker backward() {
        return new SchemaCompatibilityChecker(CompatibilityMode.BACKWARD);
    }

    /**
     * Creates a checker with FORWARD compatibility.
     *
     * @return the checker
     */
    public static SchemaCompatibilityChecker forward() {
        return new SchemaCompatibilityChecker(CompatibilityMode.FORWARD);
    }

    /**
     * Creates a checker with FULL compatibility.
     *
     * @return the checker
     */
    public static SchemaCompatibilityChecker full() {
        return new SchemaCompatibilityChecker(CompatibilityMode.FULL);
    }

    /**
     * Checks compatibility between two Avro schemas.
     *
     * @param oldSchemaStr the old schema (from savepoint)
     * @param newSchemaStr the new schema (current application)
     * @return the compatibility result
     */
    public CompatibilityResult checkAvroCompatibility(
            final String oldSchemaStr,
            final String newSchemaStr) {
        if (mode == CompatibilityMode.NONE) {
            return CompatibilityResult.compatible();
        }

        final Schema oldSchema = new Schema.Parser().parse(oldSchemaStr);
        final Schema newSchema = new Schema.Parser().parse(newSchemaStr);

        return checkAvroCompatibility(oldSchema, newSchema);
    }

    /**
     * Checks compatibility between two Avro schemas.
     *
     * @param oldSchema the old schema
     * @param newSchema the new schema
     * @return the compatibility result
     */
    public CompatibilityResult checkAvroCompatibility(
            final Schema oldSchema,
            final Schema newSchema) {
        if (mode == CompatibilityMode.NONE) {
            return CompatibilityResult.compatible();
        }

        final List<String> diffs = new ArrayList<>();
        boolean backwardOk = true;
        boolean forwardOk = true;

        if (mode == CompatibilityMode.BACKWARD
                || mode == CompatibilityMode.FULL) {
            final SchemaPairCompatibility backward = SchemaCompatibility
                            .checkReaderWriterCompatibility(
                    newSchema, oldSchema);
            if (backward.getType() != SchemaCompatibilityType.COMPATIBLE) {
                backwardOk = false;
                diffs.add("BACKWARD: " + backward.getDescription());
            }
        }

        if (mode == CompatibilityMode.FORWARD
                || mode == CompatibilityMode.FULL) {
            final SchemaPairCompatibility forward = SchemaCompatibility
                            .checkReaderWriterCompatibility(
                    oldSchema, newSchema);
            if (forward.getType() != SchemaCompatibilityType.COMPATIBLE) {
                forwardOk = false;
                diffs.add("FORWARD: " + forward.getDescription());
            }
        }

        if (backwardOk && forwardOk) {
            return CompatibilityResult.compatible();
        }

        if (canMigrate(oldSchema, newSchema)) {
            return CompatibilityResult.needsMigration(
                    "Schema change requires migration",
                    diffs);
        }

        return CompatibilityResult.incompatible(
                "Schema change is incompatible",
                diffs);
    }

    /**
     * Checks if schemas can be migrated.
     *
     * @param oldSchema the old schema
     * @param newSchema the new schema
     * @return true if migration is possible
     */
    private boolean canMigrate(
            final Schema oldSchema,
            final Schema newSchema) {
        if (oldSchema.getType() != newSchema.getType()) {
            return false;
        }
        if (oldSchema.getType() != Schema.Type.RECORD) {
            return false;
        }
        return true;
    }

    /**
     * Gets the compatibility mode.
     *
     * @return the mode
     */
    public CompatibilityMode getMode() {
        return mode;
    }
}
