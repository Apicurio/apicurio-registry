package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.apicurio.registry.operator.api.model.ApicurioRegistrySpecConfigurationKafkaSecurityBuilder;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.time.Duration;

public class ApicurioRegistryResourceType implements ResourceType<ApicurioRegistry> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(3);
    }

    @Override
    public String getKind() {
        return ResourceKind.APICURIO_REGISTRY;
    }

    @Override
    public ApicurioRegistry get(String namespace, String name) {
        return getOperation().inNamespace(namespace).withName(name).get();
    }

    public static MixedOperation<ApicurioRegistry, KubernetesResourceList<ApicurioRegistry>, Resource<ApicurioRegistry>> getOperation() {
        return Kubernetes.getClient().resources(ApicurioRegistry.class);
    }

    @Override
    public void create(ApicurioRegistry resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(ApicurioRegistry resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(ApicurioRegistry resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(ApicurioRegistry resource) {
        ApicurioRegistry apicurioRegistry = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (apicurioRegistry == null || apicurioRegistry.getStatus() == null) {
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

    /** Get default instances **/

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
                                    .withUrl("jdbc:postgresql://postgresql.postgresql.svc.cluster.local:5432/postgresdb")
                                    .withUserName("postgresuser")
                                    .withPassword("postgrespassword")
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
                                .withBootstrapServers("apicurio-registry-kafkasql-tls-kafka-bootstrap." + OperatorUtils.getStrimziOperatorNamespace() + ".svc.cluster.local:9092")
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

    public static void updateWithDefaultTLS(ApicurioRegistry apicurioRegistry) {
        apicurioRegistry.getSpec().getConfiguration().getKafkasql().setSecurity(new ApicurioRegistrySpecConfigurationKafkaSecurityBuilder()
                .withNewTls()
                    .withKeystoreSecretName("apicurio-registry-kafka-user-secured-tls-keystore")
                    .withTruststoreSecretName("apicurio-registry-kafkasql-tls-cluster-ca-truststore")
                .endTls()
                .build()
        );

        apicurioRegistry.getSpec().getConfiguration().getKafkasql().setBootstrapServers("apicurio-registry-kafkasql-tls-kafka-bootstrap." + OperatorUtils.getStrimziOperatorNamespace() + ".svc.cluster.local:9093");
    }

    public static void updateWithDefaultSCRAM(ApicurioRegistry apicurioRegistry) {
        apicurioRegistry.getSpec().getConfiguration().getKafkasql().setSecurity(new ApicurioRegistrySpecConfigurationKafkaSecurityBuilder()
                .withNewScram()
                    .withTruststoreSecretName("apicurio-registry-kafkasql-scram-cluster-ca-truststore")
                    .withPasswordSecretName("apicurio-registry-kafka-user-secured-scram")
                    .withUser("apicurio-registry-kafka-user-secured-scram")
                .endScram()
                .build()
        );

        apicurioRegistry.getSpec().getConfiguration().getKafkasql().setBootstrapServers("apicurio-registry-kafkasql-scram-kafka-bootstrap." + OperatorUtils.getStrimziOperatorNamespace() + ".svc.cluster.local:9093");
    }
}