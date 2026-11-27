package io.apicurio.tests.gitops;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.gitops.infra.GitOpsTestFixture;
import io.apicurio.tests.gitops.infra.GitOpsTopology;
import io.apicurio.tests.gitops.infra.GitPushHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.apicurio.deployment.Constants.GITOPS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(GITOPS)
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GitOpsTopologyIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitOpsTopologyIT.class);
    private static final int GIT_DAEMON_PORT = 9418;

    private GitOpsTopology topology;
    private RegistryClient registryClient;
    private GitPushHelper pushHelper;
    private Vertx vertx;
    private KubernetesClient kubernetesClient;
    private LocalPortForward portForward;

    private boolean isCsdMode() {
        return Boolean.parseBoolean(System.getProperty("gitops.topology.csd"));
    }

    @BeforeAll
    void setup() throws Exception {
        vertx = Vertx.vertx();

        if (isCsdMode()) {
            setupCsd();
        } else {
            setupCi();
        }

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData artifact = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID).get();
            assertEquals(GitOpsTestFixture.ARTIFACT_ID, artifact.getArtifactId());
            assertEquals(GitOpsTestFixture.GROUP_ID, artifact.getGroupId());
        });
        LOGGER.info("Topology ready — initial fixture data loaded");
    }

    private void setupCi() throws Exception {
        LOGGER.info("CI mode: starting Testcontainers topology");
        topology = new GitOpsTopology();
        topology.start();

        registryClient = RegistryClientFactory.create(
                RegistryClientOptions.create(topology.getRegistryUrl(), vertx)
                        .requestTimeout(10_000, 60_000)
                        .retry(true, 5, 250));

        pushHelper = new GitPushHelper(topology.getHostGitUrl());
    }

    private void setupCsd() {
        LOGGER.info("CSD mode: connecting to operator-deployed registry and git-server");
        final String namespace = System.getProperty("test.namespace",
                "apicurio-registry-e2e");

        final String registryUrl = TestUtils.getRegistryV3ApiUrl();
        registryClient = RegistryClientFactory.create(
                RegistryClientOptions.create(registryUrl, vertx)
                        .requestTimeout(10_000, 60_000)
                        .retry(true, 5, 250));

        kubernetesClient = new KubernetesClientBuilder().build();
        final var gitServerPods = kubernetesClient.pods().inNamespace(namespace)
                .withLabel("app", "gitops-git-server").list();
        if (gitServerPods.getItems().isEmpty()) {
            throw new IllegalStateException(
                    "No git-server pod found with label app=gitops-git-server in namespace " + namespace);
        }
        if (gitServerPods.getItems().size() > 1) {
            LOGGER.warn("Multiple git-server pods found ({}), using the first one",
                    gitServerPods.getItems().size());
        }
        final String podName = gitServerPods.getItems().get(0).getMetadata().getName();
        LOGGER.info("Port-forwarding to git-server pod '{}'", podName);

        portForward = kubernetesClient.pods().inNamespace(namespace)
                .withName(podName)
                .portForward(GIT_DAEMON_PORT);

        final String gitUrl = "git://localhost:" + portForward.getLocalPort() + "/default";
        LOGGER.info("Git-server available at {}", gitUrl);
        pushHelper = new GitPushHelper(gitUrl);
    }

    @AfterAll
    void teardown() {
        if (pushHelper != null) {
            pushHelper.close();
        }
        if (portForward != null) {
            try {
                portForward.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing port-forward", e);
            }
        }
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
        if (topology != null) {
            topology.close();
        }
    }

    @Test
    @Order(1)
    void testSyncAfterNewArtifact() throws Exception {
        final String artifactYaml = String.join("\n",
                "$type: artifact-v0",
                "registryIds: [" + GitOpsTestFixture.REGISTRY_ID + "]",
                "groupId: " + GitOpsTestFixture.GROUP_ID,
                "artifactId: orders",
                "artifactType: OPENAPI",
                "versions:",
                "  - version: \"1\"",
                "    state: ENABLED",
                "    content: ./content/orders-1.0.0.yaml"
        );

        final String contentYaml = String.join("\n",
                "openapi: \"3.0.2\"",
                "info:",
                "  title: Orders API",
                "  version: 1.0.0",
                "paths: {}"
        );

        pushHelper.addFile("artifact-orders.registry.yaml", artifactYaml);
        pushHelper.addFile("content/orders-1.0.0.yaml", contentYaml);
        pushHelper.pushCommit("Add orders artifact");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData artifact = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId("orders").get();
            assertEquals("orders", artifact.getArtifactId());
            assertEquals("OPENAPI", artifact.getArtifactType());
        });

        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() -> {
            try (InputStream content = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId("orders")
                    .versions().byVersionExpression("1").content().get()) {
                final String body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.contains("Orders API"),
                        "Expected content to contain 'Orders API' but got: " + body);
            }
        });
    }

    @Test
    @Order(2)
    void testSyncAfterContentUpdate() throws Exception {
        final String artifactYaml = String.join("\n",
                "$type: artifact-v0",
                "registryIds: [" + GitOpsTestFixture.REGISTRY_ID + "]",
                "groupId: " + GitOpsTestFixture.GROUP_ID,
                "artifactId: " + GitOpsTestFixture.ARTIFACT_ID,
                "artifactType: OPENAPI",
                "versions:",
                "  - version: \"1\"",
                "    state: ENABLED",
                "    content: ./content/petstore-1.0.0.yaml",
                "  - version: \"2\"",
                "    state: ENABLED",
                "    content: ./content/petstore-2.0.0.yaml",
                "rules:",
                "  - ruleType: COMPATIBILITY",
                "    config: BACKWARD"
        );

        final String v2Content = String.join("\n",
                "openapi: \"3.0.0\"",
                "info:",
                "  version: 2.0.0",
                "  title: Swagger Petstore v2",
                "paths: {}"
        );

        pushHelper.addFile("artifact-petstore.registry.yaml", artifactYaml);
        pushHelper.addFile("content/petstore-2.0.0.yaml", v2Content);
        pushHelper.pushCommit("Add petstore v2");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            try (InputStream content = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID)
                    .versions().byVersionExpression("2").content().get()) {
                final String body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.contains("Swagger Petstore v2"),
                        "Expected v2 content but got: " + body);
            }
        });

        try (InputStream v1Content = registryClient.groups()
                .byGroupId(GitOpsTestFixture.GROUP_ID)
                .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID)
                .versions().byVersionExpression("1").content().get()) {
            final String v1Body = new String(v1Content.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(v1Body.contains("Swagger Petstore"),
                    "v1 content should still be present");
        }
    }

    @Test
    @Order(3)
    void testErrorRecoveryFromInvalidYaml() throws Exception {
        pushHelper.addFile("registry.registry.yaml", "invalid: [unclosed");
        pushHelper.pushCommit("Break registry YAML");

        await().during(Duration.ofSeconds(15)).atMost(Duration.ofSeconds(20))
                .ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData artifact = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID).get();
            assertEquals(GitOpsTestFixture.ARTIFACT_ID, artifact.getArtifactId(),
                    "Registry should still serve original data after invalid push");
        });

        final String validRegistry = String.join("\n",
                "$type: registry-v0",
                "registryId: " + GitOpsTestFixture.REGISTRY_ID,
                "globalRules:",
                "  - ruleType: VALIDITY",
                "    config: FULL",
                "properties: []"
        );

        pushHelper.addFile("registry.registry.yaml", validRegistry);
        pushHelper.pushCommit("Restore valid registry YAML");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData meta = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID).get();
            assertEquals(GitOpsTestFixture.ARTIFACT_ID, meta.getArtifactId());
        });
    }

    @Test
    @Order(4)
    void testContentTypeAutoDetection() throws Exception {
        final String avroArtifact = String.join("\n",
                "$type: artifact-v0",
                "registryIds: [" + GitOpsTestFixture.REGISTRY_ID + "]",
                "groupId: " + GitOpsTestFixture.GROUP_ID,
                "artifactId: address-schema",
                "artifactType: AVRO",
                "versions:",
                "  - version: \"1\"",
                "    state: ENABLED",
                "    content: ./content/address.avsc"
        );

        final String avroContent = String.join("\n",
                "{",
                "  \"type\": \"record\",",
                "  \"name\": \"Address\",",
                "  \"namespace\": \"com.example\",",
                "  \"fields\": [",
                "    {\"name\": \"street\", \"type\": \"string\"},",
                "    {\"name\": \"city\", \"type\": \"string\"}",
                "  ]",
                "}"
        );

        final String jsonSchemaArtifact = String.join("\n",
                "$type: artifact-v0",
                "registryIds: [" + GitOpsTestFixture.REGISTRY_ID + "]",
                "groupId: " + GitOpsTestFixture.GROUP_ID,
                "artifactId: config-schema",
                "artifactType: JSON",
                "versions:",
                "  - version: \"1\"",
                "    state: ENABLED",
                "    content: ./content/config.json"
        );

        final String jsonSchemaContent = String.join("\n",
                "{",
                "  \"$schema\": \"http://json-schema.org/draft-07/schema#\",",
                "  \"type\": \"object\",",
                "  \"properties\": {",
                "    \"name\": {\"type\": \"string\"}",
                "  }",
                "}"
        );

        pushHelper.addFile("artifact-address.registry.yaml", avroArtifact);
        pushHelper.addFile("content/address.avsc", avroContent);
        pushHelper.addFile("artifact-config.registry.yaml", jsonSchemaArtifact);
        pushHelper.addFile("content/config.json", jsonSchemaContent);
        pushHelper.pushCommit("Add AVRO and JSON Schema artifacts");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData avroMeta = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId("address-schema").get();
            assertEquals("AVRO", avroMeta.getArtifactType());
        });

        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData jsonMeta = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId("config-schema").get();
            assertEquals("JSON", jsonMeta.getArtifactType());
        });
    }

    @Test
    @Order(5)
    void testSyncAfterArtifactDeletion() throws Exception {
        final String artifactYaml = String.join("\n",
                "$type: artifact-v0",
                "registryIds: [" + GitOpsTestFixture.REGISTRY_ID + "]",
                "groupId: " + GitOpsTestFixture.GROUP_ID,
                "artifactId: temp-artifact",
                "artifactType: OPENAPI",
                "versions:",
                "  - version: \"1\"",
                "    state: ENABLED",
                "    content: ./content/temp-1.0.0.yaml"
        );
        final String tempContent = String.join("\n",
                "openapi: \"3.0.2\"",
                "info:",
                "  title: Temporary API",
                "  version: 1.0.0",
                "paths: {}"
        );

        pushHelper.addFile("artifact-temp.registry.yaml", artifactYaml);
        pushHelper.addFile("content/temp-1.0.0.yaml", tempContent);
        pushHelper.pushCommit("Add temp artifact");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData meta = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId("temp-artifact").get();
            assertEquals("temp-artifact", meta.getArtifactId());
        });

        pushHelper.removeFile("artifact-temp.registry.yaml");
        pushHelper.removeFile("content/temp-1.0.0.yaml");
        pushHelper.pushCommit("Remove temp artifact");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final var searchResults = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID).artifacts().get();
            final boolean found = searchResults.getArtifacts().stream()
                    .anyMatch(a -> "temp-artifact".equals(a.getArtifactId()));
            assertFalse(found, "Deleted artifact should no longer appear in search results");
        });
    }

    @Test
    @Order(6)
    void testSyncAfterRuleChange() throws Exception {
        final String registryWithNewRule = String.join("\n",
                "$type: registry-v0",
                "registryId: " + GitOpsTestFixture.REGISTRY_ID,
                "globalRules:",
                "  - ruleType: VALIDITY",
                "    config: FULL",
                "  - ruleType: COMPATIBILITY",
                "    config: BACKWARD",
                "properties: []"
        );

        pushHelper.addFile("registry.registry.yaml", registryWithNewRule);
        pushHelper.pushCommit("Add COMPATIBILITY global rule");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final var rules = registryClient.admin().rules().get();
            assertTrue(rules.contains(RuleType.COMPATIBILITY),
                    "Expected COMPATIBILITY global rule after sync");
            assertTrue(rules.contains(RuleType.VALIDITY),
                    "VALIDITY rule should still be present");
        });

        final var compatRule = registryClient.admin().rules()
                .byRuleType(RuleType.COMPATIBILITY.getValue()).get();
        assertEquals("BACKWARD", compatRule.getConfig());
    }

    @Test
    @Order(7)
    void testRecoveryFromMissingContentFile() throws Exception {
        final String artifactYaml = String.join("\n",
                "$type: artifact-v0",
                "registryIds: [" + GitOpsTestFixture.REGISTRY_ID + "]",
                "groupId: " + GitOpsTestFixture.GROUP_ID,
                "artifactId: broken-ref",
                "artifactType: OPENAPI",
                "versions:",
                "  - version: \"1\"",
                "    state: ENABLED",
                "    content: ./content/nonexistent-file.yaml"
        );

        pushHelper.addFile("artifact-broken-ref.registry.yaml", artifactYaml);
        pushHelper.pushCommit("Add artifact referencing missing content file");

        await().during(Duration.ofSeconds(15)).atMost(Duration.ofSeconds(20))
                .ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData petstore = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID).get();
            assertEquals(GitOpsTestFixture.ARTIFACT_ID, petstore.getArtifactId(),
                    "Registry should still serve existing data after a missing content file push");
        });

        pushHelper.removeFile("artifact-broken-ref.registry.yaml");
        pushHelper.pushCommit("Remove broken artifact reference");

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            final ArtifactMetaData meta = registryClient.groups()
                    .byGroupId(GitOpsTestFixture.GROUP_ID)
                    .artifacts().byArtifactId(GitOpsTestFixture.ARTIFACT_ID).get();
            assertEquals(GitOpsTestFixture.ARTIFACT_ID, meta.getArtifactId());
        });
    }
}
