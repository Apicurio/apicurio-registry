package io.apicurio.registry.resolver;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

public class ArtifactTypeToContentType {

    public static String toContentType(String artifactType) {
        if (ArtifactType.AVRO.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.JSON.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.PROTOBUF.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_PROTOBUF;
        } else if (ArtifactType.KCONNECT.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.OPENAPI.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.ASYNCAPI.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.WSDL.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_XML;
        } else if (ArtifactType.XSD.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_XML;
        } else if (ArtifactType.XML.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_XML;
        } else if (ArtifactType.GRAPHQL.value().equals(artifactType)) {
            return ContentTypes.APPLICATION_GRAPHQL;
        }
        throw new IllegalArgumentException("Artifact type not supported: " + artifactType);
    }

}

