package io.apicurio.registry.systemtest.messaginginfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.Namespace;

public class NamespaceResourceType implements ResourceType<Namespace> {

    @Override
    public String getKind() {
        return "Namespace";
    }

    @Override
    public Namespace get(String namespace, String name) {
        return Kubernetes.getClient().namespaces().withName(name).get();
    }

    @Override
    public void create(Namespace resource) {
        Kubernetes.getClient().namespaces().create(resource);
    }

    @Override
    public void delete(Namespace resource) throws Exception {
        Kubernetes.getClient().namespaces().delete(resource);
    }

    @Override
    public boolean isReady(Namespace resource) {
        Namespace namespace = get(null, resource.getMetadata().getName());

        if (namespace == null) {
            return false;
        }

        return namespace.getStatus().getPhase().equals("Active");
    }

    @Override
    public void refreshResource(Namespace existing, Namespace newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }
}
