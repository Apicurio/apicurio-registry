package io.apicurio.registry.rules.validity;

/**
 * Defines the level of validation performed by the content validity rule when new content is added to an
 * artifact. Validity checking ensures that content is well-formed and correct for its artifact type.
 */
public enum ValidityLevel {

    /**
     * No validity checking is performed.
     */
    NONE,

    /**
     * Only syntactic validity is checked (e.g. the content is valid JSON, valid XML, parseable Protobuf).
     */
    SYNTAX_ONLY,

    /**
     * Full validation is performed, including both syntactic and semantic checks (e.g. verifying that a JSON
     * Schema document conforms to the JSON Schema specification, or that an Avro schema is semantically
     * valid).
     */
    FULL;

}
