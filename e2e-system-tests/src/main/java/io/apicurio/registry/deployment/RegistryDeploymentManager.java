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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RegistryDeploymentManager implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    private static final String E2E_NAMESPACE_RESOURCE = "/e2e-namespace.yml";

    private static final String APPLICATION_IN_MEMORY_RESOURCES = "/in-memory/registry-in-memory.yml";
    private static final String APPLICATION_KAFKA_RESOURCES = "/kafka/registry-kafka.yml";

    private static final String KAFKA_RESOURCES = "/kafka/kafka.yml";

    private static final String TEST_NAMESPACE = "apicurio-registry-e2e"; //TODO try to use @KubernetesTest with the dynamic namespace
    private static final String APPLICATION_SERVICE = "apicurio-registry-service";

    KubernetesClient kubernetesClient;
    LocalPortForward localPortForward;

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            handleInfraDeployment();

            LOGGER.info("Test suite started ##################################################");
        }
    }

    private void handleInfraDeployment() {
        kubernetesClient = new KubernetesClientBuilder()
                .build();

        //First of all, create the namespace used for the test.
        kubernetesClient.load(getClass().getResourceAsStream(E2E_NAMESPACE_RESOURCE))
                .create();

        //Based on the configuration, dpeloy the appropriate variant
        if (Boolean.parseBoolean(System.getProperty("deployInMemory"))) {
            deployInMemoryApp();
        } else if (Boolean.parseBoolean(System.getProperty("deploySql"))) {
            deploySqlApp();
        } else if (Boolean.parseBoolean(System.getProperty("deployKafka"))) {
            deployKafkaApp();
        }

        //No matter the storage type, create port forward so the application is reachable from the tests
        localPortForward = kubernetesClient.services()
                .inNamespace(TEST_NAMESPACE)
                .withName(APPLICATION_SERVICE)
                .portForward(8080, 8080);
    }

    private void deployInMemoryApp() {
        //Deploy all the resources associated to the in-memory variant
        kubernetesClient.load(getClass().getResourceAsStream(APPLICATION_IN_MEMORY_RESOURCES))
                .create();

        //Wait for all the pods of the variant to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(30, TimeUnit.SECONDS);
    }

    private void deployKafkaApp() {
        //Deploy all the resources associated to kafka
        kubernetesClient.load(getClass().getResourceAsStream(KAFKA_RESOURCES))
                .create();

        //Wait for all the kafka pods to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(30, TimeUnit.SECONDS);

        //Deploy all the resources associated to the kafka variant
        kubernetesClient.load(getClass().getResourceAsStream(APPLICATION_KAFKA_RESOURCES))
                .create();

        //Wait for all the pods of the variant to be ready
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE).waitUntilReady(30, TimeUnit.SECONDS);
    }

    private void deploySqlApp() {

    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Test suite ended ##################################################");

        if (localPortForward != null) {
            localPortForward.close();
        }

        //Finally, once the testsuite is done, cleanup all the resources in the cluster
        if (kubernetesClient != null) {
            LOGGER.info("Closing test resources ##################################################");
            kubernetesClient.namespaces()
                    .withName(TEST_NAMESPACE)
                    .delete();

            kubernetesClient.close();
        }
    }
}