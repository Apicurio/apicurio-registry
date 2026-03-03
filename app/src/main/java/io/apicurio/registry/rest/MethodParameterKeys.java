package io.apicurio.registry.rest;

/**
 * Constants for method parameter keys used in {@link MethodMetadata} annotations.
 * These keys are extracted by {@link MethodMetadataInterceptor} and made available
 * to downstream interceptors (e.g. auditing, caching).
 */
public final class MethodParameterKeys {

    public static final String MPK_ARTIFACT_ID = "artifact_id";
    public static final String MPK_ARTIFACT_TYPE = "artifact_type";
    public static final String MPK_CANONICAL = "canonical";
    public static final String MPK_CONTENT_HASH = "content_hash";
    public static final String MPK_DESCRIPTION = "description";
    public static final String MPK_DESCRIPTION_ENCODED = "description_encoded";
    public static final String MPK_EDITABLE_METADATA = "editable_metadata";
    // Used to extract entity ID for ETag generation in HTTP caching
    public static final String MPK_ENTITY_ID = "entityId";
    public static final String MPK_FOR_BROWSER = "for_browser";
    public static final String MPK_FROM_URL = "from_url";
    public static final String MPK_GROUP_ID = "group_id";
    public static final String MPK_IF_EXISTS = "if_exists";
    public static final String MPK_NAME = "name";
    public static final String MPK_NAME_ENCODED = "name_encoded";
    public static final String MPK_OWNER = "owner";
    public static final String MPK_PRINCIPAL_ID = "principal_id";
    public static final String MPK_PROPERTY_CONFIGURATION = "property_configuration";
    public static final String MPK_REF_TYPE = "refType";
    public static final String MPK_ROLE_MAPPING = "role_mapping";
    public static final String MPK_RULE = "rule";
    public static final String MPK_RULE_TYPE = "rule_type";
    public static final String MPK_UPDATE_ROLE = "update_role";
    public static final String MPK_UPDATE_STATE = "update_state";
    public static final String MPK_VERSION = "version";

    private MethodParameterKeys() {
    }
}
