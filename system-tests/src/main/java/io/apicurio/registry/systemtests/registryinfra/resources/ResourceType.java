package io.apicurio.registry.systemtests.registryinfra.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.time.Duration;

public interface ResourceType<T extends HasMetadata> {

    Duration getTimeout();
    String getKind();

    T get(String namespace, String name);

    void create(T resource);

    void createOrReplace(T resource);

    void delete(T resource) throws Exception;

    /**
     * Check if this resource is marked as ready or not.
     *
     * @return true if ready.
     */
    boolean isReady(T resource);

    boolean doesNotExist(T resource);

    /**
     * Update the resource with the latest state on the Kubernetes API.
     */
    void refreshResource(T existing, T newResource);
}