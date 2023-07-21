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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.TENANT_MANAGER_SERVICE;
import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;

public class PortForwardManager implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

    KubernetesClient kubernetesClient;
    static LocalPortForward registryPortForward;
    static LocalPortForward tenantManagerPortForward;

    private static final Logger logger = LoggerFactory.getLogger(PortForwardManager.class);

    public PortForwardManager() {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            kubernetesClient = new KubernetesClientBuilder()
                    .build();
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            startRegistryPortForward();

            if (Boolean.parseBoolean(System.getProperty("multitenancy.tests"))) {
                startTenantManagerPortForward();
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            if (registryPortForward != null) {
                registryPortForward.close();
            }

            if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
                if (tenantManagerPortForward != null) {
                    tenantManagerPortForward.close();
                }
            }
        }
    }

    private void startRegistryPortForward() {
        try {
            //No matter the storage type, create port forward so the application is reachable from the tests
            registryPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(APPLICATION_SERVICE)
                    .portForward(8080, 8080);
        } catch (IllegalStateException ex) {
            logger.warn("Error found forwarding registry port, the port forwarding might be running already, continuing...", ex);
        }
    }


    private void startTenantManagerPortForward() {
        try {
            //Create the tenant manager port forward so it's available for the deployment
            tenantManagerPortForward = kubernetesClient.services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(TENANT_MANAGER_SERVICE)
                    .portForward(8585, 8585);
        } catch (IllegalStateException ex) {
            logger.warn("Error found forwarding tenant manager port, the port forwarding might be running already, continuing...", ex);
        }
    }


    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (registryPortForward != null && registryPortForward.errorOccurred()) {
            registryPortForward.close();
            startRegistryPortForward();
        }

        if (tenantManagerPortForward != null && tenantManagerPortForward.errorOccurred()) {
            tenantManagerPortForward.close();
            startTenantManagerPortForward();
        }
    }
}
