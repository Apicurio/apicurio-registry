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

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.impl.OpenShiftClientImpl;
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
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.E2E_NAMESPACE_RESOURCE;
import static io.apicurio.deployment.KubernetesTestResources.KEYCLOAK_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.REGISTRY_OPENSHIFT_ROUTE;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_DATABASE;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_OPENSHIFT_ROUTE;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;

public class RegistryDeploymentManager implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    static KubernetesClient kubernetesClient;
    static LocalPortForward registryPortForward;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            kubernetesClient = new KubernetesClientBuilder()
                    .build();

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

        //Finally, once the testsuite is done, cleanup all the resources in the cluster
        if (kubernetesClient != null && !(Boolean.parseBoolean(System.getProperty("preserveNamespace")))) {
            LOGGER.info("Closing test resources ##################################################");

            if (registryPortForward != null) {
                try {
                    registryPortForward.close();
                } catch (IOException e) {
                    LOGGER.warn("Error closing registry port forward", e);
                }
            }

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

    private void handleInfraDeployment() throws Exception {
        //First, create the namespace used for the test.
        kubernetesClient.load(getClass().getResourceAsStream(E2E_NAMESPACE_RESOURCE))
                .create();

        //Based on the configuration, deploy the appropriate variant
        if (Boolean.parseBoolean(System.getProperty("deployInMemory"))) {
            LOGGER.info("Deploying In Memory Registry Variant with image: {} ##################################################", System.getProperty("registry-in-memory-image"));
            InMemoryDeploymentManager.deployInMemoryApp(System.getProperty("registry-in-memory-image"));
        } else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            LOGGER.info("Deploying SQL Registry Variant with image: {} ##################################################", System.getProperty("registry-sql-image"));
            SqlDeploymentManager.deploySqlApp(System.getProperty("registry-sql-image"));
        } else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
            LOGGER.info("Deploying Kafka SQL Registry Variant with image: {} ##################################################", System.getProperty("registry-kafkasql-image"));
            KafkaSqlDeploymentManager.deployKafkaApp(System.getProperty("registry-kafkasql-image"));
        }
    }

    static void prepareTestsInfra(String externalResources, String registryResources, boolean startKeycloak, String
            registryImage, boolean startTenantManager) throws IOException {
        if (startKeycloak) {
            LOGGER.info("Deploying Keycloak resources ##################################################");
            deployResource(KEYCLOAK_RESOURCES);
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

        final InputStream resourceAsStream = RegistryDeploymentManager.class.getResourceAsStream(registryResources);

        assert resourceAsStream != null;

        String registryLoadedResources = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8.name());

        if (registryImage != null) {
            registryLoadedResources = registryLoadedResources.replace(REGISTRY_IMAGE, registryImage);
        }

        try {
            //Deploy all the resources associated to the registry variant
            kubernetesClient.load(IOUtils.toInputStream(registryLoadedResources, StandardCharsets.UTF_8.name()))
                    .create();
        } catch (Exception ex) {
            LOGGER.warn("Error creating registry resources:", ex);
        }

        //Wait for all the pods of the variant to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(360, TimeUnit.SECONDS);

        setupTestNetworking(startTenantManager);
    }

    private static void setupTestNetworking(boolean startTenantManager) {
        if (Constants.TEST_PROFILE.equals(Constants.UI)) {
            //In the UI tests we use a port forward to make the application available to the testsuite.
            registryPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(APPLICATION_SERVICE)
                    .portForward(8080, 8080);
        } else {

            //For openshift, a route to the application is created we use it to set up the networking needs.
            if (Boolean.parseBoolean(System.getProperty("openshift.resources"))) {

                OpenShiftClient openShiftClient = new OpenShiftClientImpl();

                try {
                    final Route registryRoute = openShiftClient.routes()
                            .load(RegistryDeploymentManager.class.getResourceAsStream(REGISTRY_OPENSHIFT_ROUTE))
                            .create();
                    System.setProperty("quarkus.http.test-host", registryRoute.getSpec().getHost());
                    System.setProperty("quarkus.http.test-port", "80");

                } catch (Exception ex) {
                    LOGGER.warn("The registry route already exists: ", ex);
                }

                try {
                    final Route tenantManagerRoute = openShiftClient.routes()
                            .load(RegistryDeploymentManager.class.getResourceAsStream(TENANT_MANAGER_OPENSHIFT_ROUTE))
                            .create();

                    System.setProperty("tenant.manager.external.endpoint", tenantManagerRoute.getSpec().getHost());
                } catch (Exception ex) {
                    LOGGER.warn("The tenant manger route already exists: ", ex);
                }

            } else {
                //If we're running the cluster tests but no external endpoint has been provided, set the value of the load balancer.
                if (System.getProperty("quarkus.http.test-host").equals("localhost")) {
                    System.setProperty("quarkus.http.test-host", kubernetesClient.services().inNamespace(TEST_NAMESPACE).withName(APPLICATION_SERVICE).get().getSpec().getClusterIP());
                }

                //If we're running the cluster tests but no external endpoint has been provided, set the value of the load balancer.
                if (startTenantManager && System.getProperty("tenant.manager.external.endpoint") == null) {
                    System.setProperty("tenant.manager.external.endpoint", kubernetesClient.services().inNamespace(TEST_NAMESPACE).withName(TENANT_MANAGER_SERVICE).get().getSpec().getClusterIP());
                }
            }
        }
    }

    private static void deployResource(String resource) {
        //Deploy all the resources associated to the external requirements
        kubernetesClient.load(RegistryDeploymentManager.class.getResourceAsStream(resource))
                .create();

        //Wait for all the external resources pods to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(60, TimeUnit.SECONDS);
    }
}