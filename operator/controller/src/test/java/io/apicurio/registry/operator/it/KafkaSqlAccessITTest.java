package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import static io.apicurio.registry.operator.Tags.KAFKA;
import static io.apicurio.registry.operator.Tags.SLOW;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(KAFKA)
@Tag(SLOW)
public class KafkaSqlAccessITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlAccessITTest.class);

    private static boolean kafkaAccessOperatorInstalled = false;

    @BeforeAll
    public static void beforeAll() throws Exception {
        if (!strimziInstalled) {
            applyStrimziResources();
        }
        if (!kafkaAccessOperatorInstalled) {
            installKafkaAccessOperator();
        }
    }

    private static void installKafkaAccessOperator() throws IOException {
        var kafkaAccessOperatorURL = new URL(
                "https://github.com/strimzi/kafka-access-operator/releases/download/0.3.0/kafka-access-operator-0.3.0.yaml");
        try (BufferedInputStream in = new BufferedInputStream(kafkaAccessOperatorURL.openStream())) {
            List<HasMetadata> resources = Serialization.unmarshal(in);
            resources.forEach(r -> {
                if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding crb) {
                    crb.getSubjects().forEach(s -> s.setNamespace(namespace));
                } else if (r.getKind().equals("RoleBinding") && r instanceof RoleBinding rb) {
                    rb.getSubjects().forEach(s -> s.setNamespace(namespace));
                }
                log.info("Creating Kafka Access Operator resource kind {} in namespace {}", r.getKind(),
                        namespace);
                client.resource(r).inNamespace(namespace).createOrReplace();
                await().atMost(Duration.ofMinutes(2)).ignoreExceptions().until(() -> {
                    assertThat(client.resource(r).inNamespace(namespace).get()).isNotNull();
                    return true;
                });
            });
        }
        kafkaAccessOperatorInstalled = true;
    }

    @Test
    void testKafkaSQLAccess() {
        // Deploy the Kafka cluster
        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/access/example-cluster.kafka.yaml"))
                .create();
        final var clusterName = "example-cluster";

        // Wait for the Kafka broker pod to be ready
        await().ignoreExceptions().untilAsserted(() ->
                assertThat(client.pods().inNamespace(namespace).withName(clusterName + "-dual-role-0").get()
                        .getStatus().getConditions()).filteredOn(c -> "Ready".equals(c.getType()))
                        .map(PodCondition::getStatus).containsOnly("True"));

        // Create the KafkaUser
        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/access/apicurio-registry.kafkauser.yaml"))
                .inNamespace(namespace).create();

        final var userName = "apicurio-registry";

        // Wait for the KafkaUser secret to be created by the User Operator
        await().untilAsserted(
                () -> assertThat(client.secrets().inNamespace(namespace).withName(userName).get())
                        .isNotNull());

        // Create the KafkaAccess resource
        client.load(getClass().getResourceAsStream("/k8s/examples/kafkasql/access/example-kafkaaccess.yaml"))
                .inNamespace(namespace).create();

        final var kafkaAccessSecretName = "my-kafka-access";

        // Wait for the KafkaAccess binding secret to be created
        await().untilAsserted(
                () -> assertThat(client.secrets().inNamespace(namespace).withName(kafkaAccessSecretName).get())
                        .isNotNull());

        // Deploy the ApicurioRegistry CR with kafkaAccessSecretName
        var registry = deserialize("k8s/examples/kafkasql/access/example-kafkasql-access.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getStorage().getKafkasql().setKafkaAccessSecretName(kafkaAccessSecretName);

        client.resource(registry).create();

        // Wait for the registry deployment to come up and log "Using Kafka-SQL artifactStore"
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
}
