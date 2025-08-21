/*
 * Copyright 2023 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.deployment;

import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;
import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;

public class KafkaSqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSqlDeploymentManager.class);

    static void deployKafkaApp(String registryImage) throws Exception {
        LOGGER.info("Deploying KafkaSQL-based Registry variant with test profile: {}", Constants.TEST_PROFILE);
        
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                LOGGER.info("Configuring KafkaSQL Registry with authentication (Keycloak) enabled");
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                LOGGER.info("Configuring KafkaSQL Registry with multitenancy support enabled");
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            case Constants.KAFKA_SQL:
                LOGGER.info("Configuring KafkaSQL Registry for database upgrade testing");
                prepareKafkaDbUpgradeTests(registryImage);
                break;
            case Constants.KAFKASQL_MANUAL:
                LOGGER.info("Skipping automatic deployment for manual KafkaSQL testing");
                break;
            default:
                LOGGER.info("Configuring standard KafkaSQL Registry deployment");
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
                break;
        }
        LOGGER.info("KafkaSQL Registry variant deployment completed successfully");
    }

    private static void prepareKafkaDbUpgradeTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for KafkaSQL DB Upgrade migration tests ##################################################");

        //For the migration tests first we deploy the 2.1 version and add the required data.
        LOGGER.info("Phase 1: Deploying KafkaSQL Registry v2.1 with Kafka storage for initial data setup");
        prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_2_1_KAFKA_RESOURCES, false, null, false);
        LOGGER.info("Phase 1: Populating v2.1 KafkaSQL Registry with migration test data");
        prepareKafkaSqlMigrationData(ApicurioRegistryBaseIT.getRegistryBaseUrl());

        //Once all the data has been introduced, the old deployment is deleted.
        LOGGER.info("Phase 1: Removing v2.1 KafkaSQL Registry deployment to prepare for v2.4 upgrade");
        deleteRegistryDeployment();

        //The Registry version 2.3 is deployed, the version introducing artifact references.
        LOGGER.info("Phase 2: Deploying KafkaSQL Registry v2.4 for references migration testing");
        prepareTestsInfra(null, APPLICATION_2_4_KAFKA_RESOURCES, false, null, false);
        LOGGER.info("Phase 2: Adding artifact references test data to v2.4 KafkaSQL Registry");
        prepareKafkaSqlReferencesMigrationData(ApicurioRegistryBaseIT.getRegistryBaseUrl());

        //Once the references data is ready, we delete this old deployment and finally the current one is deployed.
        LOGGER.info("Phase 2: Removing v2.4 KafkaSQL Registry deployment to prepare for current version");
        deleteRegistryDeployment();

        LOGGER.info("Phase 3: Deploying current KafkaSQL Registry version for upgrade validation");
        prepareTestsInfra(null, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
        LOGGER.info("Finished preparing data for the KafkaSQL DB Upgrade tests ##################################################");
    }

    private static void deleteRegistryDeployment() {
        LOGGER.info("Deleting KafkaSQL Registry deployment: {} from namespace: {}", APPLICATION_DEPLOYMENT, TEST_NAMESPACE);
        
        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();
        LOGGER.info("KafkaSQL Registry deployment deletion initiated, waiting for completion (timeout: 60 seconds)...");

        //Wait for the deployment to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
            LOGGER.info("KafkaSQL Registry deployment {} successfully deleted", APPLICATION_DEPLOYMENT);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for KafkaSQL deployment {} deletion", APPLICATION_DEPLOYMENT, e);
        } finally {
            deployment.cancel(true);
        }
    }

    private static void prepareKafkaSqlMigrationData(String registryBaseUrl) throws Exception {
        LOGGER.info("Initializing migration test data for KafkaSQL Registry v2.1");
        LOGGER.info("  -> Registry Base URL: {}", registryBaseUrl);
        
        var registryClient = RegistryClientFactory.create(registryBaseUrl);

        LOGGER.info("  -> Preparing Protobuf hash upgrade test data...");
        UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(registryClient);
        LOGGER.info("  -> Preparing log compaction test data...");
        UpgradeTestsDataInitializer.prepareLogCompactionTests(registryClient);
        LOGGER.info("  -> Preparing Avro canonical hash upgrade test data...");
        UpgradeTestsDataInitializer.prepareAvroCanonicalHashUpgradeData(registryClient);
        
        LOGGER.info("KafkaSQL migration test data preparation completed");
    }

    private static void prepareKafkaSqlReferencesMigrationData(String registryBaseUrl) throws Exception {
        LOGGER.info("  -> Preparing artifact references migration test data for KafkaSQL Registry v2.4...");
        LOGGER.info("  -> Registry Base URL: {}", registryBaseUrl);
        
        var registryClient = RegistryClientFactory.create(registryBaseUrl);

        UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(registryClient);
        LOGGER.info("  -> Artifact references migration test data preparation completed");
    }
}
