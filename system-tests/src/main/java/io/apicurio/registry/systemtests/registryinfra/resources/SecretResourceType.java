package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.platform.Kubernetes;
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
        return Kubernetes.getSecret(namespace, name);
    }

    @Override
    public void create(Secret resource) {
        Kubernetes.createSecret(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(Secret resource) {
        Kubernetes.createOrReplaceSecret(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(Secret resource) throws Exception {
        Kubernetes.deleteSecret(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(Secret resource) {
        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) != null;
    }

    @Override
    public boolean doesNotExist(Secret resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(Secret existing, Secret newResource) {
        existing.setMetadata(newResource.getMetadata());
    }
}
