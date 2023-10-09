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

package io.apicurio.tests.utils;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.Map;

public abstract class AbstractTestDataInitializer implements QuarkusTestResourceLifecycleManager {

    GenericContainer registryContainer;
    String registryUrl;

    @Override
    public int order() {
        return 10000;
    }

    @Override
    public Map<String, String> start() {
        startRegistryApplication("quay.io/apicurio/apicurio-registry-mem:latest-release");
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        //Once the data is set, stop the old registry before running the tests.
        if (registryContainer != null && registryContainer.isRunning()) {
            registryContainer.stop();
        }
    }

    public String startRegistryApplication(String imageName) {
        registryContainer = new GenericContainer<>(imageName)
                .withEnv(Map.of(
                        "QUARKUS_HTTP_PORT", "8081"))
                .withNetworkMode("host");

        registryContainer.start();
        registryContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

        this.registryUrl = "http://localhost:8081";

        return registryUrl;
    }

    public String getRegistryUrl() {
        return registryUrl;
    }
}
