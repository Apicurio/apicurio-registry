package io.apicurio.deployment;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_DEPLOYMENT;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_SECURED_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;
import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;

public class KafkaSqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSqlDeploymentManager.class);
    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    static void deployKafkaApp(String registryImage) throws Exception {
        if (Constants.isGroupActive(Constants.AUTH)) {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage);
        } else if (Constants.isGroupActive(Constants.KAFKA_SQL_SNAPSHOTTING)) {
            prepareKafkaSqlSnapshottingTests(registryImage);
        } else {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        }
    }

    private static void prepareKafkaSqlSnapshottingTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for KafkaSQL snapshot tests...");

        // First we deploy the Registry application with all the required data.
        prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        prepareSnapshotData(ApicurioRegistryBaseIT.getRegistryV3ApiUrl());

        // Once all the data has been introduced, the existing deployment is deleted so all the replicas are
        // re-created and restored from the snapshot.
        deleteRegistryDeployment();

        // Now we re-recreate the deployment so all the replicas are restored from the snapshot.
        LOGGER.info("Finished preparing data for the KafkaSQL snapshot tests.");
        prepareTestsInfra(null, APPLICATION_KAFKA_RESOURCES, false, registryImage);
    }

    private static void prepareSnapshotData(String registryBaseUrl) {
        // Create a bunch of artifacts and rules, so they're added to the snapshot.
        String simpleAvro = resourceToString("artifactTypes/avro/multi-field_v1.json");

        Vertx vertx = Vertx.vertx();
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryBaseUrl, vertx)
                // Seeding runs against a registry that may still be converging; the read-idle
                // timeout kills stalled connections and retries must cover a slow redeploy.
                // KafkaSQL applies every one of these 1000 concurrent creates through a single
                // ordered consumer thread, so a request near the tail of that backlog can
                // legitimately wait well past a "normal" request's timeout under loaded CI --
                // a shorter deadline here previously produced a client-side connection-closed
                // error that silently aborted seeding partway through (see the incident this
                // comment was added for: 999/1000 artifacts, snapshot never triggered).
                .requestTimeout(10_000, 180_000).retry(true, 5, 1_000));

        // Thousands of sequential blocking calls take 20+ minutes on a loaded CI
        // runner (this read as "the job hangs" more than once); fan out instead.
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            LOGGER.info("Creating 1000 artifacts that will be packed into a snapshot..");
            seedGroup(client, NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID, 1000, simpleAvro, executor);

            LOGGER.info("Creating kafkasql snapshot..");
            client.admin().snapshots().post();

            LOGGER.info("Adding new artifacts on top of the snapshot..");
            seedGroup(client, "default", 1000, simpleAvro, executor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while preparing snapshot data", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error preparing snapshot data", e.getCause());
        } finally {
            executor.shutdown();
            vertx.close();
        }
    }

    private static void seedGroup(RegistryClient client, String groupId, int count, String content,
            ExecutorService executor) throws InterruptedException, ExecutionException {
        // Prime the write path synchronously: on a freshly deployed KafkaSQL registry the
        // first write bootstraps the journal (topic creation etc.), and a parallel burst
        // of first-writes races that bootstrap and fails. One serial create first, then
        // fan out.
        createArtifactWithRule(client, groupId, content);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int idx = 1; idx < count; idx++) {
            futures.add(CompletableFuture.runAsync(() -> createArtifactWithRule(client, groupId, content),
                    executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
    }

    private static void createArtifactWithRule(RegistryClient client, String groupId, String content) {
        String artifactId = UUID.randomUUID().toString();
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId,
                ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON);
        client.groups().byGroupId(groupId).artifacts().post(createArtifact,
                config -> config.headers.add("X-Registry-ArtifactId", artifactId));
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("SYNTAX_ONLY");
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .post(createRule);
    }

    private static void deleteRegistryDeployment() {
        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient.apps().deployments()
                .inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT)
                .delete();

        // Wait for the deployment to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for deployment deletion", e);
        } finally {
            deployment.cancel(true);
        }
    }
}
