package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.api.kafka.model.KafkaUserAuthentication;
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
        return Kubernetes.getResources(KafkaUser.class);
    }

    @Override
    public void create(KafkaUser resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(resource);
    }

    @Override
    public void createOrReplace(KafkaUser resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .createOrReplace(resource);
    }

    @Override
    public void delete(KafkaUser resource) throws Exception {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .delete();
    }

    @Override
    public boolean isReady(KafkaUser resource) {
        KafkaUser kafkaUser = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (kafkaUser == null || kafkaUser.getStatus() == null) {
            return false;
        }

        return kafkaUser
                .getStatus()
                .getConditions()
                .stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public boolean doesNotExist(KafkaUser resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(KafkaUser existing, KafkaUser newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static KafkaUser getDefaultByKind(String name, String namespace, String clusterName, KafkaKind kafkaKind) {
        KafkaUserAuthentication kafkaUserAuthentication = null;

        if (KafkaKind.TLS.equals(kafkaKind)) {
            kafkaUserAuthentication = new KafkaUserTlsClientAuthentication();
        } else if (KafkaKind.SCRAM.equals(kafkaKind)) {
            kafkaUserAuthentication = new KafkaUserScramSha512ClientAuthentication();
        }

        return new KafkaUserBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withLabels(Collections.singletonMap("strimzi.io/cluster", clusterName))
                .endMetadata()
                .withNewSpec()
                    .withAuthentication(kafkaUserAuthentication)
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
        return getDefaultByKind(
                Constants.KAFKA_USER,
                Environment.NAMESPACE,
                Constants.KAFKA,
                KafkaKind.TLS
        );
    }

    public static KafkaUser getDefaultSCRAM() {
        return getDefaultByKind(
                Constants.KAFKA_USER,
                Environment.NAMESPACE,
                Constants.KAFKA,
                KafkaKind.SCRAM
        );
    }
}
