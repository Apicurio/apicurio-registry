package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.EntityOperatorSpec;
import io.strimzi.api.kafka.model.EntityOperatorSpecBuilder;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListener;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListenerBuilder;
import io.strimzi.api.kafka.model.listener.arraylistener.KafkaListenerType;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class KafkaResourceType implements ResourceType<Kafka> {
    private static final String KAFKA_VERSION = "3.1.0";
    private static final int KAFKA_REPLICAS = 3;
    private static final int ZOOKEEPER_REPLICAS = 1;

    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(13);
    }

    @Override
    public String getKind() {
        return ResourceKind.KAFKA;
    }

    @Override
    public Kafka get(String namespace, String name) {
        return getOperation()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static MixedOperation<Kafka, KubernetesResourceList<Kafka>, Resource<Kafka>> getOperation() {
        return Kubernetes.getResources(Kafka.class);
    }

    @Override
    public void create(Kafka resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(resource);
    }

    @Override
    public void createOrReplace(Kafka resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .createOrReplace(resource);
    }

    @Override
    public void delete(Kafka resource) throws Exception {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .delete();
    }

    @Override
    public boolean isReady(Kafka resource) {
        Kafka kafka = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (kafka == null || kafka.getStatus() == null) {
            return false;
        }

        return kafka
                .getStatus()
                .getConditions()
                .stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public boolean doesNotExist(Kafka resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(Kafka existing, Kafka newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static GenericKafkaListener getPlainListener() {
        return new GenericKafkaListenerBuilder()
                .withName("plain")
                .withPort(9092)
                .withType(KafkaListenerType.INTERNAL)
                .withTls(false)
                .build();
    }

    public static GenericKafkaListener getTlsListener() {
        return new GenericKafkaListenerBuilder()
                .withName("tls")
                .withPort(9093)
                .withType(KafkaListenerType.INTERNAL)
                .withTls(true)
                .withAuth(new KafkaListenerAuthenticationTls())
                .build();
    }

    public static GenericKafkaListener getScramListener() {
        return new GenericKafkaListenerBuilder()
                .withName("tls")
                .withPort(9093)
                .withType(KafkaListenerType.INTERNAL)
                .withTls(true)
                .withAuth(new KafkaListenerAuthenticationScramSha512())
                .build();
    }

    public static Map<String, Object> getDefaultConfig() {
        return new HashMap<>() {{
            put("offsets.topic.replication.factor", 3);
            put("transaction.state.log.replication.factor", 3);
            put("transaction.state.log.min.isr", 2);
        }};
    }

    public static PersistentClaimStorage getDefaultStorage() {
        return new PersistentClaimStorageBuilder()
                .withSize("100Gi")
                .withDeleteClaim(true)
                .build();
    }

    public static EntityOperatorSpec getDefaultEntityOperator() {
        return new EntityOperatorSpecBuilder()
                .withNewTopicOperator()
                .endTopicOperator()
                .withNewUserOperator()
                .endUserOperator()
                .build();
    }

    public static Kafka getDefaultNoAuth() {
        return new KafkaBuilder()
                .withNewMetadata()
                    .withName(Constants.KAFKA)
                    .withNamespace(Environment.NAMESPACE)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withVersion(KAFKA_VERSION)
                        .withReplicas(KAFKA_REPLICAS)
                        .withListeners(getPlainListener())
                        .withConfig(getDefaultConfig())
                        .withStorage(getDefaultStorage())
                    .endKafka()
                    .withNewZookeeper()
                        .withReplicas(ZOOKEEPER_REPLICAS)
                        .withStorage(getDefaultStorage())
                    .endZookeeper()
                    .withEntityOperator(getDefaultEntityOperator())
                .endSpec()
                .build();
    }

    public static Kafka getDefaultTLS() {
        return new KafkaBuilder()
                .withNewMetadata()
                    .withName(Constants.KAFKA)
                    .withNamespace(Environment.NAMESPACE)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withVersion(KAFKA_VERSION)
                        .withReplicas(KAFKA_REPLICAS)
                        .withListeners(
                                getPlainListener(),
                                getTlsListener()
                        )
                        .withConfig(getDefaultConfig())
                        .withStorage(getDefaultStorage())
                    .endKafka()
                    .withNewZookeeper()
                        .withReplicas(ZOOKEEPER_REPLICAS)
                        .withStorage(getDefaultStorage())
                    .endZookeeper()
                    .withEntityOperator(getDefaultEntityOperator())
                .endSpec()
                .build();
    }

    public static Kafka getDefaultSCRAM() {
        return new KafkaBuilder()
                .withNewMetadata()
                    .withName(Constants.KAFKA)
                    .withNamespace(Environment.NAMESPACE)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withVersion(KAFKA_VERSION)
                        .withReplicas(KAFKA_REPLICAS)
                        .withListeners(
                                getPlainListener(),
                                getScramListener()
                        )
                        .withConfig(getDefaultConfig())
                        .withStorage(getDefaultStorage())
                    .endKafka()
                    .withNewZookeeper()
                        .withReplicas(ZOOKEEPER_REPLICAS)
                        .withStorage(getDefaultStorage())
                    .endZookeeper()
                    .withEntityOperator(getDefaultEntityOperator())
                .endSpec()
                .build();
    }

    public static Kafka getDefaultByKind(KafkaKind kind) {
        if (KafkaKind.NO_AUTH.equals(kind)) {
            return getDefaultNoAuth();
        } else if (KafkaKind.TLS.equals(kind)) {
            return getDefaultTLS();
        } else if (KafkaKind.SCRAM.equals(kind)) {
            return getDefaultSCRAM();
        } else {
            throw new IllegalStateException("Unexpected value: " + kind);
        }
    }
}
