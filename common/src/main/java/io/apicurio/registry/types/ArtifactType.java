
package io.apicurio.registry.types;

/**
 * Defines the supported artifact types in the registry. The artifact type identifies the format or schema
 * language of an artifact's content and determines how the registry parses, validates, and checks
 * compatibility of that content.
 */
public class ArtifactType {

    private ArtifactType() {
    }

    // TODO: Turn into enum, which can contain both a string value and a numeric identifier.
    // See io.apicurio.registry.storage.impl.kafkasql.serde.ArtifactTypeOrdUtil

    /** Apache Avro schema. */
    public static final String AVRO = "AVRO";

    /** Google Protocol Buffers definition. */
    public static final String PROTOBUF = "PROTOBUF";

    /** JSON Schema. */
    public static final String JSON = "JSON";

    /** OpenAPI specification. */
    public static final String OPENAPI = "OPENAPI";

    /** AsyncAPI specification. */
    public static final String ASYNCAPI = "ASYNCAPI";

    /** GraphQL schema definition language (SDL). */
    public static final String GRAPHQL = "GRAPHQL";

    /** Apache Kafka Connect schema. */
    public static final String KCONNECT = "KCONNECT";

    /** Web Services Description Language (WSDL) definition. */
    public static final String WSDL = "WSDL";

    /** XML Schema Definition (XSD). */
    public static final String XSD = "XSD";

    /** XML document. */
    public static final String XML = "XML";

    /** AI Agent Card for the A2A (Agent-to-Agent) protocol. */
    public static final String AGENT_CARD = "AGENT_CARD";

    /** Apache Iceberg table metadata. */
    public static final String ICEBERG_TABLE = "ICEBERG_TABLE";

    /** Apache Iceberg view metadata. */
    public static final String ICEBERG_VIEW = "ICEBERG_VIEW";

}
