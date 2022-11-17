package io.apicurio.registry.maven;

import io.apicurio.registry.types.ArtifactType;

import java.util.HashMap;
import java.util.Map;



public enum ArtifactExtensionType {
    // TODO: should we support custom types even here?
    AVRO(ArtifactType.AVRO, "avsc"),
    PROTOBUF(ArtifactType.PROTOBUF, "proto"),
    JSON(ArtifactType.JSON, "json"),
    OPENAPI(ArtifactType.OPENAPI, "json"),
    ASYNCAPI(ArtifactType.ASYNCAPI, "json"),
    GRAPHQL(ArtifactType.GRAPHQL, "graphql"),
    KCONNECT(ArtifactType.KCONNECT, "json"),
    WSDL(ArtifactType.WSDL, "wsdl"),
    XSD(ArtifactType.XSD, "xsd"),
    XML(ArtifactType.XML, "xml");

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
