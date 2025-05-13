package io.apicurio.registry.mcp;

public final class Descriptions {

    public static final String GROUP_ID = """
            Artifacts are organized into groups, identified by a group ID. \
            When an artifact is created or updated, a group ID must be specified. \
            Ask the user to provide you with the group ID, or generate a reasonable value based on context. \
            There is always a default group named "default".""";

    public static final String ARTIFACT_ID = """
            Artifacts are identified by an artifact ID, in addition to the group ID. \
            When a new artifact is created, an artifact ID must be specified. \
            Ask the user to provide you with the artifact ID, or generate a reasonable value based on context.""";

    public static final String VERSION = """
            Artifact version identifier. \
            Try to use semantic versioning when generating version numbers, and try to use the same version as is in the content.""";

    public static final String VERSION_EXPRESSION = """
            Version expression is a string that is used to identify an artifact version. Can be in one of the following formats:
            - Version expression contains artifact version identifiers as-is, which represents the specific version.
            - Version expression contains the string "branch=latest", which represents the latest version in an artifact.
            """; // TODO: Branches

    public static final String DESCRIPTION = """
            Provide a description for the object being created.""";

    public static final String SEARCH_DESCRIPTION = """
            Search by object's description. Object fits the search criterion if the description contains the given string.""";

    public static final String SEARCH_JSON_LABELS = """
            Search by object's labels. \
            Provide labels in the form of an JSON object, containing string keys and string values. \
            Labels are used to categorize objects, provide context, and for faster searching. \
            Object fits the search criterion if all of the labels are present. \
            """;

    public static final String JSON_LABELS = """
            Provide labels in the form of an JSON object, containing string keys and string values. \
            Labels are used to categorize objects, provide context, and for faster searching. \
            Ask the user whether they want you to generate the labels, offering some suggestions.""";

    public static final String ARTIFACT_TYPE = """
            Artifacts store content of a specific type. \
            To get the list of artifact types supported by the apicurio Registry server, \
            execute the "get_artifact_types" function.""";

    public static final String CONTENT_TYPE = """
            Content type (also known as media type or MIME type), for example "application/json" or "application/yaml".""";

    public static final String CONTENT = """
            Content itself. This is usually an API definition or a message schema.""";

    public static final String DEF_NAME = """
            Name is a concise and human-friendly descriptor for an object in Apicurio Registry. \
            Name is not an identifier.""";

    public static final String SEARCH_NAME = """
                                                     Search by object's name. Object fits the search criterion if it's name contains the provided sub-string.\s""" + DEF_NAME;

    public static final String NAME = """
                                              Name for the object being created or updated.\s""" + DEF_NAME + """
                                              Generate a name from the content or ask the user what name do they want to use.""";

    public static final String VERSION_IS_DRAFT = """
            Usually, artifact versions are not mutable, and to update an artifact content, a new version must be created. \
            However, Apicurio Registry provide a feature where a version can be created in a "DRAFT" state, \
            which means it can be modified, until it is moved into the "ENABLED" state. \
            Ask the user whether you should create an artifact version in the draft mode. \
            After the user is ready to publish, call the "update_artifact_version_state" function.""";

    public static final String VERSION_STATE = """
            Version state is a piece of metadata that can be in one of the following 4 values:
              - "ENABLED" version is published and ready for use.
              - "DEPRECATED" version should not be used, but is still available;
              - "DISABLED" version should not be used and will not show up when listing artifact versions, or retrieving the latest version of an artifact. \
                           It will still be accessible when retrieving a version using group ID, artifact ID and version identifiers.
              - "DRAFT" Usually, artifact versions are not mutable, and to update an artifact content, a new version must be created. \
                        However, Apicurio Registry provide a feature where a version can be created in a "DRAFT" state, \
                        which means it can be modified, until it is moved into the "ENABLED" state. \
                        However, Apicurio Registry provide a feature where a version can be created in a "DRAFT" state, \
                        which means it can be modified, until it is moved into the "ENABLED" state.""";

    public static final String PROPERTY_NAME = """
            Name of a configuration property.""";

    public static final String PROPERTY_VALUE = """
            Value of the configuration property. Value must be convertible to the property's data type. \
            For example, a boolean property value must be either "true" or "false". \
            You can determine the data type by calling the "get_configuration_property" function.""";

    public static final String ORDER = """
            Results can be returned in descending or ascending order. \
             - If the value is "desc", the results are sorted in descending order (highest to lowest or alphabetical).
             - If the value is "asc", the results are sorted in ascending order (lowest to highest or reverse alphabetical).""";

    public static final String GROUP_ORDER_BY = """
            Results can be returned in one of the following orderings, based on the value of the argument. \
             - If the value is "groupId", the groups are ordered alphabetically by group ID.
             - If the value is "createdOn", the groups are ordered by the creation time of the group.
             - If the value is "modifiedOn", the groups are ordered by the time of last modification of the group metadata.""";

    public static final String ARTIFACT_ORDER_BY = """
            Results can be returned in one of the following orderings, based on the value of the argument. \
             - If the value is "groupId", the artifacts are ordered alphabetically by group ID.
             - If the value is "artifactId", the artifacts are ordered alphabetically by artifact ID.
             - If the value is "name", the artifacts are ordered by the artifact name. Artifact name is not an identifier, but a concise human-friendly name for the object.
             - If the value is "artifactType", the artifacts are ordered by the type of the artifact. You can call the "get_artifact_types" function to get the list of supported artifact types.
             - If the value is "createdOn", the artifacts are ordered by the creation time of the artifact.
             - If the value is "modifiedOn", the artifacts are ordered by the time of last modification of the artifact metadata.
             """;

    public static final String VERSION_ORDER_BY = """
            Results can be returned in one of the following orderings, based on the value of the argument. \
             - If the value is "groupId", versions are ordered alphabetically by group ID.
             - If the value is "artifactId", versions are ordered alphabetically by artifact ID.
             - If the value is "version", versions are ordered alphabetically by the version identifier.
             - If the value is "name", versions are ordered alphabetically by the version name. Version name is not an identifier, but a concise human-friendly name for the object.         
             - If the value is "createdOn", the artifacts are ordered by the creation time of the artifact.
             - If the value is "modifiedOn", the artifacts are ordered by the time of last modification of the artifact metadata.
             - If the value is "globalId", the artifacts are ordered by the global ID identifier. \
             Global ID is a numeric identifier unique within an Apicurio Registry instance. \
             It is assigned by incrementing a counter each time an artifact version is created.""";

    private Descriptions() {
    }
}
