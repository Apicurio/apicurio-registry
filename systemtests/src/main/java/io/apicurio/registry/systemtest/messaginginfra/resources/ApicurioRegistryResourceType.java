package io.apicurio.registry.systemtest.messaginginfra.resources;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class ApicurioRegistryResourceType implements ResourceType<ApicurioRegistry> {

    @Override
    public String getKind() {
        return ResourceKind.APICURIO_REGISTRY;
    }

    @Override
    public ApicurioRegistry get(String namespace, String name) {
        return getOperation().inNamespace(namespace).withName(name).get();
    }

    public static ApicurioRegistry getDefault() {
        return new ApicurioRegistryBuilder()
                .withNewMetadata()
                .withName("apicurio-registry-test")
                .withNamespace("apicurio-registry-test-namespace")
                .endMetadata()
                .withNewSpec()
                .withNewConfiguration()
                .withPersistence("kafkasql")
                .withNewKafkasql()
                .withBootstrapServers("my-cluster-kafka-bootstrap.registry-example-kafkasql-plain.svc:9092")
                .endKafkasql()
                .endConfiguration()
                .endSpec()
                .build();
    }

    public static MixedOperation<ApicurioRegistry, KubernetesResourceList<ApicurioRegistry>, Resource<ApicurioRegistry>> getOperation() {
        return Kubernetes.getClient().resources(ApicurioRegistry.class);
    }

    @Override
    public void create(ApicurioRegistry resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void delete(ApicurioRegistry resource) throws Exception {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(ApicurioRegistry resource) {
        return false;
    }

    @Override
    public void refreshResource(ApicurioRegistry existing, ApicurioRegistry newResource) {

    }
}