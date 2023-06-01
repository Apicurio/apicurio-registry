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
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RegistryDeploymentManager implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDeploymentManager.class);

    private static final String KUBERNETES_IN_MEMORY_DEPLOYMENT = "in-memory.yml";
    private static final String IN_MEMORY_NAMESPACE = "apicurio-registry-e2e-in-memory";

    KubernetesClient kubernetesClient;

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (System.getProperty("quarkus.http.test-host") != null) {
            kubernetesClient = new KubernetesClientBuilder()
                    .build();

            try {
                kubernetesClient.load(getClass().getResourceAsStream(KUBERNETES_IN_MEMORY_DEPLOYMENT))
                        .create();

                kubernetesClient.pods().inNamespace(IN_MEMORY_NAMESPACE)
                        .waitUntilReady(30, TimeUnit.SECONDS);

            } catch (Exception e) {
                e.printStackTrace();
            }
            LOGGER.info("Test suite started ##################################################");
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("Test suite ended ##################################################");
        LOGGER.info("Closing test resources ##################################################");

        kubernetesClient.namespaces()
                .withName(IN_MEMORY_NAMESPACE)
                .delete();

        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
    }
}