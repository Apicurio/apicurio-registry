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
import static io.apicurio.registry.utils.tests.TestUtils.getRegistryV3ApiUrl;

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
        // Once the data is set, stop the old registry before running the tests.
        if (registryContainer != null && registryContainer.isRunning()) {
            registryContainer.stop();
        }
    }

    public String startRegistryApplication(String imageName) {
        int hostPort = 8081;
        int containerExposedPort = 8081;
        Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(
                new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(containerExposedPort)));

        registryContainer = new GenericContainer<>(imageName)
                .withEnv(Map.of("QUARKUS_HTTP_PORT", "8081", "REGISTRY_APIS_V2_DATE_FORMAT",
                        "yyyy-MM-dd'T'HH:mm:ss'Z'"))
                .withExposedPorts(containerExposedPort).withCreateContainerCmdModifier(cmd);

        registryContainer.start();
        registryContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

        this.registryUrl = getRegistryV3ApiUrl(8081);
        this.registryBaseUrl = getRegistryBaseUrl(8081);

        return registryUrl;
    }

    public String getRegistryUrl(int port) {
        return getRegistryBaseUrl(port);
    }
}
