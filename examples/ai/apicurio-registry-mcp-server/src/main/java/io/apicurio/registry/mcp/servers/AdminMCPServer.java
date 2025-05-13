package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.ConfigurationProperty;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;

import static io.apicurio.registry.mcp.Descriptions.PROPERTY_NAME;
import static io.apicurio.registry.mcp.Descriptions.PROPERTY_VALUE;
import static io.apicurio.registry.mcp.Utils.handleError;

public class AdminMCPServer {

    @Inject
    RegistryService service;

    @Tool(description = """
            Returns a list of artifact types supported by the Apicurio Registry server.""")
    List<ArtifactTypeInfo> get_artifact_types() {
        return handleError(() -> service.getArtifactTypes());
    }

    @Tool(description = """
            Returns a list of all dynamic configuration properties of the Apicurio Registry server. \
            Dynamic configuration property is a property that can be modified without requiring server restart.""")
    List<ConfigurationProperty> list_configuration_properties() {
        return handleError(() -> service.listConfigurationProperties());
    }

    @Tool(description = """
            Get information about a dynamic configuration property of the Apicurio Registry server. \
            Dynamic configuration property is a property that can be modified without requiring server restart.""")
    ConfigurationProperty get_configuration_property(
            @ToolArg(description = PROPERTY_NAME) String propertyName
    ) {
        return handleError(() ->
                service.getConfigurationProperty(propertyName)
        );
    }

    @Tool(description = """
            Update dynamic configuration property of the Apicurio Registry server. \
            Dynamic configuration property is a property that can be modified without requiring server restart.""")
    String update_configuration_property(
            @ToolArg(description = PROPERTY_NAME) String propertyName,
            @ToolArg(description = PROPERTY_VALUE) String propertyValue
    ) {
        return handleError(() -> {
            service.updateConfigurationProperty(propertyName, propertyValue);
        });
    }
}
