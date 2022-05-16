package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.apicurio.registry.operator.api.model.ApicurioRegistrySpecConfigurationKafkaSecurityBuilder;
import io.apicurio.registry.operator.api.model.ApicurioRegistrySpecConfigurationSecurityBuilder;
import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.KeycloakUtils;
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
        return getOperation()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static MixedOperation<ApicurioRegistry, KubernetesResourceList<ApicurioRegistry>, Resource<ApicurioRegistry>>
    getOperation() {
        return Kubernetes.getClient().resources(ApicurioRegistry.class);
    }

    @Override
    public void create(ApicurioRegistry resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(resource);
    }

    @Override
    public void createOrReplace(ApicurioRegistry resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .createOrReplace(resource);
    }

    @Override
    public void delete(ApicurioRegistry resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .delete();
    }

    @Override
    public boolean isReady(ApicurioRegistry resource) {
        ApicurioRegistry apicurioRegistry = get(
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName()
        );

        if (apicurioRegistry == null || apicurioRegistry.getStatus() == null) {
            return false;
        }

        return apicurioRegistry
                .getStatus()
                .getConditions()
                .stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public boolean doesNotExist(ApicurioRegistry resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(ApicurioRegistry existing, ApicurioRegistry newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static ApicurioRegistry getDefaultMem(String name, String namespace) {
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
    }

    public static ApicurioRegistry getDefaultSql(String name, String namespace) {
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
    }
    public static ApicurioRegistry getDefaultKafkasql(String name, String namespace) {
        return new ApicurioRegistryBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewConfiguration()
                        .withPersistence("kafkasql")
                        .withNewKafkasql()
                            .withBootstrapServers(
                                    Constants.KAFKA + "-kafka-bootstrap." + Constants.TESTSUITE_NAMESPACE +
                                            ".svc.cluster.local:9092"
                            )
                        .endKafkasql()
                    .endConfiguration()
                .endSpec()
                .build();
    }

    public static ApicurioRegistry getDefaultMem(String name) {
        return getDefaultMem(name, Constants.TESTSUITE_NAMESPACE);
    }

    public static ApicurioRegistry getDefaultSql(String name) {
        return getDefaultSql(name, Constants.TESTSUITE_NAMESPACE);
    }

    public static ApicurioRegistry getDefaultKafkasql(String name) {
        return getDefaultKafkasql(name, Constants.TESTSUITE_NAMESPACE);
    }

    public static ApicurioRegistry getDefaultMem() {
        return getDefaultMem(Constants.REGISTRY);
    }

    public static ApicurioRegistry getDefaultSql() {
        return getDefaultSql(Constants.REGISTRY);
    }

    public static ApicurioRegistry getDefaultKafkasql() {
        return getDefaultKafkasql(Constants.REGISTRY);
    }

    public static void updateWithDefaultTLS(ApicurioRegistry apicurioRegistry) {
        apicurioRegistry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .setSecurity(
                        new ApicurioRegistrySpecConfigurationKafkaSecurityBuilder()
                                .withNewTls()
                                    .withKeystoreSecretName(Constants.KAFKA_USER + "-keystore")
                                    .withTruststoreSecretName(Constants.KAFKA + "-cluster-ca-truststore")
                                .endTls()
                                .build()
                );

        apicurioRegistry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .setBootstrapServers(
                        Constants.KAFKA + "-kafka-bootstrap." + Constants.TESTSUITE_NAMESPACE +
                                ".svc.cluster.local:9093"
                );
    }

    public static void updateWithDefaultSCRAM(ApicurioRegistry apicurioRegistry) {
        apicurioRegistry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .setSecurity(
                        new ApicurioRegistrySpecConfigurationKafkaSecurityBuilder()
                                .withNewScram()
                                    .withTruststoreSecretName(Constants.KAFKA + "-cluster-ca-truststore")
                                    .withPasswordSecretName(Constants.KAFKA_USER)
                                    .withUser(Constants.KAFKA_USER)
                                .endScram()
                                .build()
                );

        apicurioRegistry
                .getSpec()
                .getConfiguration()
                .getKafkasql()
                .setBootstrapServers(
                        Constants.KAFKA + "-kafka-bootstrap." + Constants.TESTSUITE_NAMESPACE +
                                ".svc.cluster.local:9093"
                );
    }

    public static void updateWithDefaultKeycloak(ApicurioRegistry apicurioRegistry) {
        apicurioRegistry
                .getSpec()
                .getConfiguration()
                .setSecurity(
                        new ApicurioRegistrySpecConfigurationSecurityBuilder()
                                .withNewKeycloak()
                                .withApiClientId(Constants.SSO_CLIENT_API)
                                .withUiClientId(Constants.SSO_CLIENT_UI)
                                .withRealm(Constants.SSO_REALM)
                                .withUrl(KeycloakUtils.getDefaultKeycloakURL())
                                .endKeycloak()
                                .build()
                );
    }
}