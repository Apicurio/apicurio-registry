package io.apicurio.registry.types;

import jakarta.ws.rs.core.MediaType;

public final class ArtifactMediaTypes {

    private static final String APPLICATION = "application";

    public static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE;
    public static final MediaType XML = MediaType.APPLICATION_XML_TYPE;
    public static final MediaType YAML = new MediaType(APPLICATION, "x-yaml");
    public static final MediaType PROTO = new MediaType(APPLICATION, "x-protobuf");
    public static final MediaType GRAPHQL = new MediaType(APPLICATION, "graphql");
    public static final MediaType BINARY = new MediaType(APPLICATION, "octet-stream");

}
