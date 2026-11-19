package io.apicurio.registry.agents.inventory;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_MCP;

/**
 * Configuration of keeping groups in sync with inventories of agents and MCP servers: Kubernetes
 * resources and third-party agent registries.
 * <p>
 * {@code @Singleton}, like the other configuration classes: it holds injected configuration values
 * only, so it needs no client proxy.
 */
@Singleton
public class AgentInventoryConfig {

    @ConfigProperty(name = "apicurio.agent-inventory.enabled", defaultValue = "false")
    @Info(category = CATEGORY_MCP, description = "Enable keeping groups in sync with inventories of agents and MCP servers, such as MCPServer and AgentCard resources in Kubernetes and agents registered in Google Cloud Agent Registry", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.agent-inventory.kubernetes.namespaces")
    @Info(category = CATEGORY_MCP, description = "Kubernetes namespaces that an inventory sync may read MCPServer and AgentCard resources from, as a comma-separated list. When empty, Kubernetes inventory sources are refused. The registry's service account needs permission to list these resources in each namespace", availableSince = "3.4.0")
    Optional<List<String>> kubernetesNamespaces;

    @ConfigProperty(name = "apicurio.agent-inventory.kubernetes.agent-card.group", defaultValue = "agent.rossoctl.dev")
    @Info(category = CATEGORY_MCP, description = "API group of the AgentCard resources read by the KUBERNETES_AGENT_CARDS inventory source. The default is the group of the Rossoctl operator, formerly Kagenti; set agent.kagenti.dev for clusters running an older Kagenti operator", availableSince = "3.4.0")
    String agentCardGroup;

    @ConfigProperty(name = "apicurio.agent-inventory.google.url", defaultValue = "https://agentregistry.googleapis.com")
    @Info(category = CATEGORY_MCP, description = "Base URL of the Google Cloud Agent Registry API read by the GOOGLE_AGENT_REGISTRY inventory source", availableSince = "3.4.0")
    String googleUrl;

    @ConfigProperty(name = "apicurio.agent-inventory.timeout.seconds", defaultValue = "10")
    @Info(category = CATEGORY_MCP, description = "Timeout, in seconds, for each request to an inventory source during a sync", availableSince = "3.4.0")
    int timeoutSeconds;

    @ConfigProperty(name = "apicurio.agent-inventory.items.max-size", defaultValue = "1000")
    @Info(category = CATEGORY_MCP, description = "Maximum number of items an inventory source may list for one sync. A sync of a source listing more is refused, so an unexpectedly large inventory cannot flood the group", availableSince = "3.4.0")
    int itemsMaxSize;

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getKubernetesNamespaces() {
        return kubernetesNamespaces.orElse(List.of());
    }

    public String getAgentCardGroup() {
        return agentCardGroup;
    }

    public String getGoogleUrl() {
        return googleUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getItemsMaxSize() {
        return itemsMaxSize;
    }
}
