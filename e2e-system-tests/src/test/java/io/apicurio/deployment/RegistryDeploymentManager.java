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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.utils.tests.TestUtils;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_DEPLOYMENT;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_MULTITENANT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_SECURED_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_MULTITENANT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_SECURED_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_OLD_SQL_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_SQL_MULTITENANT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_SQL_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_SQL_SECURED_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.DATABASE_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.E2E_NAMESPACE_RESOURCE;
import static io.apicurio.deployment.KubernetesTestResources.KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KEYCLOAK_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KEYCLOAK_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_DATABASE;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.tests.ApicurioRegistryBaseIT.getRegistryBaseUrl;

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
            deployInMemoryApp(System.getProperty("registry-in-memory-image"));
        } else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            deploySqlApp(System.getProperty("registry-sql-image"));
        } else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
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
            case Constants.DB_UPGRADE:
                throw new UnsupportedOperationException("Running db upgrade tests is not supported for the in memory storage variant");
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
            case Constants.DB_UPGRADE:
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
            case Constants.DB_UPGRADE:
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
            deployResource(KEYCLOAK_RESOURCES);

            //Create the keycloak port forward so the tests can reach it to get tokens
            keycloakPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(KEYCLOAK_SERVICE)
                    .portForward(8090, 8090);
        }

        if (startTenantManager) {
            deployResource(TENANT_MANAGER_DATABASE);
            deployResource(TENANT_MANAGER_RESOURCES);

            //Create the tenant manager port forward so it's available for the deployment
            keycloakPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(TENANT_MANAGER_SERVICE)
                    .portForward(8585, 8585);
        }

        if (externalResources != null) {
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

        //For the migration tests first we deploy the in-memory variant, add some data and then the appropriate variant is deployed.

        prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_OLD_SQL_RESOURCES, false, null, true);

        //TODO create port forward

        RegistryClient registryClient = RegistryClientFactory.create(getRegistryBaseUrl());

        //Warm up waiting for registry ready
        TestUtils.retry(() -> {
            registryClient.listArtifactsInGroup("null");
        });


        kubernetesClient.apps().deployments().withName(APPLICATION_DEPLOYMENT).delete();
        kubernetesClient.services().withName(APPLICATION_SERVICE).delete();


        prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, true);
    }

    private void prepareKafkaDbUpgradeTests(String registryImage) throws IOException {



        prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_MULTITENANT_RESOURCES, false, registryImage, true);
    }
}