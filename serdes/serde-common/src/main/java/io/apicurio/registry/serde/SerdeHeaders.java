package io.apicurio.registry.serde;

/**
 * Contains all of the header names used when serde classes are configured to pass information via headers
 * instead of via the message payload. Note that these header names can be overridden via configuration.
 */
public class SerdeHeaders {

    public static final String HEADER_KEY_ENCODING = "apicurio.key.encoding";
    public static final String HEADER_VALUE_ENCODING = "apicurio.value.encoding";
    public static final String HEADER_KEY_GROUP_ID = "apicurio.key.groupId";
    public static final String HEADER_VALUE_GROUP_ID = "apicurio.value.groupId";
    public static final String HEADER_KEY_ARTIFACT_ID = "apicurio.key.artifactId";
    public static final String HEADER_VALUE_ARTIFACT_ID = "apicurio.value.artifactId";
    public static final String HEADER_KEY_VERSION = "apicurio.key.version";
    public static final String HEADER_VALUE_VERSION = "apicurio.value.version";
    public static final String HEADER_KEY_GLOBAL_ID = "apicurio.key.globalId";
    public static final String HEADER_VALUE_GLOBAL_ID = "apicurio.value.globalId";
    public static final String HEADER_KEY_CONTENT_ID = "apicurio.key.contentId";
    public static final String HEADER_VALUE_CONTENT_ID = "apicurio.value.contentId";
    public static final String HEADER_KEY_CONTENT_HASH = "apicurio.key.contentHash";
    public static final String HEADER_VALUE_CONTENT_HASH = "apicurio.value.contentHash";
    public static final String HEADER_KEY_MESSAGE_TYPE = "apicurio.key.msgType";
    public static final String HEADER_VALUE_MESSAGE_TYPE = "apicurio.value.msgType";

}
