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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static io.apicurio.registry.operator.resource.ResourceFactory.load;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class KafkaSQLITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSQLITTest.class);

    @BeforeAll
    public static void beforeAll() throws Exception {
        ITBase.beforeAll();
        applyStrimziResources(false);
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

    @AfterAll
    public static void afterAll() throws Exception {
        applyStrimziResources(true);
        ITBase.afterAll();
    }

    private static void applyStrimziResources(boolean delete) {
        String text = load("/k8s/examples/kafkasql/strimzi-cluster-operator-0.43.0.yaml");
        List<HasMetadata> resources = Serialization.unmarshal(text);
        resources.stream().forEach(r -> {
            if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                var crb = (ClusterRoleBinding) r;
                crb.getSubjects().stream().forEach(s -> s.setNamespace(namespace));
            } else if (r.getKind().equals("RoleBinding") && r instanceof RoleBinding) {
                var crb = (RoleBinding) r;
                crb.getSubjects().stream().forEach(s -> s.setNamespace(namespace));
            }
            if (!delete) {
                log.info("Creating Strimzi in namespace {}", namespace);
                client.resource(r).inNamespace(namespace).createOrReplace();
            } else {
                log.info("Deleting Strimzi from namespace {}", namespace);
                client.resource(r).inNamespace(namespace).delete();
            }
        });
    }
}
