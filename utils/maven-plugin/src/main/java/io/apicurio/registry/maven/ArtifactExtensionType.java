package io.apicurio.registry.maven;

import java.util.HashMap;
import java.util.Map;



public enum ArtifactExtensionType {
    // TODO: make this customizable
    AVRO("AVRO", "avsc"),
    PROTOBUF("PROTOBUF", "proto"),
    JSON("JSON", "json"),
    OPENAPI("OPENAPI", "json"),
    ASYNCAPI("ASYNCAPI", "json"),
    GRAPHQL("GRAPHQL", "graphql"),
    KCONNECT("KCONNECT", "json"),
    WSDL("WSDL", "wsdl"),
    XSD("XSD", "xsd"),
    XML("XML", "xml");

    private final String artifactType;
    private final String artifactExtension;
    private final static Map<String, ArtifactExtensionType> CONSTANTS = new HashMap<>();

    static {
        for (ArtifactExtensionType c: values()) {
            CONSTANTS.put(c.artifactType, c);
        }
    }

    ArtifactExtensionType(String artifactType, String artifactExtension) {
        this.artifactType = artifactType;
        this.artifactExtension = artifactExtension;
    }

    @Override
    public String toString() {
        return this.artifactExtension;
    }

    public static ArtifactExtensionType fromArtifactType(String artifactType) {
        ArtifactExtensionType constant = CONSTANTS.get(artifactType);
        if (constant == null) {
            throw new IllegalArgumentException(artifactType.toString());
        } else {
            return constant;
        }
    }
}
