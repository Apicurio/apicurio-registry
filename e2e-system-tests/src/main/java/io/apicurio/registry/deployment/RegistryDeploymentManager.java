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

package io.apicurio.registry.deployment;

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

import static io.apicurio.registry.deployment.Constants.REGISTRY_IMAGE;
import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_SECURED_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_KAFKA_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_KAFKA_SECURED_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_SQL_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_SQL_SECURED_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.DATABASE_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.E2E_NAMESPACE_RESOURCE;
import static io.apicurio.registry.deployment.KubernetesTestResources.KAFKA_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.KEYCLOAK_RESOURCES;
import static io.apicurio.registry.deployment.KubernetesTestResources.KEYCLOAK_SERVICE;
import static io.apicurio.registry.deployment.KubernetesTestResources.TEST_NAMESPACE;

public class RegistryDeploymentManager implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    KubernetesClient kubernetesClient;
    LocalPortForward registryPortForward;
    LocalPortForward keycloakPortForward;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            try {
                handleInfraDeployment();
            } catch (IOException e) {
                LOGGER.error("Error starting registry deployment", e);
            }

            LOGGER.info("Test suite started ##################################################");
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.info("Test suite ended ##################################################");

        if (registryPortForward != null) {
            try {
                registryPortForward.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing registry port forward", e);
            }
        }

        if (keycloakPortForward != null) {
            try {
                keycloakPortForward.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing keycloak port forward", e);
            }
        }

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

    private void handleInfraDeployment() throws IOException {
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


    private void deployInMemoryApp(String registryImage) throws IOException {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            startResources(null, APPLICATION_IN_MEMORY_SECURED_RESOURCES, true, registryImage);
        } else if (Constants.TEST_PROFILE.equals(Constants.DB_UPGRADE)) {

        } else {
            startResources(null, APPLICATION_IN_MEMORY_RESOURCES, false, registryImage);
        }
    }

    private void deployKafkaApp(String registryImage) throws IOException {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            startResources(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage);
        } else if (Constants.TEST_PROFILE.equals(Constants.DB_UPGRADE)) {

        } else {
            startResources(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        }

    }

    private void deploySqlApp(String registryImage) throws IOException {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            startResources(DATABASE_RESOURCES, APPLICATION_SQL_SECURED_RESOURCES, true, registryImage);
        } else if (Constants.TEST_PROFILE.equals(Constants.DB_UPGRADE)) {

        } else {
            startResources(DATABASE_RESOURCES, APPLICATION_SQL_RESOURCES, false, registryImage);
        }
    }

    private void startResources(String externalResources, String registryResources, boolean startKeycloak, String registryImage) throws IOException {
        if (startKeycloak) {
            //Deploy all the resources associated to the external requirements
            kubernetesClient.load(getClass().getResourceAsStream(KEYCLOAK_RESOURCES))
                    .create();

            //Wait for all the external resources pods to be ready
            kubernetesClient.pods()
                    .inNamespace(TEST_NAMESPACE).waitUntilReady(60, TimeUnit.SECONDS);

            //Create the keycloak port forward so the tests can reach it to get tokens
            keycloakPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(KEYCLOAK_SERVICE)
                    .portForward(8090, 8090);
        }

        if (externalResources != null) {
            //Deploy all the resources associated to the external requirements
            kubernetesClient.load(getClass().getResourceAsStream(externalResources))
                    .create();

            //Wait for all the external resources pods to be ready
            kubernetesClient.pods()
                    .inNamespace(TEST_NAMESPACE).waitUntilReady(60, TimeUnit.SECONDS);
        }

        final InputStream resourceAsStream = getClass().getResourceAsStream(registryResources);

        assert resourceAsStream != null;

        final String registryLoadedResources = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8.name());
        final String replacedRegistryResource = registryLoadedResources.replace(REGISTRY_IMAGE, registryImage);

        //Deploy all the resources associated to the registry variant
        kubernetesClient.load(IOUtils.toInputStream(replacedRegistryResource, StandardCharsets.UTF_8.name()))
                .create();

        //Wait for all the pods of the variant to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(60, TimeUnit.SECONDS);
    }
}