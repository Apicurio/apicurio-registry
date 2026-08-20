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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public abstract class AbstractRegistryInfra {

    private static final Logger log = LoggerFactory.getLogger(AbstractRegistryInfra.class);

    // KafkaSQL bootstrap involves sequential Kafka consumer polls at 5s intervals
    // plus SQL initialization. Configurable via -Dtest.extra.timeout.kafkasql-startup=<seconds>.
    private static final Duration KAFKASQL_STARTUP_TIMEOUT = Duration.ofSeconds(
            Integer.getInteger("test.extra.timeout.kafkasql-startup", 120));

    // The wait strategy matches the success line AND the known startup-failure lines,
    // so expected-failure tests resolve as soon as the registry rejects its config
    // instead of burning the full startup timeout. After the wait, the logs decide
    // the outcome.
    private static final String SUCCESS_LINE = "KafkaSQL storage bootstrapped in";
    private static final List<String> FAILURE_LINES = List.of(
            // KafkaAdminUtil.assertConfiguration: bad journal/snapshots/events topic config
            "To prevent accidental loss of data",
            // KafkaAdminUtil.verifyJournalTopicContents: v2 records in the journal topic
            "contains records from Apicurio Registry v2");
    private static final String STARTUP_OUTCOME_PATTERN =
            ".*(" + SUCCESS_LINE + "|" + String.join("|", FAILURE_LINES) + ").*";

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
                .waitingFor(Wait.forLogMessage(STARTUP_OUTCOME_PATTERN, 1)
                        .withStartupTimeout(KAFKASQL_STARTUP_TIMEOUT)
                );

        try {
            long startTime = System.currentTimeMillis();
            registryContainer.start();
            long elapsed = System.currentTimeMillis() - startTime;
            registryContainer.followOutput(new Slf4jLogConsumer(log).withPrefix(name));
            String logs = registryContainer.getLogs();
            if (FAILURE_LINES.stream().anyMatch(logs::contains)) {
                // Expected startup failure (validation rejected the config): the
                // container is up but the storage refused to bootstrap. Stop it so
                // the next test gets a clean slate.
                log.info("Container '{}' rejected its configuration after {} ms (expected for negative tests)", name, elapsed);
                registryContainer.stop();
                return false;
            }
            log.info("Container '{}' started in {} ms", name, elapsed);
            return true;
        } catch (ContainerLaunchException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                throw new AssertionError(
                        "Registry container '" + name + "' startup timed out after "
                                + KAFKASQL_STARTUP_TIMEOUT.toSeconds() + "s"
                                + " — this is an infrastructure issue, not a validation failure. "
                                + "Override with -Dtest.extra.timeout.kafkasql-startup=<seconds>",
                        ex);
            }
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
