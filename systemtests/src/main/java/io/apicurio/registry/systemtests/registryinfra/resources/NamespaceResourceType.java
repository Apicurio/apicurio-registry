package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;

import java.time.Duration;

public class NamespaceResourceType implements ResourceType<Namespace> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.NAMESPACE;
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
    public void createOrReplace(Namespace resource) {
        Kubernetes.getClient().namespaces().createOrReplace(resource);
    }

    @Override
    public void delete(Namespace resource) {
        Kubernetes.getClient().namespaces().withName(resource.getMetadata().getName()).delete();
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
    public boolean doesNotExist(Namespace resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(Namespace existing, Namespace newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Namespace getDefault(String name) {
        return new NamespaceBuilder()
                .withNewMetadata()
                    .withName(name)
                .endMetadata()
                .build();
    }
}
