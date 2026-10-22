package io.apicurio.registry.types;

import jakarta.ws.rs.core.MediaType;

public final class ArtifactMediaTypes {

    public static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE;
    public static final MediaType XML = MediaType.APPLICATION_XML_TYPE;
    public static final MediaType YAML = new MediaType("application", "x-yaml");
    public static final MediaType PROTO = new MediaType("application", "x-protobuf");
    public static final MediaType GRAPHQL = new MediaType("application", "graphql");
    public static final MediaType BINARY = new MediaType("application", "octet-stream");

}
