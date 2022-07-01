package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionBuilder;

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
        Subscription subscription = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (subscription == null || subscription.getStatus() == null) {
            return false;
        }

        return subscription
                .getStatus()
                .getConditions()
                .stream()
                .filter(condition -> condition.getType().equals("CatalogSourcesUnhealthy"))
                .map(condition -> condition.getStatus().equals("False"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public boolean doesNotExist(Subscription resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(Subscription existing, Subscription newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Subscription getDefault(
            String name,
            String namespace,
            String packageName,
            String sourceName,
            String sourceNamespace,
            String startingCSV,
            String channel
    ) {
        return new SubscriptionBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withName(packageName)
                    .withSource(sourceName)
                    .withSourceNamespace(sourceNamespace)
                    .withStartingCSV(startingCSV)
                    .withChannel(channel)
                    .withInstallPlanApproval("Automatic")
                .endSpec()
                .build();
    }
}
