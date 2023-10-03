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

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_SECURED_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KAFKA_RESOURCES;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class KafkaSqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSqlDeploymentManager.class);

    static void deployKafkaApp(String registryImage) throws Exception {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage);
        } else {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        }
    }
}
