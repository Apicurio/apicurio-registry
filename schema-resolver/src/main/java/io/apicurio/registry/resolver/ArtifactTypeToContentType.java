package io.apicurio.registry.resolver;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

public class ArtifactTypeToContentType {

    public static String toContentType(String artifactType) {
        if (ArtifactType.AVRO.equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.JSON.equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.PROTOBUF.equals(artifactType)) {
            return ContentTypes.APPLICATION_PROTOBUF;
        } else if (ArtifactType.KCONNECT.equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.OPENAPI.equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.ASYNCAPI.equals(artifactType)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (ArtifactType.WSDL.equals(artifactType)) {
            return ContentTypes.APPLICATION_XML;
        } else if (ArtifactType.XSD.equals(artifactType)) {
            return ContentTypes.APPLICATION_XML;
        } else if (ArtifactType.XML.equals(artifactType)) {
            return ContentTypes.APPLICATION_XML;
        } else if (ArtifactType.GRAPHQL.equals(artifactType)) {
            return ContentTypes.APPLICATION_GRAPHQL;
        }
        throw new IllegalArgumentException("Artifact type not supported: " + artifactType);
    }

}
