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

    public static ApicurioRegistry getDefaultByKind(String name, String namespace, String kind) {
        if (PersistenceKind.MEM.equals(kind)) {
            return new ApicurioRegistryBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                    .withNewConfiguration()
                    .withPersistence("mem")
                    .endConfiguration()
                    .endSpec()
                    .build();
        } else if (PersistenceKind.SQL.equals(kind)) {
            return new ApicurioRegistryBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                    .withNewConfiguration()
                    .withPersistence("sql")
                    .withNewSql()
                    .withNewDataSource()
                    .withUrl("jdbc:postgresql://<service name>.<namespace>.svc:5432/<database name>")
                    .withUsername("postgres")
                    .withPassword("<password>")
                    .endDataSource()
                    .endSql()
                    .endConfiguration()
                    .endSpec()
                    .build();
        } else if (PersistenceKind.KAFKA_SQL.equals(kind)) {
            return new ApicurioRegistryBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
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
        throw new IllegalStateException("Unexpected value: " + kind);
    }

    public static ApicurioRegistry getDefaultMem(String name, String namespace) {
        return getDefaultByKind(name, namespace, PersistenceKind.MEM);
    }

    public static ApicurioRegistry getDefaultSql(String name, String namespace) {
        return getDefaultByKind(name, namespace, PersistenceKind.SQL);
    }
    public static ApicurioRegistry getDefaultKafkasql(String name, String namespace) {
        return getDefaultByKind(name, namespace, PersistenceKind.KAFKA_SQL);
    }

    public static ApicurioRegistry getDefaultMem(String name) {
        return getDefaultMem(name, "apicurio-registry-test-namespace-mem");
    }

    public static ApicurioRegistry getDefaultSql(String name) {
        return getDefaultSql(name, "apicurio-registry-test-namespace-sql");
    }

    public static ApicurioRegistry getDefaultKafkasql(String name) {
        return getDefaultKafkasql(name, "apicurio-registry-test-namespace-kafkasql");
    }

    public static ApicurioRegistry getDefaultMem() {
        return getDefaultMem("apicurio-registry-test-instance-mem");
    }

    public static ApicurioRegistry getDefaultSql() {
        return getDefaultSql("apicurio-registry-test-instance-sql");
    }

    public static ApicurioRegistry getDefaultKafkasql() {
        return getDefaultKafkasql("apicurio-registry-test-instance-kafkasql");
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
        ApicurioRegistry apicurioRegistry = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (apicurioRegistry == null) {
            return false;
        }

        return apicurioRegistry.getStatus().getConditions().stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public void refreshResource(ApicurioRegistry existing, ApicurioRegistry newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }
}