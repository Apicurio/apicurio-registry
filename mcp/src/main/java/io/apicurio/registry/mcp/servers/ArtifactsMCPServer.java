package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;

import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ID;
import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_ORDER_BY;
import static io.apicurio.registry.mcp.Descriptions.ARTIFACT_TYPE;
import static io.apicurio.registry.mcp.Descriptions.DESCRIPTION;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Descriptions.JSON_LABELS;
import static io.apicurio.registry.mcp.Descriptions.NAME;
import static io.apicurio.registry.mcp.Descriptions.ORDER;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_DESCRIPTION;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_JSON_LABELS;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_NAME;
import static io.apicurio.registry.mcp.Utils.handleError;

public class ArtifactsMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Get a list of artifacts within the group.""")
    List<SearchedArtifact> list_artifacts(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ORDER) String order,
            @ToolArg(description = ARTIFACT_ORDER_BY) String artifactOrderBy
    ) {
        return handleError(() -> service.listArtifacts(
                groupId,
                order,
                artifactOrderBy
        ));
    }

    @Tool(description = """
            Get information (metadata) about an existing artifact. \
            If the artifact does not exist, an error is returned.""")
    ArtifactMetaData get_artifact_metadata(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId
    ) {
        return handleError(() -> service.getArtifactMetadata(
                groupId,
                artifactId
        ));
    }

    @Tool(description = """
            Update information (metadata) about an existing artifact. \
            If the artifact does not exist, an error is returned.""")
    String update_artifact_metadata(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = NAME, required = false) String name,
            @ToolArg(description = DESCRIPTION, required = false) String description,
            @ToolArg(description = JSON_LABELS, required = false) String jsonLabels
    ) {
        return handleError(() -> service.updateArtifactMetadata(
                groupId,
                artifactId,
                name,
                description,
                jsonLabels
        ));
    }

    @Tool(description = """
            Create new artifact in the Apicurio Registry server. \
            Artifacts consist of a sequence of versions, which represent changes to a piece of content. \
            This function creates an empty artifact, to which content can be added later. \
            Returns metadata of the newly-created artifact.""")
    ArtifactMetaData create_artifact(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID) String artifactId,
            @ToolArg(description = ARTIFACT_TYPE, required = false) String artifactType,
            @ToolArg(description = NAME, required = false) String name,
            @ToolArg(description = DESCRIPTION, required = false) String description,
            @ToolArg(description = JSON_LABELS, required = false) String jsonLabels
    ) {
        return handleError(() -> service.createArtifact(
                        groupId,
                        artifactId,
                        artifactType,
                        name,
                        description,
                        jsonLabels
                )
        );
    }

    @Tool(description = """
            Search for artifacts in the Apicurio Registry server. \
            Returns metadata of the groups that fit the search criteria.""")
    List<SearchedArtifact> search_artifacts(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = ARTIFACT_ID, required = false) String artifactId,
            @ToolArg(description = ARTIFACT_TYPE, required = false) String artifactType,
            @ToolArg(description = SEARCH_NAME, required = false) String name,
            @ToolArg(description = SEARCH_DESCRIPTION, required = false) String description,
            @ToolArg(description = SEARCH_JSON_LABELS, required = false) String jsonLabels,
            @ToolArg(description = ORDER) String order,
            @ToolArg(description = ARTIFACT_ORDER_BY) String artifactOrderBy
    ) {
        return handleError(() -> service.searchArtifacts(
                groupId,
                artifactId,
                artifactType,
                name,
                description,
                jsonLabels,
                order,
                artifactOrderBy
        ));
    }
}
