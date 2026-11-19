package io.apicurio.registry.agents.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.rest.v3.beans.AgentInventorySource;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;
import io.apicurio.registry.types.ArtifactType;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code MCPServer} resources of the MCP Lifecycle Operator ({@code mcp.x-k8s.io}), as
 * {@code MCP_SERVER} artifacts. The resource does not list its tools; the sync imports them from
 * the server's address when asked to.
 */
@ApplicationScoped
public class KubernetesMcpServerSource implements InventorySource {

    static final String GROUP = "mcp.x-k8s.io";
    static final String KIND = "MCPServer";
    static final String PLURAL = "mcpservers";
    // v1 is the storage version from operator 0.4; Open Data Hub ships v1beta1 as storage.
    static final List<String> VERSIONS = List.of("v1", "v1beta1", "v1alpha1");
    static final String ARTIFACT_ID_PREFIX = "k8s-mcp";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DESCRIPTION_MAX_LENGTH = 100;

    @Inject
    AgentInventoryConfig config;

    @Inject
    Instance<KubernetesClient> client;

    @Override
    public AgentInventorySource source() {
        return AgentInventorySource.KUBERNETES_MCP_SERVERS;
    }

    @Override
    public String scope(AgentInventorySyncRequest request) {
        return KubernetesInventory.namespace(request, config);
    }

    @Override
    public List<InventoryItem> list(String namespace, Map<String, String> headers, int maxItems) {
        KubernetesInventory.requireNoHeaders(headers);
        List<InventoryItem> items = new ArrayList<>();
        for (GenericKubernetesResource resource : KubernetesInventory.list(client, GROUP, VERSIONS, KIND,
                PLURAL, namespace, maxItems)) {
            items.add(item(namespace, resource));
        }
        return items;
    }

    static InventoryItem item(String namespace, GenericKubernetesResource resource) {
        String name = resource.getMetadata().getName();
        Map<String, Object> status = KubernetesInventory.map(resource.getAdditionalProperties().get("status"));
        Map<String, Object> serverInfo = KubernetesInventory.map(status.get("serverInfo"));
        String url = KubernetesInventory.string(KubernetesInventory.map(status.get("address")), "url");
        String version = KubernetesInventory.string(serverInfo, "version");
        String serverName = KubernetesInventory.string(serverInfo, "name");

        // server.json, as the MCP Registry defines it. The name must be "<namespace>/<name>" in
        // reverse-DNS style, and the description is limited to 100 characters.
        ObjectNode server = MAPPER.createObjectNode();
        server.put("name", "io.k8s." + namespace + "/" + name);
        String description = "MCP server " + namespace + "/" + name + (serverName != null ? " (" + serverName + ")" : "");
        server.put("description", description.length() > DESCRIPTION_MAX_LENGTH
                ? description.substring(0, DESCRIPTION_MAX_LENGTH) : description);
        server.put("version", version != null ? version : "0.0.0");
        if (url != null) {
            server.putArray("remotes").addObject().put("type", "streamable-http").put("url", url);
        }
        return new InventoryItem(namespace + "/" + name,
                KubernetesInventory.artifactId(ARTIFACT_ID_PREFIX, namespace, name), ArtifactType.MCP_SERVER,
                serverName != null ? serverName : name, description, server.toString(), url);
    }
}
