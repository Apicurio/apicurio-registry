package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.Secret;

import java.time.Duration;

public class SecretResourceType implements ResourceType<Secret> {

    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.SECRET;
    }

    @Override
    public Secret get(String namespace, String name) {
        return Kubernetes.getClient().secrets().inNamespace(namespace).withName(name).get();
    }

    @Override
    public void create(Secret resource) {
        Kubernetes.getClient().secrets().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(Secret resource) {
        Kubernetes.getClient().secrets().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(Secret resource) throws Exception {
        Kubernetes.getClient().secrets().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(Secret resource) {
        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) != null;
    }

    @Override
    public void refreshResource(Secret existing, Secret newResource) {
        existing.setMetadata(newResource.getMetadata());
    }
}
