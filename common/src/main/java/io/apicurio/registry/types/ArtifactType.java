package io.apicurio.registry.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines the supported artifact types in the registry. The artifact type identifies the format or schema
 * language of an artifact's content and determines how the registry parses, validates, and checks
 * compatibility of that content.
 */
@SuppressWarnings("java:S1133")
public sealed interface ArtifactType permits ArtifactType.BuiltIn, ArtifactType.Custom {

    int CUSTOM_NUMERIC_ID = -1;

    /**
     * @return the string value of the artifact type
     */
    @JsonValue
    String value();

    /**
     * @return the numeric identifier for this artifact type, or CUSTOM_NUMERIC_ID if none.
     */
    int numericId();

    @JsonCreator
    static ArtifactType fromValue(String value) {
        if (value == null) {
            return null;
        }
        ArtifactType builtIn = BuiltIn.CACHE.get(value);
        if (builtIn != null) {
            return builtIn;
        }
        return new Custom(value);
    }

    enum BuiltIn implements ArtifactType {
        AVRO("AVRO", 1),
        PROTOBUF("PROTOBUF", 2),
        JSON("JSON", 3),
        OPENAPI("OPENAPI", 4),
        ASYNCAPI("ASYNCAPI", 5),
        GRAPHQL("GRAPHQL", 6),
        KCONNECT("KCONNECT", 7),
        WSDL("WSDL", 8),
        XSD("XSD", 9),
        XML("XML", 10),
        AGENT_CARD("AGENT_CARD", 11),
        MCP_TOOL("MCP_TOOL", 12),
        ICEBERG_TABLE("ICEBERG_TABLE", 13),
        ICEBERG_VIEW("ICEBERG_VIEW", 14),
        OPENRPC("OPENRPC", 15),
        MODEL_SCHEMA("MODEL_SCHEMA", 16),
        PROMPT_TEMPLATE("PROMPT_TEMPLATE", 17),
        ODCS_CONTRACT("ODCS_CONTRACT", 18),
        THRIFT("THRIFT", 19);

        private final String value;
        private final int numericId;

        BuiltIn(String value, int numericId) {
            this.value = value;
            this.numericId = numericId;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public int numericId() {
            return numericId;
        }

        private static final Map<String, BuiltIn> CACHE;
        static {
            Map<String, BuiltIn> map = new HashMap<>();
            for (BuiltIn type : values()) {
                map.put(type.value(), type);
            }
            CACHE = Collections.unmodifiableMap(map);
        }
    }

    record Custom(String value) implements ArtifactType {
        public Custom {
            Objects.requireNonNull(value, "Artifact type value cannot be null");
            if (BuiltIn.CACHE.containsKey(value)) {
                throw new IllegalArgumentException("Cannot create Custom artifact type for BuiltIn type: " + value);
            }
        }

        @Override
        public int numericId() {
            return CUSTOM_NUMERIC_ID;
        }
    }

    /** Apache Avro schema. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String AVRO = BuiltIn.AVRO.value();

    /** Google Protocol Buffers definition. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String PROTOBUF = BuiltIn.PROTOBUF.value();

    /** JSON Schema. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String JSON = BuiltIn.JSON.value();

    /** OpenAPI specification. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String OPENAPI = BuiltIn.OPENAPI.value();

    /** AsyncAPI specification. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String ASYNCAPI = BuiltIn.ASYNCAPI.value();

    /** GraphQL schema definition language (SDL). 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String GRAPHQL = BuiltIn.GRAPHQL.value();

    /** Apache Kafka Connect schema. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String KCONNECT = BuiltIn.KCONNECT.value();

    /** Web Services Description Language (WSDL) definition. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String WSDL = BuiltIn.WSDL.value();

    /** XML Schema Definition (XSD). 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String XSD = BuiltIn.XSD.value();

    /** XML document. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String XML = BuiltIn.XML.value();

    /** AI Agent Card for the A2A (Agent-to-Agent) protocol. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String AGENT_CARD = BuiltIn.AGENT_CARD.value();

    /** MCP (Model Context Protocol) tool definition. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String MCP_TOOL = BuiltIn.MCP_TOOL.value();

    /** Apache Iceberg table metadata. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String ICEBERG_TABLE = BuiltIn.ICEBERG_TABLE.value();

    /** Apache Iceberg view metadata. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String ICEBERG_VIEW = BuiltIn.ICEBERG_VIEW.value();

    /** OpenRPC specification for JSON-RPC APIs. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String OPENRPC = BuiltIn.OPENRPC.value();

    /** AI/ML model input/output schema definition. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String MODEL_SCHEMA = BuiltIn.MODEL_SCHEMA.value();

    /** Version-controlled prompt template with variable schemas. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String PROMPT_TEMPLATE = BuiltIn.PROMPT_TEMPLATE.value();

    /** Open Data Contract Standard (ODCS) v3.1 contract definition. 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String ODCS_CONTRACT = BuiltIn.ODCS_CONTRACT.value();

    /** Apache Thrift interface definition language (IDL). 
     * @deprecated Use {@link BuiltIn} instead.
     */
    @Deprecated(forRemoval = true)
    String THRIFT = BuiltIn.THRIFT.value();
}
