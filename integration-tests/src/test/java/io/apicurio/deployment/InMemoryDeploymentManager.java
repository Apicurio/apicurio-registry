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

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_MULTITENANT_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_IN_MEMORY_SECURED_RESOURCES;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class InMemoryDeploymentManager {

    static void deployInMemoryApp(String registryImage) throws Exception {
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
}
