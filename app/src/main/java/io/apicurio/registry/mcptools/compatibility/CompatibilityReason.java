package io.apicurio.registry.mcptools.compatibility;

/**
 * A mismatch between a producer's {@code outputSchema} and a consumer's {@code inputSchema}.
 *
 * @param code what kind of mismatch this is
 * @param producerPointer JSON Pointer into the producer tool document
 * @param consumerPointer JSON Pointer into the consumer tool document
 * @param message human readable description of the mismatch
 */
public record CompatibilityReason(ReasonCode code, String producerPointer, String consumerPointer,
        String message) {
}
