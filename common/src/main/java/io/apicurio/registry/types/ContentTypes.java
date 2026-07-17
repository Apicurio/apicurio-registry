package io.apicurio.registry.types;

public class ContentTypes {

    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_YAML = "application/x-yaml";
    public static final String APPLICATION_XML = "application/xml";
    public static final String APPLICATION_PROTOBUF = "application/x-protobuf";
    public static final String APPLICATION_GRAPHQL = "application/graphql";
    public static final String TEXT_PROMPT_TEMPLATE = "text/x-prompt-template";
    public static final String APPLICATION_THRIFT = "application/x-thrift";
    public static final String APPLICATION_EMPTY = "application/vnd.apicurio.empty";

    public static boolean isEmptyContentType(String contentType) {
        return APPLICATION_EMPTY.equals(contentType);
    }

    /**
     * Returns the conventional file extension for a given MIME content type.
     * Falls back to ".bin" for unknown types.
     */
    public static String getFileExtension(String contentType) {
        if (contentType == null) {
            return ".bin";
        }
        return switch (contentType) {
            case APPLICATION_JSON -> ".json";
            case APPLICATION_YAML -> ".yaml";
            case APPLICATION_XML -> ".xml";
            case APPLICATION_PROTOBUF -> ".proto";
            case APPLICATION_GRAPHQL -> ".graphql";
            case APPLICATION_THRIFT -> ".thrift";
            case TEXT_PROMPT_TEMPLATE -> ".txt";
            case APPLICATION_EMPTY -> ".empty";
            default -> ".bin";
        };
    }

}
