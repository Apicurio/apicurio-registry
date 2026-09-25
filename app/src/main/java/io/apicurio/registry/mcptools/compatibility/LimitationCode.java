package io.apicurio.registry.mcptools.compatibility;

/**
 * Why a comparison cannot fully decide whether two tools are compatible.
 */
public enum LimitationCode {

    /**
     * The schema declares a {@code $schema} dialect that is not supported.
     */
    UNSUPPORTED_DIALECT(true),

    /**
     * The schema uses a keyword that the comparison does not evaluate yet.
     */
    UNSUPPORTED_KEYWORD(true),

    /**
     * The verdict depends on the producer emitting only the properties it declares.
     */
    PRODUCER_OBJECT_OPEN(false),

    /**
     * The schema nests structure deeper than the comparison evaluates.
     */
    DEPTH_LIMIT_REACHED(true),

    /**
     * A schema could not be read or compared.
     */
    COMPARISON_FAILED(false),

    /**
     * The producer tool declares no {@code outputSchema}.
     */
    SOURCE_HAS_NO_OUTPUT_SCHEMA(false);

    private final boolean coversSubtree;

    LimitationCode(boolean coversSubtree) {
        this.coversSubtree = coversSubtree;
    }

    /**
     * Whether a limitation with this code invalidates the mismatches found at or below the
     * schema node that carries it.
     */
    public boolean coversSubtree() {
        return coversSubtree;
    }
}
