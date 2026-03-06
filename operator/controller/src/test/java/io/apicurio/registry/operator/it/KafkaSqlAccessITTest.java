package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
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
import java.util.ArrayList;
import java.util.List;

import static io.apicurio.registry.operator.Tags.KAFKA;
import static io.apicurio.registry.operator.Tags.SLOW;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(KAFKA)
@Tag(SLOW)
public class KafkaSqlAccessITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlAccessITTest.class);

    private static final String KAFKA_ACCESS_OPERATOR_VERSION = "0.2.0";
    private static final String KAFKA_ACCESS_OPERATOR_RAW_BASE =
            "https://raw.githubusercontent.com/strimzi/kafka-access-operator/"
                    + KAFKA_ACCESS_OPERATOR_VERSION + "/packaging/install/";
    private static final String[] KAFKA_ACCESS_INSTALL_FILES = {
            "010-ServiceAccount.yaml",
            "020-ClusterRole.yaml",
            "030-ClusterRoleBinding.yaml",
            "040-Crd-kafkaaccess.yaml",
            "050-Deployment.yaml"
    };

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
        List<HasMetadata> allResources = new ArrayList<>();
        for (String fileName : KAFKA_ACCESS_INSTALL_FILES) {
            var url = new URL(KAFKA_ACCESS_OPERATOR_RAW_BASE + fileName);
            try (BufferedInputStream in = new BufferedInputStream(url.openStream())) {
                Object parsed = Serialization.unmarshal(in);
                if (parsed instanceof List<?> list) {
                    list.stream().filter(HasMetadata.class::isInstance)
                            .map(HasMetadata.class::cast).forEach(allResources::add);
                } else if (parsed instanceof HasMetadata hm) {
                    allResources.add(hm);
                }
            }
        }
        allResources.forEach(r -> {
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

        // Wait for the KafkaAccess CRD to be fully established before proceeding
        await().atMost(Duration.ofMinutes(2)).ignoreExceptions().untilAsserted(() -> {
            var crd = client.apiextensions().v1().customResourceDefinitions()
                    .withName("kafkaaccesses.access.strimzi.io").get();
            assertThat(crd).isNotNull();
            assertThat(crd.getStatus().getConditions())
                    .filteredOn(c -> "Established".equals(c.getType()))
                    .anyMatch(c -> "True".equals(c.getStatus()));
        });

        // Wait for the Kafka Access Operator pod to be ready
        await().atMost(Duration.ofMinutes(2)).ignoreExceptions().untilAsserted(() -> {
            var pods = client.pods().inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", "kafka-access-operator").list().getItems();
            assertThat(pods).hasSize(1);
            assertThat(client.resource(pods.get(0)).isReady()).isTrue();
        });

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

        // Create the KafkaAccess resource using genericKubernetesResources with explicit
        // resource definition since there is no Java model for KafkaAccess on the classpath
        var kafkaAccessContext = new ResourceDefinitionContext.Builder()
                .withGroup("access.strimzi.io")
                .withVersion("v1alpha1")
                .withKind("KafkaAccess")
                .withPlural("kafkaaccesses")
                .withNamespaced(true)
                .build();
        var kafkaAccessResource = new io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder()
                .withApiVersion("access.strimzi.io/v1alpha1")
                .withKind("KafkaAccess")
                .withNewMetadata().withName("my-kafka-access").withNamespace(namespace).endMetadata()
                .addToAdditionalProperties("spec", java.util.Map.of(
                        "kafka", java.util.Map.of("name", "example-cluster", "listener", "tls"),
                        "user", java.util.Map.of("kind", "KafkaUser", "apiGroup", "kafka.strimzi.io",
                                "name", "apicurio-registry")))
                .build();
        client.genericKubernetesResources(kafkaAccessContext)
                .inNamespace(namespace)
                .resource(kafkaAccessResource)
                .create();

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

        // Wait for the registry deployment to come up and log "Using Kafka-SQL artifactStore".
        // Use a longer timeout because this test deploys a lot of infrastructure (Strimzi, Kafka Access
        // Operator, Kafka cluster) which can be slow on resource-constrained CI runners.
        await().atMost(ofMinutes(8)).ignoreExceptions().untilAsserted(() -> {
            var readyReplicas = client.apps().deployments().inNamespace(namespace)
                    .withName(registry.getMetadata().getName() + "-app-deployment").get().getStatus()
                    .getReadyReplicas();
            assertThat(readyReplicas).isNotNull().isEqualTo(1);
            var podName = client.pods().inNamespace(namespace).list().getItems().stream()
                    .map(pod -> pod.getMetadata().getName())
                    .filter(podN -> podN.startsWith(registry.getMetadata().getName() + "-app-deployment"))
                    .findFirst().get();
            assertThat(client.pods().inNamespace(namespace).withName(podName).getLog())
                    .contains("Using Kafka-SQL artifactStore");
        });
    }
}
