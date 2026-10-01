package io.apicurio.registry.mcptools.compatibility;

/**
 * The tool schema a pointer or limitation refers to.
 */
public enum SchemaSide {

    /**
     * The {@code outputSchema} of the tool whose output is passed on.
     */
    PRODUCER,

    /**
     * The {@code inputSchema} of the tool that receives the output.
     */
    CONSUMER
}
