package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.listener.ListenerStatus;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class KafkaSQLITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSQLITTest.class);

    @BeforeAll
    public static void beforeAll() throws Exception {
        ITBase.before();
        applyStrimziResources();
    }

    @Test
    void testKafkaSQLPlain() {
        var kafka = deserialize("/k8s/examples/kafkasql/plain/ephemeral.kafka.yaml", Kafka.class);
        client.resource(kafka).create();

        await().ignoreExceptions().until(() -> {
            var status = client.resources(Kafka.class).inNamespace(namespace).withName("my-cluster").get()
                    .getStatus();
            assertThat(status.getConditions()).filteredOn(c -> "Ready".equals(c.getType())).singleElement()
                    .extracting("status", as(InstanceOfAssertFactories.STRING)).isEqualTo("True");
            return true;
        });

        // get plain bootstrap servers
        var status = client.resources(Kafka.class).inNamespace(namespace).withName("my-cluster").get()
                .getStatus();
        var bootstrapServers = status.getListeners().stream().filter(l -> "plain".equals(l.getName()))
                .map(ListenerStatus::getBootstrapServers).findFirst().get();

        var registry = deserialize("k8s/examples/kafkasql/plain/kafka-plain.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getKafkasql().setBootstrapServers(bootstrapServers);

        client.resource(registry).create();

        await().ignoreExceptions().until(() -> {
            assertThat(client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                    .getReadyReplicas().intValue()).isEqualTo(1);
            var podName = client.pods().inNamespace(namespace).list().getItems().stream()
                    .map(pod -> pod.getMetadata().getName())
                    .filter(podN -> podN.startsWith(registry.getMetadata().getName() + "-app-deployment"))
                    .findFirst().get();
            assertThat(client.pods().inNamespace(namespace).withName(podName).getLog())
                    .contains("Using Kafka-SQL artifactStore");

            return true;
        });
    }

    private static void applyStrimziResources() throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(
                new URL("https://strimzi.io/install/latest").openStream())) {
            List<HasMetadata> resources = Serialization.unmarshal(in);
            resources.forEach(r -> {
                if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                    var crb = (ClusterRoleBinding) r;
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                } else if (r.getKind().equals("RoleBinding") && r instanceof RoleBinding) {
                    var crb = (RoleBinding) r;
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                }
                log.info("Creating Strimzi in namespace {}", namespace);
                client.resource(r).inNamespace(namespace).createOrReplace();
            });
        }
    }
}
