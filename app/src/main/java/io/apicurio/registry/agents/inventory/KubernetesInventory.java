package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.model.ArtifactId;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What the Kubernetes inventory sources share: the namespace allow list, reading a custom resource
 * at whichever of its versions the cluster serves, and the artifact ID of a resource.
 */
final class KubernetesInventory {

    // DNS-1123 label: what Kubernetes allows as a namespace name.
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?");

    private KubernetesInventory() {
    }

    /**
     * @return the namespace in the request, once checked against the allow list
     */
    static String namespace(AgentInventorySyncRequest request, AgentInventoryConfig config) {
        String namespace = request.getNamespace();
        if (namespace == null || namespace.isBlank()) {
            throw new BadRequestException("A namespace is required for Kubernetes inventory sources.");
        }
        if (!NAMESPACE.matcher(namespace).matches() || !config.getKubernetesNamespaces().contains(namespace)) {
            throw new BadRequestException(
                    "The namespace is not allowed. Allowed namespaces are listed in apicurio.agent-inventory.kubernetes.namespaces.");
        }
        if (request.getLocation() != null) {
            throw new BadRequestException("A location is only used by the GOOGLE_AGENT_REGISTRY source.");
        }
        return namespace;
    }

    /**
     * Kubernetes sources authenticate with the registry's service account, so they take no headers.
     */
    static void requireNoHeaders(Map<String, String> headers) {
        if (!headers.isEmpty()) {
            throw new BadRequestException("Kubernetes inventory sources do not use request headers.");
        }
    }

    /**
     * Lists the resources of a kind in a namespace, trying each version in turn: a cluster serves
     * the versions of the operator release it runs, which need not be the latest.
     *
     * @param group    the API group, for example {@code mcp.x-k8s.io}
     * @param versions the versions to try, newest first
     * @param kind     the kind, for example {@code MCPServer}
     * @param plural   the resource's plural name, for example {@code mcpservers}; given, so that
     *                 listing needs no API discovery request
     */
    static List<GenericKubernetesResource> list(Instance<KubernetesClient> client, String group,
            List<String> versions, String kind, String plural, String namespace, int maxItems) {
        if (!client.isResolvable()) {
            throw new ConflictException("Kubernetes inventory sources are not available.");
        }
        for (String version : versions) {
            try {
                ResourceDefinitionContext resource = new ResourceDefinitionContext.Builder().withGroup(group)
                        .withVersion(version).withKind(kind).withPlural(plural).withNamespaced(true).build();
                List<GenericKubernetesResource> items = client.get().genericKubernetesResources(resource)
                        .inNamespace(namespace).list().getItems();
                if (items.size() > maxItems) {
                    throw new InventorySourceException(
                            "The namespace has more " + kind + " resources than one sync accepts.");
                }
                return items;
            } catch (KubernetesClientException e) {
                if (e.getCode() == 404) {
                    continue;
                }
                if (e.getCode() == 401 || e.getCode() == 403) {
                    throw new InventorySourceException(
                            "The registry is not allowed to list " + kind + " resources in this namespace.");
                }
                throw new InventorySourceException("The " + kind + " resources could not be listed.");
            }
        }
        throw new InventorySourceException("The cluster does not serve " + kind + " resources (" + group + ").");
    }

    /**
     * @return the artifact ID of a resource: the prefix, the namespace and the name, which are
     *         stable for the life of the resource and valid in an artifact ID
     */
    static String artifactId(String prefix, String namespace, String name) {
        String id = prefix + "." + namespace + "." + name;
        if (!ArtifactId.isValid(id)) {
            throw new InventorySourceException("A resource name could not be used as an artifact ID.");
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static String string(Map<String, Object> map, String key) {
        return map.get(key) instanceof String s && !s.isBlank() ? s : null;
    }
}
