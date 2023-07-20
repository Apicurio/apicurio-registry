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
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.SqlStorageUpgradeIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUser;
import io.apicurio.tests.multitenancy.TenantUserClient;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.deployment.KubernetesTestResources.*;

public class RegistryDeploymentManager implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    KubernetesClient kubernetesClient;
    LocalPortForward registryPortForward;
    LocalPortForward keycloakPortForward;
    LocalPortForward tenantManagerPortForward;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            try {
                handleInfraDeployment();
            } catch (Exception e) {
                LOGGER.error("Error starting registry deployment", e);
            }

            LOGGER.info("Test suite started ##################################################");
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.info("Test suite ended ##################################################");

        closePortForward(registryPortForward);
        closePortForward(keycloakPortForward);
        closePortForward(tenantManagerPortForward);

        //Finally, once the testsuite is done, cleanup all the resources in the cluster
        if (kubernetesClient != null && !(Boolean.parseBoolean(System.getProperty("preserveNamespace")))) {
            LOGGER.info("Closing test resources ##################################################");

            final Resource<Namespace> namespaceResource = kubernetesClient.namespaces()
                    .withName(TEST_NAMESPACE);

            namespaceResource.delete();

            // wait the namespace to be deleted
            CompletableFuture<List<Namespace>> namespace = namespaceResource
                    .informOnCondition(Collection::isEmpty);

            try {
                namespace.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                LOGGER.warn("Error waiting for namespace deletion", e);
            } finally {
                namespace.cancel(true);
            }
            kubernetesClient.close();
        }
    }

    private void closePortForward(LocalPortForward portForward) {
        if (portForward != null) {
            try {
                portForward.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing port forward", e);
            }
        }
    }

    private void handleInfraDeployment() throws Exception {
        kubernetesClient = new KubernetesClientBuilder()
                .build();

        //First, create the namespace used for the test.
        kubernetesClient.load(getClass().getResourceAsStream(E2E_NAMESPACE_RESOURCE))
                .create();

        //Based on the configuration, deploy the appropriate variant
        if (Boolean.parseBoolean(System.getProperty("deployInMemory"))) {
            LOGGER.info("Deploying In Memory Registry Variant with image: {} ##################################################", System.getProperty("registry-in-memory-image"));
            deployInMemoryApp(System.getProperty("registry-in-memory-image"));
        } else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            LOGGER.info("Deploying SQL Registry Variant with image: {} ##################################################", System.getProperty("registry-sql-image"));
            deploySqlApp(System.getProperty("registry-sql-image"));
        } else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
            LOGGER.info("Deploying Kafka SQL Registry Variant with image: {} ##################################################", System.getProperty("registry-kafkasql-image"));
            deployKafkaApp(System.getProperty("registry-kafkasql-image"));
        }
    }

    private void deployInMemoryApp(String registryImage) throws Exception {
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                prepareTestsInfra(null, APPLICATION_IN_MEMORY_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                prepareTestsInfra(null, APPLICATION_IN_MEMORY_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            default:
                prepareTestsInfra(null, APPLICATION_IN_MEMORY_RESOURCES, false, registryImage, false);
                break;
        }
    }

    private void deployKafkaApp(String registryImage) throws Exception {
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            case Constants.KAFKA_SQL:
                prepareKafkaDbUpgradeTests(registryImage);
                break;
            default:
                prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
                break;
        }
    }

    private void deploySqlApp(String registryImage) throws Exception {
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            case Constants.SQL:
                prepareSqlDbUpgradeTests(registryImage);
                break;
            default:
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_RESOURCES, false, registryImage, false);
                break;
        }
    }

    private void prepareTestsInfra(String externalResources, String registryResources, boolean startKeycloak, String
            registryImage, boolean startTenantManager) throws IOException {
        if (startKeycloak) {
            LOGGER.info("Deploying Keycloak resources ##################################################");

            deployResource(KEYCLOAK_RESOURCES);

            //Create the keycloak port forward so the tests can reach it to get tokens
            keycloakPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(KEYCLOAK_SERVICE)
                    .portForward(8090, 8090);
        }

        if (startTenantManager) {
            LOGGER.info("Deploying Tenant Manager resources ##################################################");

            deployResource(TENANT_MANAGER_DATABASE);
            deployResource(TENANT_MANAGER_RESOURCES);
        }

        if (externalResources != null) {
            LOGGER.info("Deploying external dependencies for Registry ##################################################");
            deployResource(externalResources);
        }

        final InputStream resourceAsStream = getClass().getResourceAsStream(registryResources);

        assert resourceAsStream != null;

        String registryLoadedResources = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8.name());

        if (registryImage != null) {
            registryLoadedResources = registryLoadedResources.replace(REGISTRY_IMAGE, registryImage);
        }

        //Deploy all the resources associated to the registry variant
        kubernetesClient.load(IOUtils.toInputStream(registryLoadedResources, StandardCharsets.UTF_8.name()))
                .create();

        //Wait for all the pods of the variant to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(60, TimeUnit.SECONDS);
    }

    private void deployResource(String resource) {
        //Deploy all the resources associated to the external requirements
        kubernetesClient.load(getClass().getResourceAsStream(resource))
                .create();

        //Wait for all the external resources pods to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(60, TimeUnit.SECONDS);
    }

    private void prepareSqlDbUpgradeTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for SQL DB Upgrade migration tests...");

        //For the migration tests first we deploy the in-memory variant, add some data and then the appropriate variant is deployed.
        prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_OLD_SQL_RESOURCES, false, null, true);

        //Create the tenant manager port forward so it's available for the deployment
        tenantManagerPortForward = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(TENANT_MANAGER_SERVICE)
                .portForward(8585, 8585);

        prepareSqlMigrationData(ApicurioRegistryBaseIT.getTenantManagerUrl(), ApicurioRegistryBaseIT.getRegistryBaseUrl(), tenantManagerPortForward);

        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient.services().inNamespace(TEST_NAMESPACE).withName(APPLICATION_SERVICE).delete();
        kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();

        // wait the namespace to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for namespace deletion", e);
        } finally {
            deployment.cancel(true);
        }

        //Once done preparing data, close the tenant manager port forwarding.
        tenantManagerPortForward.close();

        LOGGER.info("Finished preparing data for the SQL DB Upgrade tests.");
        prepareTestsInfra(null, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, false);
    }

    private void prepareKafkaDbUpgradeTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for KafkaSQL DB Upgrade migration tests...");

        //For the migration tests first we deploy the in-memory variant, add some data and then the appropriate variant is deployed.
        prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_OLD_KAFKA_RESOURCES, false, null, false);
        prepareKafkaSqlMigrationData(ApicurioRegistryBaseIT.getRegistryBaseUrl());

        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient.services().inNamespace(TEST_NAMESPACE).withName(APPLICATION_SERVICE).delete();
        kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();

        // wait the namespace to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for namespace deletion", e);
        } finally {
            deployment.cancel(true);
        }

        LOGGER.info("Finished preparing data for the KafkaSQL DB Upgrade tests.");
        prepareTestsInfra(null, APPLICATION_KAFKA_RESOURCES, false, registryImage, false);
    }

    private void prepareSqlMigrationData(String tenantManagerUrl, String registryBaseUrl, LocalPortForward tenantManagerPortForward) throws Exception {
        try (LocalPortForward ignored = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(APPLICATION_SERVICE)
                .portForward(8080, 8080)) {

            final TenantManagerClientImpl tenantManagerClient = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);

            //Warm up until the tenant manager is ready.
            TestUtils.retry(() -> tenantManagerClient.listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId));

            LOGGER.info("Tenant manager is ready, filling registry with test data...");

            UpgradeTestsDataInitializer.prepareTestStorageUpgrade(SqlStorageUpgradeIT.class.getSimpleName(), tenantManagerUrl, registryBaseUrl);

            TestUtils.waitFor("Waiting for tenant data to be available...", 3000, 180000, () -> tenantManagerClient.listTenants(TenantStatusValue.READY, 0, 51, SortOrder.asc, SortBy.tenantId).getCount() == 10);

            LOGGER.info("Done filling registry with test data...");

            MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
            TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
            final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

            //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
            UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(tenantUpgradeClient.client);
            UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(tenantUpgradeClient.client);

            SqlStorageUpgradeIT.upgradeTenantClient = tenantUpgradeClient.client;
        }
    }

    private void prepareKafkaSqlMigrationData(String registryBaseUrl) throws Exception {
        try (LocalPortForward ignored = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(APPLICATION_SERVICE)
                .portForward(8080, 8080)) {

            var registryClient = RegistryClientFactory.create(registryBaseUrl);

            UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(registryClient);
            UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(registryClient);
            UpgradeTestsDataInitializer.prepareLogCompactionTests(registryClient);

        }
    }
}