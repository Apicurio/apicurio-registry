package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserBuilder;
import io.strimzi.api.kafka.model.KafkaUserScramSha512ClientAuthentication;
import io.strimzi.api.kafka.model.KafkaUserTlsClientAuthentication;

import java.time.Duration;
import java.util.Collections;

public class KafkaUserResourceType implements ResourceType<KafkaUser> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.KAFKA_USER;
    }

    @Override
    public KafkaUser get(String namespace, String name) {
        return getOperation().inNamespace(namespace).withName(name).get();
    }

    public static MixedOperation<KafkaUser, KubernetesResourceList<KafkaUser>, Resource<KafkaUser>> getOperation() {
        return Kubernetes.getClient().resources(KafkaUser.class);
    }

    @Override
    public void create(KafkaUser resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(KafkaUser resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(KafkaUser resource) throws Exception {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(KafkaUser resource) {
        KafkaUser kafkaUser = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if(kafkaUser == null || kafkaUser.getStatus() == null) {
            return false;
        }

        return kafkaUser.getStatus().getConditions().stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public void refreshResource(KafkaUser existing, KafkaUser newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static KafkaUser getDefaultByKind(String name, String namespace, String clusterName, KafkaKind kafkaKind) {
        return new KafkaUserBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withLabels(Collections.singletonMap("strimzi.io/cluster", clusterName))
                .endMetadata()
                .withNewSpec()
                    .withAuthentication(KafkaKind.TLS.equals(kafkaKind) ? new KafkaUserTlsClientAuthentication() : new KafkaUserScramSha512ClientAuthentication())
                .endSpec()
                .build();
    }

    public static KafkaUser getDefaultTLS(String name, String namespace, String clusterName) {
        return getDefaultByKind(name, namespace, clusterName, KafkaKind.TLS);
    }

    public static KafkaUser getDefaultSCRAM(String name, String namespace, String clusterName) {
        return getDefaultByKind(name, namespace, clusterName, KafkaKind.SCRAM);
    }

    public static KafkaUser getDefaultTLS() {
        return getDefaultByKind("apicurio-registry-kafka-user-secured-tls", OperatorUtils.getStrimziOperatorNamespace(), "apicurio-registry-kafkasql-tls", KafkaKind.TLS);
    }

    public static KafkaUser getDefaultSCRAM() {
        return getDefaultByKind("apicurio-registry-kafka-user-secured-scram", OperatorUtils.getStrimziOperatorNamespace(), "apicurio-registry-kafkasql-scram", KafkaKind.SCRAM);
    }
}
