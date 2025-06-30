package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.SearchedGroup;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;

import static io.apicurio.registry.mcp.Descriptions.DESCRIPTION;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ID;
import static io.apicurio.registry.mcp.Descriptions.GROUP_ORDER_BY;
import static io.apicurio.registry.mcp.Descriptions.JSON_LABELS;
import static io.apicurio.registry.mcp.Descriptions.ORDER;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_DESCRIPTION;
import static io.apicurio.registry.mcp.Descriptions.SEARCH_JSON_LABELS;
import static io.apicurio.registry.mcp.Utils.handleError;

public class GroupsMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Get the list of artifact groups from the Apicurio Registry server. \
            Always include the default group named "default".""")
    List<SearchedGroup> list_groups(
            @ToolArg(description = ORDER) String order,
            @ToolArg(description = GROUP_ORDER_BY) String orderBy
    ) {
        return handleError(() -> service.listGroups(
                order,
                orderBy
        ));
    }

    @Tool(description = """
            Create new group in the Apicurio Registry server. \
            It is not necessary to create a group before creating a new artifact, \
            but the advantage of using this function is that you can provide a description and labels for the group. \
            Returns metadata of the newly created group.""")
    GroupMetaData create_group(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = DESCRIPTION, required = false) String description,
            @ToolArg(description = JSON_LABELS, required = false) String jsonLabels
    ) {
        return handleError(() -> service.createGroup(
                groupId,
                description,
                jsonLabels
        ));
    }

    @Tool(description = """
            Update group metadata in the Apicurio Registry server.""")
    String update_group_metadata(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = DESCRIPTION, required = false) String description,
            @ToolArg(description = JSON_LABELS, required = false) String jsonLabels
    ) {
        return handleError(() -> service.updateGroupMetadata(
                groupId,
                description,
                jsonLabels
        ));
    }

    @Tool(description = """
            Update group metadata in the Apicurio Registry server.""")
    GroupMetaData get_group_metadata(
            @ToolArg(description = GROUP_ID) String groupId
    ) {
        return handleError(() -> service.getGroupMetadata(
                groupId
        ));
    }

    // TODO: Does it make sense to have both list_groups and search_groups?
    @Tool(description = """
            Search for groups in the Apicurio Registry server. \
            Returns metadata of the groups that fit the search criteria.""")
    List<SearchedGroup> search_groups(
            @ToolArg(description = GROUP_ID) String groupId,
            @ToolArg(description = SEARCH_DESCRIPTION, required = false) String description,
            @ToolArg(description = SEARCH_JSON_LABELS, required = false) String jsonLabels,
            @ToolArg(description = ORDER) String order,
            @ToolArg(description = GROUP_ORDER_BY) String groupOrderBy
    ) {
        return handleError(() -> service.searchGroups(
                groupId,
                description,
                jsonLabels,
                order,
                groupOrderBy
        ));
    }
}
