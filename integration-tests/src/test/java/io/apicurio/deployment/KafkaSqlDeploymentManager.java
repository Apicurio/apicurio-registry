package io.apicurio.deployment;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.kiota.http.vertx.VertXRequestAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;
import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;

public class KafkaSqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSqlDeploymentManager.class);
    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    static void deployKafkaApp(String registryImage) throws Exception {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage);
        }
        else if (Constants.TEST_PROFILE.equals(Constants.KAFKA_SQL_SNAPSHOTTING)) {
            prepareKafkaSqlSnapshottingTests(registryImage);
        }
        else {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        }
    }

    private static void prepareKafkaSqlSnapshottingTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for KafkaSQL snapshot tests...");

        //First we deploy the Registry application with all the required data.
        prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        prepareSnapshotData(ApicurioRegistryBaseIT.getRegistryV3ApiUrl());

        //Once all the data has been introduced, the existing deployment is deleted so all the replicas are re-created and restored from the snapshot.
        deleteRegistryDeployment();

        //Now we re-recreate the deployment so all the replicas are restored from the snapshot.
        LOGGER.info("Finished preparing data for the KafkaSQL snapshot tests.");
        prepareTestsInfra(null, APPLICATION_KAFKA_RESOURCES, false, registryImage);
    }

    private static void prepareSnapshotData(String registryBaseUrl) {
        //Create a bunch of artifacts and rules, so they're added to the snapshot.
        String simpleAvro = resourceToString("artifactTypes/avro/multi-field_v1.json");

        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(registryBaseUrl);
        RegistryClient client = new RegistryClient(adapter);

        LOGGER.info("Creating 1000 artifacts that will be packed into a snapshot..");
        for (int idx = 0; idx < 1000; idx++) {
            String artifactId = UUID.randomUUID().toString();
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, simpleAvro,
                    ContentTypes.APPLICATION_JSON);
            client.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts()
                    .post(createArtifact, config -> config.headers.add("X-Registry-ArtifactId", artifactId));
            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("SYNTAX_ONLY");
            client.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().byArtifactId(artifactId).rules().post(rule);
        }

        LOGGER.info("Creating kafkasql snapshot..");
        client.admin().config().triggerSnapshot().get();

        LOGGER.info("Adding new artifacts on top of the snapshot..");
        for (int idx = 0; idx < 1000; idx++) {
            String artifactId = UUID.randomUUID().toString();
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, simpleAvro,
                    ContentTypes.APPLICATION_JSON);
            client.groups().byGroupId("default").artifacts()
                    .post(createArtifact, config -> config.headers.add("X-Registry-ArtifactId", artifactId));
            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("SYNTAX_ONLY");
            client.groups().byGroupId("default").artifacts().byArtifactId(artifactId).rules().post(rule);
        }
    }

    private static void deleteRegistryDeployment() {
        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE)
                .withName(APPLICATION_DEPLOYMENT);

        kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();

        //Wait for the deployment to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
        }
        catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for deployment deletion", e);
        }
        finally {
            deployment.cancel(true);
        }
    }
}
