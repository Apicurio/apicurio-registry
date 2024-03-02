/*
 * Copyright 2024 Red Hat
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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static io.apicurio.deployment.KubernetesTestResources.KEYCLOAK_SERVICE;
import static io.apicurio.deployment.TestConfiguration.isClusterTests;
import static io.apicurio.deployment.k8s.PortForwardManager.startPortForward;
import static io.apicurio.deployment.k8s.PortForwardManager.stopPortForward;

public class KeycloakPortForwardExtension implements BeforeAllCallback, AfterAllCallback {


    @Override
    public void beforeAll(ExtensionContext context) {
        if (isClusterTests()) {
            if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
                startPortForward(KEYCLOAK_SERVICE, 8090);
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (isClusterTests()) {
            if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
                stopPortForward(8090); // TODO: Is this needed?
            }
        }
    }
}
