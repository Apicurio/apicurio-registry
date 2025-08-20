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
        LOGGER.info("=== KafkaSQL Deployment Manager ===");
        LOGGER.info("Registry Image: {}", registryImage);
        LOGGER.info("TEST_PROFILE: '{}' (from groups property: '{}')", Constants.TEST_PROFILE, System.getProperty("groups"));
        
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                LOGGER.info("Deploying KafkaSQL with AUTH profile");
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                LOGGER.info("Deploying KafkaSQL with MULTITENANCY profile");
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            case Constants.KAFKA_SQL:
                LOGGER.info("Deploying KafkaSQL with KAFKA_SQL profile (upgrade tests)");
                prepareKafkaDbUpgradeTests(registryImage);
                break;
            case Constants.KAFKASQL_MANUAL:
                LOGGER.warn("=== KAFKASQL_MANUAL Profile Detected ===");
                LOGGER.warn("The KAFKASQL_MANUAL profile does NOT deploy any infrastructure!");
                LOGGER.warn("This profile expects manual setup, but tests are running in cluster mode.");
                LOGGER.warn("This is likely the cause of the missing Kafka infrastructure.");
                LOGGER.warn("=======================================");
                
                // For cluster tests, we should still deploy infrastructure even in manual mode
                if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
                    LOGGER.info("Cluster tests detected - deploying KafkaSQL infrastructure despite KAFKASQL_MANUAL profile");
                    prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
                } else {
                    LOGGER.info("Local tests - skipping infrastructure deployment as expected for KAFKASQL_MANUAL profile");
                }
                break;
            default:
                LOGGER.info("Deploying KafkaSQL with DEFAULT profile");
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
                break;
        }
        
        LOGGER.info("=== KafkaSQL Deployment Manager Complete ===");
    }

    private static void prepareKafkaDbUpgradeTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for KafkaSQL DB Upgrade migration tests...");

        //For the migration tests first we deploy the 2.1 version and add the required data.
        prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_2_1_KAFKA_RESOURCES, false, null, false);
        prepareKafkaSqlMigrationData(ApicurioRegistryBaseIT.getRegistryBaseUrl());

        //Once all the data has been introduced, the old deployment is deleted.
        deleteRegistryDeployment();

        //The Registry version 2.3 is deployed, the version introducing artifact references.
        prepareTestsInfra(null, APPLICATION_2_4_KAFKA_RESOURCES, false, null, false);
        prepareKafkaSqlReferencesMigrationData(ApicurioRegistryBaseIT.getRegistryBaseUrl());

        //Once the references data is ready, we delete this old deployment and finally the current one is deployed.
        deleteRegistryDeployment();

        LOGGER.info("Finished preparing data for the KafkaSQL DB Upgrade tests.");
        prepareTestsInfra(null, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
    }

    private static void deleteRegistryDeployment() {
        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();

        //Wait for the deployment to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for namespace deletion", e);
        } finally {
            deployment.cancel(true);
        }
    }

    private static void prepareKafkaSqlMigrationData(String registryBaseUrl) throws Exception {
        var registryClient = RegistryClientFactory.create(registryBaseUrl);

        UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(registryClient);
        UpgradeTestsDataInitializer.prepareLogCompactionTests(registryClient);
        UpgradeTestsDataInitializer.prepareAvroCanonicalHashUpgradeData(registryClient);
    }

    private static void prepareKafkaSqlReferencesMigrationData(String registryBaseUrl) throws Exception {
        var registryClient = RegistryClientFactory.create(registryBaseUrl);

        UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(registryClient);
    }
}
