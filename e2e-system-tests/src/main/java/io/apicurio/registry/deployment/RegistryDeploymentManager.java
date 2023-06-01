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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RegistryDeploymentManager implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    private static final String KUBERNETES_IN_MEMORY_DEPLOYMENT = "in-memory-deployment.yml";
    private static final String KUBERNETES_INGRESS = "ingress.yml";
    private static final String KUBERNETES_SERVICE = "service.yml";

    private static final String NAMESPACE = "apicurio-registry";

    KubernetesClient kubernetesClient;

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (System.getProperty("quarkus.http.test-host") != null) {

            kubernetesClient = new KubernetesClientBuilder()
                    .build();

            try {
                Namespace namespace = new NamespaceBuilder().withNewMetadata().withName(NAMESPACE)
                        .endMetadata()
                        .build();

                LOGGER.info("Created namespace: {}", kubernetesClient.namespaces().resource(namespace).createOrReplace());

                Deployment deployment = kubernetesClient.apps()
                        .deployments()
                        .inNamespace(NAMESPACE)
                        .load(getClass().getResource(KUBERNETES_IN_MEMORY_DEPLOYMENT))
                        .createOrReplace();

                Service service = kubernetesClient.services()
                        .inNamespace(NAMESPACE)
                        .load(getClass().getResource(KUBERNETES_SERVICE))
                        .createOrReplace();

                HasMetadata ingress = kubernetesClient.resource(getClass().getResourceAsStream(KUBERNETES_INGRESS))
                        .inNamespace(NAMESPACE)
                        .createOrReplace();

                kubernetesClient.pods().inNamespace(NAMESPACE)
                        .waitUntilReady(30, TimeUnit.SECONDS);

            } catch (Exception e) {
                e.printStackTrace();
                if (kubernetesClient != null) {
                    kubernetesClient.namespaces()
                            .withName(NAMESPACE)
                            .delete();
                }
            }
            LOGGER.info("Test suite started ##################################################");
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Individual Test started ##################################################");
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Test suite ended ##################################################");

        LOGGER.info("Closing test resources ##################################################3");

        if (kubernetesClient != null) {

            kubernetesClient.namespaces().withName(NAMESPACE).delete();

            kubernetesClient.close();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Individual Test executed ##################################################");

    }

    @Override
    public void close() throws Throwable {

    }
}
