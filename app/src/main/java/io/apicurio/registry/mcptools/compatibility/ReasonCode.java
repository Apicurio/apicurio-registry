package io.apicurio.registry.mcptools.compatibility;

/**
 * Why a producer's output is not accepted by a consumer's input.
 */
public enum ReasonCode {

    /**
     * The consumer requires an input that the producer does not guarantee to emit.
     */
    REQUIRED_NOT_GUARANTEED,

    /**
     * The producer can emit a value that the consumer does not accept.
     */
    TYPE_NOT_ACCEPTED,

    /**
     * The producer can emit a property that the consumer's {@code additionalProperties} rejects.
     */
    ADDITIONAL_PROPERTY_NOT_ACCEPTED
}
