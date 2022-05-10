package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;

import java.time.Duration;

public class SubscriptionResourceType implements ResourceType<Subscription> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.SUBSCRIPTION;
    }

    @Override
    public Subscription get(String namespace, String name) {
        return Kubernetes.getSubscription(namespace, name);
    }

    @Override
    public void create(Subscription resource) {
        Kubernetes.createSubscription(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(Subscription resource) {
        Kubernetes.createOrReplaceSubscription(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(Subscription resource) throws Exception {
        Kubernetes.deleteSubscription(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(Subscription resource) {
        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName())
                .getStatus()
                .getConditions()
                .stream()
                .filter(condition -> condition.getType().equals("CatalogSourcesUnhealthy"))
                .map(condition -> condition.getStatus().equals("False"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public void refreshResource(Subscription existing, Subscription newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }
}
