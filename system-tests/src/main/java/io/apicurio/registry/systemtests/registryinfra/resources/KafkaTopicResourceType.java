package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.KafkaTopic;
import io.strimzi.api.kafka.model.KafkaTopicBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;

public class KafkaTopicResourceType implements ResourceType<KafkaTopic> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.KAFKA_TOPIC;
    }

    @Override
    public KafkaTopic get(String namespace, String name) {
        return getOperation()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static MixedOperation<KafkaTopic, KubernetesResourceList<KafkaTopic>, Resource<KafkaTopic>> getOperation() {
        return Kubernetes.getResources(KafkaTopic.class);
    }

    @Override
    public void create(KafkaTopic resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(resource);
    }

    @Override
    public void createOrReplace(KafkaTopic resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .createOrReplace(resource);
    }

    @Override
    public void delete(KafkaTopic resource) throws Exception {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .delete();
    }

    @Override
    public boolean isReady(KafkaTopic resource) {
        KafkaTopic kafkaTopic = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (kafkaTopic == null || kafkaTopic.getStatus() == null) {
            return false;
        }

        return kafkaTopic
                .getStatus()
                .getConditions()
                .stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public boolean doesNotExist(KafkaTopic resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(KafkaTopic existing, KafkaTopic newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static KafkaTopic getDefault(String name, String namespace, String clusterName) {
        return new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withLabels(Collections.singletonMap("strimzi.io/cluster", clusterName))
                .endMetadata()
                .withNewSpec()
                    .withPartitions(3)
                    .withReplicas(3)
                    .withConfig(new HashMap<>() {{
                        put("retention.ms", 7200000);
                        put("segment.bytes", 1073741824);
                    }})
                .endSpec()
                .build();
    }
}
