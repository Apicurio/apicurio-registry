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

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static io.apicurio.registry.utils.tests.TestUtils.getRegistryBaseUrl;
import static io.apicurio.registry.utils.tests.TestUtils.getRegistryV2ApiUrl;

public abstract class AbstractTestDataInitializer implements QuarkusTestResourceLifecycleManager {

    GenericContainer registryContainer;
    String registryUrl;
    String registryBaseUrl;

    @Override
    public int order() {
        return 10000;
    }

    @Override
    public Map<String, String> start() {
        startRegistryApplication("quay.io/apicurio/apicurio-registry-mem:2.4.14.Final");
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
        int hostPort = 8081;
        int containerExposedPort = 8081;
        Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(containerExposedPort)));

        registryContainer = new GenericContainer<>(imageName)
                .withEnv(Map.of(
                        "QUARKUS_HTTP_PORT", "8081",
                        "REGISTRY_APIS_V2_DATE_FORMAT","yyyy-MM-dd'T'HH:mm:ss'Z'"))
                .withExposedPorts(containerExposedPort)
                .withCreateContainerCmdModifier(cmd);

        registryContainer.start();
        registryContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

        this.registryUrl = getRegistryV2ApiUrl(8081);
        this.registryBaseUrl = getRegistryBaseUrl(8081);

        return registryUrl;
    }

    public String getRegistryUrl(int port) {
        return getRegistryBaseUrl(port);
    }
}
