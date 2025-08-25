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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_MULTITENANT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_SECURED_RESOURCES;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class InMemoryDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryDeploymentManager.class);

    static void deployInMemoryApp(String registryImage) throws Exception {
        LOGGER.info("Deploying In-Memory Registry variant with test profile: {}", Constants.TEST_PROFILE);
        
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                LOGGER.info("Configuring In-Memory Registry with authentication (Keycloak) enabled");
                prepareTestsInfra(null, APPLICATION_IN_MEMORY_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                LOGGER.info("Configuring In-Memory Registry with multitenancy support enabled");
                prepareTestsInfra(null, APPLICATION_IN_MEMORY_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            default:
                LOGGER.info("Configuring standard In-Memory Registry deployment");
                prepareTestsInfra(null, APPLICATION_IN_MEMORY_RESOURCES, false, registryImage, false);
                break;
        }
        LOGGER.info("In-Memory Registry variant deployment completed successfully");
    }
}
