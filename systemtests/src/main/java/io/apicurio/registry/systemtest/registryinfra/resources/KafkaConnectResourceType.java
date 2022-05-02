package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.framework.OperatorUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaConnectBuilder;
import io.strimzi.api.kafka.model.connect.build.DockerOutputBuilder;
import io.strimzi.api.kafka.model.connect.build.Plugin;
import io.strimzi.api.kafka.model.connect.build.PluginBuilder;
import io.strimzi.api.kafka.model.connect.build.TgzArtifactBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;

public class KafkaConnectResourceType implements ResourceType<KafkaConnect> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(7);
    }

    @Override
    public String getKind() {
        return ResourceKind.KAFKA_CONNECT;
    }

    @Override
    public KafkaConnect get(String namespace, String name) {
        return getOperation().inNamespace(namespace).withName(name).get();
    }

    public static MixedOperation<KafkaConnect, KubernetesResourceList<KafkaConnect>, Resource<KafkaConnect>> getOperation() {
        return Kubernetes.getClient().resources(KafkaConnect.class);
    }

    @Override
    public void create(KafkaConnect resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(KafkaConnect resource) {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(KafkaConnect resource) throws Exception {
        getOperation().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(KafkaConnect resource) {
        KafkaConnect kafkaConnect = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if(kafkaConnect == null || kafkaConnect.getStatus() == null) {
            return false;
        }

        return kafkaConnect.getStatus().getConditions().stream()
                .filter(condition -> condition.getType().equals("Ready"))
                .map(condition -> condition.getStatus().equals("True"))
                .findFirst()
                .orElse(false);
    }

    @Override
    public void refreshResource(KafkaConnect existing, KafkaConnect newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static KafkaConnect getDefault(String name, String namespace) {
        return new KafkaConnectBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withAnnotations(Collections.singletonMap("strimzi.io/use-connector-resources", "true"))
                .endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .withBootstrapServers("apicurio-registry-kafkasql-no-auth-kafka-bootstrap." + OperatorUtils.getStrimziOperatorNamespace() + ".svc.cluster.local:9092")
                    .withNewBuild()
                        .withOutput(new DockerOutputBuilder()
                                .withImage("image-registry.openshift-image-registry.svc.cluster.local:5000/" + namespace + "/apicurio-debezium:latest-ci")
                                .build()
                        )
                        .withPlugins(new ArrayList<Plugin>() {{
                            add(new PluginBuilder()
                                    .withName("debezium-connector-postgres")
                                    .withArtifacts(new TgzArtifactBuilder()
                                            .withUrl("https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/1.4.1.Final/debezium-connector-postgres-1.4.1.Final-plugin.tar.gz")
                                            .withSha512sum("99b0924aad98c6066e6bd22a05cf25789e6ba95ed53102d0c76e7775c3966ac8cf1b9a88e779685123c90e0bd1512d3bb986ad5052e8cae18cbcd2e8cf16f116")
                                            .build()
                                    ).build()
                            );
                            add(new PluginBuilder()
                                    .withName("apicurio-converters")
                                    .withArtifacts(new TgzArtifactBuilder()
                                            .withUrl(Environment.convertersUrl)
                                            .withSha512sum(Environment.convertersSha512sum)
                                            .build()
                                    ).build()
                            );
                        }})
                    .endBuild()
                .endSpec()
                .build();
    }

    public static KafkaConnect getDefault() {
        return getDefault("apicurio-registry-kafkasql-connect-no-auth", OperatorUtils.getStrimziOperatorNamespace());
    }
}
