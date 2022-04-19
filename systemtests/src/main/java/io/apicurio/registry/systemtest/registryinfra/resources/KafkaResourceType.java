package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaBuilder;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListener;
import io.strimzi.api.kafka.model.listener.arraylistener.KafkaListenerType;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorage;

import java.util.HashMap;

public class KafkaResourceType implements ResourceType<Kafka> {
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
        Deployment deployment = Kubernetes.getClient().apps().deployments().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName() + "-entity-operator").get();

        if (deployment == null) {
            return false;
        }

        DeploymentSpec deploymentSpec = deployment.getSpec();
        DeploymentStatus deploymentStatus = deployment.getStatus();

        if (deploymentStatus == null || deploymentStatus.getReplicas() == null || deploymentStatus.getAvailableReplicas() == null) {
            return false;
        }

        if (deploymentSpec == null || deploymentSpec.getReplicas() == null) {
            return false;
        }

        return deploymentSpec.getReplicas().intValue() == deploymentStatus.getReplicas() && deploymentSpec.getReplicas() <= deploymentStatus.getAvailableReplicas();
    }

    @Override
    public void refreshResource(Kafka existing, Kafka newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Kafka getDefault(String name, String namespace) {
        return new KafkaBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withVersion("3.1.0")
                        .withReplicas(1)
                        .withListeners(new GenericKafkaListener() {{
                            setName("plain");
                            setPort(9092);
                            setType(KafkaListenerType.INTERNAL);
                            setTls(false);
                        }})
                        .withConfig(new HashMap<>() {{
                            put("offsets.topic.replication.factor", 1);
                            put("transaction.state.log.replication.factor", 1);
                            put("transaction.state.log.min.isr", 1);
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

    public static Kafka getDefault() {
        return getDefault("apicurio-registry-kafkasql", OperatorUtils.getStrimziOperatorNamespace());
    }
}
