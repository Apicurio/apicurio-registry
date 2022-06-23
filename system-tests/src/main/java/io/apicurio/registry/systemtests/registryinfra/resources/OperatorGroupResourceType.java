package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;

import java.time.Duration;

public class OperatorGroupResourceType implements ResourceType<OperatorGroup> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.OPERATOR_GROUP;
    }

    @Override
    public OperatorGroup get(String namespace, String name) {
        return Kubernetes.getOperatorGroup(namespace, name);
    }

    @Override
    public void create(OperatorGroup resource) {
        Kubernetes.createOperatorGroup(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(OperatorGroup resource) {
        Kubernetes.createOrReplaceOperatorGroup(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(OperatorGroup resource) throws Exception {
        Kubernetes.deleteOperatorGroup(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(OperatorGroup resource) {
        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) != null;
    }

    @Override
    public boolean doesNotExist(OperatorGroup resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(OperatorGroup existing, OperatorGroup newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }
}
