package io.apicurio.registry.systemtest;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.util.HashMap;
import java.util.function.UnaryOperator;

public abstract class AbstractResourceManager<T extends CustomResource<?, ?>, L> {
    protected abstract Class<T> getResourceClass();

    protected abstract Class<L> getResourceListClass();

    protected MixedOperation<T, L, Resource<T>> resourceClient;

    protected HashMap<String, T> createdResources;

    public T create(T resource) {
        createdResources.put(resource.getMetadata().getName(), resourceClient.inNamespace(resource.getMetadata().getNamespace()).create(resource));

        return createdResources.get(resource.getMetadata().getName());
    }

    public T edit(T resource, UnaryOperator<T> function) {
        createdResources.replace(resource.getMetadata().getName(), resourceClient.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).edit(function));

        return createdResources.get(resource.getMetadata().getName());
    }

    public void delete(T resource) {
        resourceClient.inNamespace(resource.getMetadata().getNamespace()).delete(resource);

        createdResources.remove(resource.getMetadata().getName());
    }

    public void deleteAll() {
        for (T resource : createdResources.values()) {
            resourceClient.inNamespace(resource.getMetadata().getNamespace()).delete(resource);
        }

        createdResources.clear();
    }
}
