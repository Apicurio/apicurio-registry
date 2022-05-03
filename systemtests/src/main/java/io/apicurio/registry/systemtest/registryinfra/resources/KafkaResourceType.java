package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationScramSha512;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationTls;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListener;
import io.strimzi.api.kafka.model.listener.arraylistener.KafkaListenerType;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;

import java.time.Duration;
import java.util.HashMap;

public class KafkaResourceType implements ResourceType<Kafka> {
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
        return getOperation().inNamespace(namespace).withName(name).get();
    }

    public static MixedOperation<Kafka, KubernetesResourceList<Kafka>, Resource<Kafka>> getOperation() {
        return Kubernetes.getClient().resources(Kafka.class);
    }

    @Override
    public void create(Kafka resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(Kafka resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(Kafka resource) throws Exception {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(Kafka resource) {
        Kafka kafka = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (kafka == null || kafka.getStatus() == null) {
            return false;
        }

        return kafka.getStatus().getConditions().stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public void refreshResource(Kafka existing, Kafka newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Kafka getDefaultByKind(String name, String namespace, KafkaKind kafkaKind) {
        if(KafkaKind.NO_AUTH.equals(kafkaKind)) {
            return new KafkaBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                        .withNewKafka()
                            .withVersion("3.1.0")
                            .withReplicas(3)
                            .withListeners(new GenericKafkaListener() {{
                                setName("plain");
                                setPort(9092);
                                setType(KafkaListenerType.INTERNAL);
                                setTls(false);
                            }})
                            .withConfig(new HashMap<>() {{
                                put("offsets.topic.replication.factor", 3);
                                put("transaction.state.log.replication.factor", 3);
                                put("transaction.state.log.min.isr", 2);
                            }})
                            .withStorage(new PersistentClaimStorage() {{
                                setSize("100Gi");
                                setDeleteClaim(true);
                            }})
                        .endKafka()
                        .withNewZookeeper()
                            .withReplicas(1)
                            .withStorage(new PersistentClaimStorage() {{
                                setSize("100Gi");
                                setDeleteClaim(true);
                            }})
                        .endZookeeper()
                        .withNewEntityOperator()
                            .withNewTopicOperator()
                            .endTopicOperator()
                            .withNewUserOperator()
                            .endUserOperator()
                        .endEntityOperator()
                    .endSpec()
                    .build();
        } else if(KafkaKind.TLS.equals(kafkaKind) || KafkaKind.SCRAM.equals(kafkaKind)) {
            return new KafkaBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                        .withNewKafka()
                            .withVersion("3.1.0")
                            .withReplicas(3)
                            .withListeners(
                                    new GenericKafkaListener() {{
                                        setName("plain");
                                        setPort(9092);
                                        setType(KafkaListenerType.INTERNAL);
                                        setTls(false);
                                    }},
                                    new GenericKafkaListener() {{
                                        setName("tls");
                                        setPort(9093);
                                        setType(KafkaListenerType.INTERNAL);
                                        setTls(true);
                                        setAuth(KafkaKind.TLS.equals(kafkaKind) ? new KafkaListenerAuthenticationTls() : new KafkaListenerAuthenticationScramSha512());
                                    }}
                            )
                            .withConfig(new HashMap<>() {{
                                put("offsets.topic.replication.factor", 3);
                                put("transaction.state.log.replication.factor", 3);
                                put("transaction.state.log.min.isr", 2);
                            }})
                            .withStorage(new PersistentClaimStorage() {{
                                setSize("100Gi");
                                setDeleteClaim(true);
                            }})
                        .endKafka()
                        .withNewZookeeper()
                            .withReplicas(1)
                            .withStorage(new PersistentClaimStorage() {{
                                setSize("100Gi");
                                setDeleteClaim(true);
                            }})
                        .endZookeeper()
                        .withNewEntityOperator()
                            .withNewTopicOperator()
                            .endTopicOperator()
                            .withNewUserOperator()
                            .endUserOperator()
                        .endEntityOperator()
                    .endSpec()
                    .build();
        }
        throw new IllegalStateException("Unexpected value: " + kafkaKind);
    }

    public static Kafka getDefaultNoAuth() {
        return getDefaultByKind("apicurio-registry-kafkasql-no-auth", Environment.strimziOperatorNamespace, KafkaKind.NO_AUTH);
    }

    public static Kafka getDefaultTLS() {
        return getDefaultByKind("apicurio-registry-kafkasql-tls", Environment.strimziOperatorNamespace, KafkaKind.TLS);
    }

    public static Kafka getDefaultSCRAM() {
        return getDefaultByKind("apicurio-registry-kafkasql-scram", Environment.strimziOperatorNamespace, KafkaKind.SCRAM);
    }
}
