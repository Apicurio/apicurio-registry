package io.apicurio.registry.utils.tests.infra;

import io.apicurio.registry.utils.tests.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Map;

public abstract class AbstractRegistryInfra {

    private static final Logger log = LoggerFactory.getLogger(AbstractRegistryInfra.class);

    private final String name;

    protected GenericContainer<?> registryContainer;

    protected AbstractRegistryInfra(String name) {
        this.name = name;
    }

    public boolean startRegistryContainer(String imageName, Map<String, String> env) {
        if (registryContainer != null && registryContainer.isRunning()) {
            throw new IllegalStateException("Container '" + name + "' is already running.");
        }

        registryContainer = new GenericContainer<>(imageName)
                .withEnv(env)
                .withNetwork(Network.SHARED)
                .withExposedPorts(8080)
                .waitingFor(Wait.forLogMessage(".*KafkaSQL storage bootstrapped in .* ms.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30))
                );

        try {
            registryContainer.start();
            registryContainer.followOutput(new Slf4jLogConsumer(log).withPrefix(name));
            return true;
        } catch (ContainerLaunchException ex) {
            log.error("Error starting {} container: {}", name, ex.getMessage(), ex);
            return false;
        }
    }

    public String getLogs() {
        return registryContainer.getLogs();
    }

    public String getRegistryV2ApiUrl() {
        return TestUtils.getRegistryV2ApiUrl(registryContainer.getMappedPort(8080));
    }

    public void stop() {
        if (registryContainer != null && registryContainer.isRunning()) {
            registryContainer.stop();
        }
    }
}
