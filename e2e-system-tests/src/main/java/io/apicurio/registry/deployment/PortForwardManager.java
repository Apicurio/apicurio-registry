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

import static io.apicurio.registry.deployment.KubernetesTestResources.APPLICATION_SERVICE;
import static io.apicurio.registry.deployment.KubernetesTestResources.TEST_NAMESPACE;

public class PortForwardManager implements BeforeAllCallback, AfterAllCallback {

    KubernetesClient kubernetesClient;
    LocalPortForward registryPortForward;

    public PortForwardManager() {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            kubernetesClient = new KubernetesClientBuilder()
                    .build();
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            //No matter the storage type, create port forward so the application is reachable from the tests
            registryPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(APPLICATION_SERVICE)
                    .portForward(8080, 8080);
        }

    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            if (registryPortForward != null) {
                registryPortForward.close();
            }
        }
    }
}
