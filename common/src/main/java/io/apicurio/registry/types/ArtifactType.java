package io.apicurio.registry.types;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an artifact type used by the registry.
 * <p>
 * Built-in types are modeled as enum constants, while custom types can still be contributed
 * at runtime through {@code ArtifactTypeUtilProvider} implementations and configuration.
 */
public sealed interface ArtifactType permits ArtifactType.BuiltIn, ArtifactType.Custom {

    ArtifactType AVRO = BuiltIn.AVRO;
    ArtifactType PROTOBUF = BuiltIn.PROTOBUF;
    ArtifactType JSON = BuiltIn.JSON;
    ArtifactType OPENAPI = BuiltIn.OPENAPI;
    ArtifactType ASYNCAPI = BuiltIn.ASYNCAPI;
    ArtifactType GRAPHQL = BuiltIn.GRAPHQL;
    ArtifactType KCONNECT = BuiltIn.KCONNECT;
    ArtifactType WSDL = BuiltIn.WSDL;
    ArtifactType XSD = BuiltIn.XSD;
    ArtifactType XML = BuiltIn.XML;
    ArtifactType AGENT_CARD = BuiltIn.AGENT_CARD;
    ArtifactType ICEBERG_TABLE = BuiltIn.ICEBERG_TABLE;
    ArtifactType ICEBERG_VIEW = BuiltIn.ICEBERG_VIEW;

    String value();

    default boolean matches(String candidate) {
        return value().equals(candidate);
    }

    default Optional<BuiltIn> asBuiltIn() {
        return this instanceof BuiltIn builtIn ? Optional.of(builtIn) : Optional.empty();
    }

    static ArtifactType fromValue(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return parseBuiltin(value).<ArtifactType>map(type -> type).orElseGet(() -> new Custom(value));
    }

    static Optional<BuiltIn> parseBuiltin(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(BuiltIn.values())
                .filter(type -> type.name().equals(value))
                .findFirst();
    }

    static Custom custom(String value) {
        return new Custom(value);
    }

    enum BuiltIn implements ArtifactType {

        /** Apache Avro schema. */
        AVRO,

        /** Google Protocol Buffers definition. */
        PROTOBUF,

        /** JSON Schema. */
        JSON,

        /** OpenAPI specification. */
        OPENAPI,

        /** AsyncAPI specification. */
        ASYNCAPI,

        /** GraphQL schema definition language (SDL). */
        GRAPHQL,

        /** Apache Kafka Connect schema. */
        KCONNECT,

        /** Web Services Description Language (WSDL) definition. */
        WSDL,

        /** XML Schema Definition (XSD). */
        XSD,

        /** XML document. */
        XML,

        /** AI Agent Card for the A2A (Agent-to-Agent) protocol. */
        AGENT_CARD,

        /** Apache Iceberg table metadata. */
        ICEBERG_TABLE,

        /** Apache Iceberg view metadata. */
        ICEBERG_VIEW;

        @Override
        public String value() {
            return name();
        }
    }

    record Custom(String value) implements ArtifactType {
        public Custom {
            Objects.requireNonNull(value, "value must not be null");
        }
    }
}
