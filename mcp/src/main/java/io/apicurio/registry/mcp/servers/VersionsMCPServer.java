package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;

import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_TYPE;
import static io.apicurio.registry.mcp.Descriptions.CONTENT;
import static io.apicurio.registry.mcp.Descriptions.CONTENT_TYPE;
import static io.apicurio.registry.mcp.Descriptions.DESCRIPTION;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Descriptions.JSON_LABELS;
import static io.apicurio.registry.mcp.Descriptions.NAME;
import static io.apicurio.registry.mcp.Descriptions.ORDER;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_DESCRIPTION;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_JSON_LABELS;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_NAME;
import static io.apicurio.registry.mcp.Descriptions.VERSION;
import static io.apicurio.registry.mcp.Descriptions.VERSION_EXPRESSION;
import static io.apicurio.registry.mcp.Descriptions.VERSION_IS_DRAFT;
import static io.apicurio.registry.mcp.Descriptions.VERSION_ORDER_BY;
import static io.apicurio.registry.mcp.Descriptions.VERSION_STATE;
import static io.apicurio.registry.mcp.Utils.handleError;

public class VersionsMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Get a list of versions within an artifact.""")
    List<SearchedVersion> list_versions(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = ORDER) String order,
            @ToolArg(description = VERSION_ORDER_BY) String versionOrderBy
    ) {
        return handleError(() -> service.listVersions(
                groupId,
                artifactId,
                order,
                versionOrderBy
        ));
    }

    @Tool(description = """
            Get content of a given version.""")
    String get_version_content(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION) String versionExpression
    ) {
        return handleError(() -> service.getVersionContent(
                groupId,
                artifactId,
                versionExpression
        ));
    }

    @Tool(description = """
            Create new artifact version in the Apicurio Registry server. \
            Artifacts consist of a sequence of versions, which represent changes to a piece of content. \
            This function creates a new version with a content. \
            To create a new version, the artifact must already exist. Use "get_artifact_metadata" function to check, otherwise use "create_new_artifact" to create the artifact first. \
            Returns metadata of the newly-created version.""")
    VersionMetaData create_version(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION, required = false) String version,
            @ToolArg(description = CONTENT_TYPE) String versionContentType,
            @ToolArg(description = CONTENT) String versionContent,
            @ToolArg(description = NAME, required = false) String name,
            @ToolArg(description = DESCRIPTION, required = false) String description,
            @ToolArg(description = JSON_LABELS, required = false) String jsonLabels,
            @ToolArg(description = VERSION_IS_DRAFT, required = false) Boolean isDraft
    ) {
        return handleError(() -> service.createVersion(
                groupId,
                artifactId,
                version,
                versionContentType,
                versionContent,
                name,
                description,
                jsonLabels,
                isDraft
        ));
    }

    @Tool(description = """
            Update the state of an artifact version.""")
    String update_version_state(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION) String versionExpression,
            @ToolArg(description = VERSION_STATE) String versionState
    ) {
        return handleError(() -> {
            service.updateVersionState(
                    groupId,
                    artifactId,
                    versionExpression,
                    versionState
            );
        });
    }

    @Tool(description = """
            Retrieve metadata of an artifact version.""")
    VersionMetaData get_version_metadata(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION) String versionExpression
    ) {
        return handleError(() -> service.getVersionMetadata(
                groupId,
                artifactId,
                versionExpression
        ));
    }

    @Tool(description = """
            Update metadata of an artifact version.""")
    String update_version_metadata(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION) String versionExpression,
            @ToolArg(description = NAME, required = false) String name,
            @ToolArg(description = DESCRIPTION, required = false) String description,
            @ToolArg(description = JSON_LABELS, required = false) String jsonLabels
    ) {
        return handleError(() -> service.updateVersionMetadata(
                groupId,
                artifactId,
                versionExpression,
                name,
                description,
                jsonLabels
        ));
    }

    @Tool(description = """
            Update content of an artifact version if it is in the DRAFT state.""")
    String update_version_content(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = VERSION_EXPRESSION) String versionExpression,
            @ToolArg(description = CONTENT_TYPE) String versionContentType,
            @ToolArg(description = CONTENT) String versionContent
    ) {
        return handleError(() -> service.updateVersionContent(
                        groupId,
                        artifactId,
                        versionExpression,
                        versionContentType,
                        versionContent
                )
        );
    }

    @Tool(description = """
            Search for artifact version in the Apicurio Registry server. \
            Returns metadata of the versions that fit the search criteria.""")
    List<SearchedVersion> search_versions(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID, required = false) String artifactId,
            @ToolArg(description = ARTIFACT_TYPE, required = false) String artifactType,
            @ToolArg(description = SEARCH_NAME, required = false) String name,
            @ToolArg(description = SEARCH_DESCRIPTION, required = false) String description,
            @ToolArg(description = SEARCH_JSON_LABELS, required = false) String jsonLabels,
            @ToolArg(description = ORDER) String order,
            @ToolArg(description = VERSION_ORDER_BY) String versionOrderBy
    ) {
        return handleError(() -> service.searchVersions(
                groupId,
                artifactId,
                artifactType,
                name,
                description,
                jsonLabels,
                order,
                versionOrderBy
        ));
    }
}
