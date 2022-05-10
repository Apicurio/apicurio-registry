package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;

import java.time.Duration;

public class CatalogSourceResourceType implements ResourceType<CatalogSource> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(5);
    }

    @Override
    public String getKind() {
        return ResourceKind.CATALOG_SOURCE;
    }

    @Override
    public CatalogSource get(String namespace, String name) {
        return Kubernetes.getCatalogSource(namespace, name);
    }

    @Override
    public void create(CatalogSource resource) {
        Kubernetes.createCatalogSource(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(CatalogSource resource) {
        Kubernetes.createOrReplaceCatalogSource(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(CatalogSource resource) throws Exception {
        Kubernetes.deleteCatalogSource(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(CatalogSource resource) {
        if (resource == null || resource.getStatus() == null) {
            return  false;
        }

        if (resource.getStatus().getConnectionState().getLastObservedState().equals("READY")) {
            return true;
        }

        return false;
    }

    @Override
    public void refreshResource(CatalogSource existing, CatalogSource newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }
}
