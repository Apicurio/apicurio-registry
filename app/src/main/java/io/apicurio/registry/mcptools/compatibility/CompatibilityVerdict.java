package io.apicurio.registry.mcptools.compatibility;

/**
 * Whether the output of one MCP tool can be passed to the input of another.
 */
public enum CompatibilityVerdict {

    /**
     * No mismatch was found and no limitation affects either schema.
     */
    COMPATIBLE,

    /**
     * At least one mismatch was found that no limitation invalidates.
     */
    INCOMPATIBLE,

    /**
     * No mismatch stands, but a limitation prevents a positive verdict.
     */
    INDETERMINATE
}
