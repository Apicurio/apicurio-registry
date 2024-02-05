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

package io.apicurio.tests.dbupgrade;

import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import junit.framework.AssertionFailedError;
import lombok.Getter;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.redpanda.RedpandaContainer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * This class is responsible for testing {@code io.apicurio.registry.storage.impl.kafkasql.upgrade.KafkaSqlUpgraderManager}
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
public class KafkaSqlStorageUpgraderManagerIT implements TestSeparator, Constants {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlStorageUpgraderManagerIT.class);

    public static final int LOCK_TIMEOUT_SECONDS = 10;

    private static final BiConsumer<String, RegistryRunner> REPORTER = (line, node) -> {
        if (line.contains("We detected a significant time difference")) {
            node.getReport().put("time-slip-detected", true);
        }
        if (line.contains("State change: WAIT -> LOCKED")) {
            node.getReport().put("wait-locked", true);
        }
        if (line.contains("Performing an upgrade with (in order):")) {
            node.getReport().put("upgrade-started", true);
        }
        if (line.contains("Upgrader KafkaSqlTestUpgrader_ClientProxy ran out of time")) {
            node.getReport().put("upgrade-timeout", true);
            node.stop();
        }
        if (line.contains("State change: LOCKED -> FAILED")) {
            node.getReport().put("locked-failed", true);
        }
        if (line.contains("Upgrade failed")) {
            node.getReport().put("upgrade-failed", true);
        }
        if (line.contains("Upgrade finished successfully!")) {
            node.getReport().put("upgrade-success", true);
        }
        if (line.contains("WaitHeartbeatEmitter thread has started.")) {
            node.getReport().put("wait-heartbeat-started", true);
        }
        if (line.contains("WaitHeartbeatEmitter thread has stopped successfully.")) {
            node.getReport().put("wait-heartbeat-stopped", true);
        }
        if (line.contains("KafkaSQL storage bootstrapped in ")) {
            node.getReport().put("finished", true);
            node.stop();
        }
    };

    private RedpandaContainer kafka;


    @BeforeEach
    protected void beforeEach() {
        log.info("Starting the Kafka Test Container");
        kafka = new RedpandaContainer("docker.redpanda.com/vectorized/redpanda");
        kafka.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka")));
        kafka.start();
    }


    /**
     * This is a happy path scenario with 3 nodes (without induced problems):
     * - Only one of the nodes can perform an upgrade
     * - We try to start them approx. at the same time
     * - We test that after an upgrade no node tries to upgrade again
     */
    @Test
    public void testUpgradeHappyPath() throws Exception {
        if (!RegistryRunner.isSupported()) {
            log.warn("TESTS IN 'KafkaSqlStorageUpgraderManagerIT' COULD NOT RUN");
            return;
        }
        var node1 = new RegistryNode();
        var node2 = new RegistryNode();
        var node3 = new RegistryNode();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            // Start the nodes, no induced problems
            node1.start(1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
            node2.start(2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
            node3.start(3, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

            assertNull(node1.getError());
            assertNull(node2.getError());
            assertNull(node3.getError());
            assertEquals(true, node1.getReport().get("finished"));
            assertEquals(true, node2.getReport().get("finished"));
            assertEquals(true, node3.getReport().get("finished"));

            // Exactly one of the node performed the upgrade
            exactlyOne(n -> n.getReport().containsKey("wait-locked") && n.getReport().containsKey("upgrade-started")
                    && n.getReport().containsKey("upgrade-success"), node1, node2, node3);

            assertEquals(null, node1.getReport().get("time-slip-detected"));
            assertEquals(null, node2.getReport().get("time-slip-detected"));
            assertEquals(null, node3.getReport().get("time-slip-detected"));

            verifyRestart(node1, node2, node3);

        } finally {
            node1.stop();
            node2.stop();
            node3.stop();
        }
    }


    /**
     * In this scenario, we configure 2 of the nodes to fail during an upgrade, and one node that will start with a bit of delay:
     * - We test that the third node completes the upgrade
     * - We test that the failed upgraders release the lock
     */
    @Test
    public void testUpgradeFail() throws Exception {
        if (!RegistryRunner.isSupported()) {
            log.warn("TESTS IN 'KafkaSqlStorageUpgraderManagerIT' COULD NOT RUN");
            return;
        }
        var node1 = new RegistryNode();
        var node2 = new RegistryNode();
        var node3 = new RegistryNode();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            // Start the nodes, first two will fail
            node1.start(1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, true, Duration.ZERO, REPORTER);
            node2.start(2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, true, Duration.ZERO, REPORTER);
            // Delay the third node
            node3.start(3, startingLine, kafka.getBootstrapServers(), Duration.ofSeconds(LOCK_TIMEOUT_SECONDS), false, Duration.ZERO, REPORTER);

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

            // Nodes finished without errors
            assertNull(node1.getError());
            assertNull(node2.getError());
            assertNull(node3.getError());
            assertEquals(true, node1.getReport().get("finished"));
            assertEquals(true, node2.getReport().get("finished"));
            assertEquals(true, node3.getReport().get("finished"));

            // First two nodes acquired the lock and failed
            // Node 1
            assertEquals(null, node1.getReport().get("time-slip-detected"));
            assertEquals(true, node1.getReport().get("wait-locked"));
            assertEquals(true, node1.getReport().get("upgrade-started"));
            assertEquals(true, node1.getReport().get("locked-failed"));
            assertEquals(true, node1.getReport().get("upgrade-failed"));
            assertEquals(null, node1.getReport().get("upgrade-success"));
            if (node1.getReport().get("wait-heartbeat-started") == Boolean.TRUE) {
                assertEquals(true, node1.getReport().get("wait-heartbeat-stopped"));
            }
            // Node 2
            assertEquals(null, node2.getReport().get("time-slip-detected"));
            assertEquals(true, node2.getReport().get("wait-locked"));
            assertEquals(true, node2.getReport().get("upgrade-started"));
            assertEquals(true, node2.getReport().get("locked-failed"));
            assertEquals(true, node2.getReport().get("upgrade-failed"));
            assertEquals(null, node2.getReport().get("upgrade-success"));
            if (node2.getReport().get("wait-heartbeat-started") == Boolean.TRUE) {
                assertEquals(true, node2.getReport().get("wait-heartbeat-stopped"));
            }
            // Node 3 performed the upgrade successfully
            assertEquals(null, node3.getReport().get("time-slip-detected"));
            assertEquals(true, node3.getReport().get("wait-locked"));
            assertEquals(true, node3.getReport().get("upgrade-started"));
            assertEquals(null, node3.getReport().get("locked-failed"));
            assertEquals(null, node3.getReport().get("upgrade-failed"));
            assertEquals(true, node3.getReport().get("upgrade-success"));
            if (node3.getReport().get("wait-heartbeat-started") == Boolean.TRUE) {
                assertEquals(true, node3.getReport().get("wait-heartbeat-stopped"));
            }

            verifyRestart(node1, node2, node3);

        } finally {
            node1.stop();
            node2.stop();
            node3.stop();
        }
    }


    /**
     * In this scenario, we configure 2 of the nodes to time out during an upgrade (i.e. the upgrader will block), and one node that will start with a bit of delay:
     * - We test that the third node completes the upgrade
     * - We test that the failed upgraders correctly handle the situation cause by the artificial delay
     */
    @Test
    public void testUpgradeDelay() throws Exception {
        if (!RegistryRunner.isSupported()) {
            log.warn("TESTS IN 'KafkaSqlStorageUpgraderManagerIT' COULD NOT RUN");
            return;
        }
        var node1 = new RegistryNode();
        var node2 = new RegistryNode();
        var node3 = new RegistryNode();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            // Start the nodes, first two will fail
            node1.start(1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ofSeconds(LOCK_TIMEOUT_SECONDS + 1), REPORTER);
            node2.start(2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ofSeconds(LOCK_TIMEOUT_SECONDS + 1), REPORTER);
            // Delay the third node
            node3.start(3, startingLine, kafka.getBootstrapServers(), Duration.ofSeconds(3 * LOCK_TIMEOUT_SECONDS), false, Duration.ZERO, REPORTER);

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 9 * LOCK_TIMEOUT_SECONDS * 1000,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

            // Nodes finished without errors
            assertNull(node1.getError());
            assertNull(node2.getError());
            assertNull(node3.getError());
            assertEquals(null, node1.getReport().get("finished")); // We stopped the node
            assertEquals(null, node2.getReport().get("finished")); // We stopped the node
            assertEquals(true, node3.getReport().get("finished"));

            // First two nodes acquired the lock and failed
            // Node 1
            assertEquals(null, node1.getReport().get("time-slip-detected"));
            assertEquals(true, node1.getReport().get("wait-locked"));
            assertEquals(true, node1.getReport().get("upgrade-started"));
            assertEquals(null, node1.getReport().get("locked-failed"));
            assertEquals(null, node1.getReport().get("upgrade-failed"));
            assertEquals(true, node1.getReport().get("upgrade-timeout"));
            assertEquals(null, node1.getReport().get("upgrade-success"));
            // Node 2
            assertEquals(null, node2.getReport().get("time-slip-detected"));
            assertEquals(true, node2.getReport().get("wait-locked"));
            assertEquals(true, node2.getReport().get("upgrade-started"));
            assertEquals(null, node2.getReport().get("locked-failed"));
            assertEquals(null, node2.getReport().get("upgrade-failed"));
            assertEquals(true, node2.getReport().get("upgrade-timeout"));
            assertEquals(null, node2.getReport().get("upgrade-success"));
            // Node 3 performed the upgrade successfully
            assertEquals(null, node3.getReport().get("time-slip-detected"));
            assertEquals(true, node3.getReport().get("wait-locked"));
            assertEquals(true, node3.getReport().get("upgrade-started"));
            assertEquals(null, node3.getReport().get("locked-failed"));
            assertEquals(null, node3.getReport().get("upgrade-failed"));
            assertEquals(null, node3.getReport().get("upgrade-timeout"));
            assertEquals(true, node3.getReport().get("upgrade-success"));
            if (node3.getReport().get("wait-heartbeat-started") == Boolean.TRUE) {
                assertEquals(true, node3.getReport().get("wait-heartbeat-stopped"));
            }

            verifyRestart(node1, node2, node3);

        } finally {
            node1.stop();
            node2.stop();
            node3.stop();
        }
    }


    /**
     * In this scenario, we configure 2 of the nodes to be shut down after taking the lock, and one node that will start with a bit of delay:
     * - We test that the third node completes the upgrade
     */
    @Test
    public void testUpgradeCrash() throws Exception {
        if (!RegistryRunner.isSupported()) {
            log.warn("TESTS IN 'KafkaSqlStorageUpgraderManagerIT' COULD NOT RUN");
            return;
        }
        var node1 = new RegistryNode();
        var node2 = new RegistryNode();
        var node3 = new RegistryNode();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            BiConsumer<String, RegistryRunner> reporter = (line, node) -> {
                REPORTER.accept(line, node);
                if (line.contains("Performing an upgrade with (in order):")) {
                    if (node.getNodeId() != 3) {
                        node.stop(); // Crash intentionally while holding the lock
                    }
                }
            };

            // Start the nodes, first two will crash
            node1.start(1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, reporter);
            node2.start(2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, reporter);
            // Delay the third node
            node3.start(3, startingLine, kafka.getBootstrapServers(), Duration.ofSeconds(3 * LOCK_TIMEOUT_SECONDS), false, Duration.ZERO, reporter);

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 9 * LOCK_TIMEOUT_SECONDS * 1000,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

            // Nodes finished without errors
            assertNull(node1.getError());
            assertNull(node2.getError());
            assertNull(node3.getError());
            assertEquals(null, node1.getReport().get("finished")); // We stopped the node
            assertEquals(null, node2.getReport().get("finished")); // We stopped the node
            assertEquals(true, node3.getReport().get("finished"));

            // First two nodes acquired the lock and crashed
            // Node 1
            assertEquals(null, node1.getReport().get("time-slip-detected"));
            assertEquals(true, node1.getReport().get("wait-locked"));
            assertEquals(true, node1.getReport().get("upgrade-started"));
            assertEquals(null, node1.getReport().get("locked-failed"));
            assertEquals(null, node1.getReport().get("upgrade-failed"));
            assertEquals(null, node1.getReport().get("upgrade-timeout"));
            assertEquals(null, node1.getReport().get("upgrade-success"));
            // Node 2
            assertEquals(null, node2.getReport().get("time-slip-detected"));
            assertEquals(true, node2.getReport().get("wait-locked"));
            assertEquals(true, node2.getReport().get("upgrade-started"));
            assertEquals(null, node2.getReport().get("locked-failed"));
            assertEquals(null, node2.getReport().get("upgrade-failed"));
            assertEquals(null, node2.getReport().get("upgrade-timeout"));
            assertEquals(null, node2.getReport().get("upgrade-success"));
            // Node 3 performed the upgrade successfully
            assertEquals(null, node3.getReport().get("time-slip-detected"));
            assertEquals(true, node3.getReport().get("wait-locked"));
            assertEquals(true, node3.getReport().get("upgrade-started"));
            assertEquals(null, node3.getReport().get("locked-failed"));
            assertEquals(null, node3.getReport().get("upgrade-failed"));
            assertEquals(null, node3.getReport().get("upgrade-timeout"));
            assertEquals(true, node3.getReport().get("upgrade-success"));
            if (node3.getReport().get("wait-heartbeat-started") == Boolean.TRUE) {
                assertEquals(true, node3.getReport().get("wait-heartbeat-stopped"));
            }

            verifyRestart(node1, node2, node3);

        } finally {
            node1.stop();
            node2.stop();
            node3.stop();
        }
    }


    private void verifyRestart(RegistryNode node1, RegistryNode node2, RegistryNode node3) throws Exception {

        // Let's restart and make sure we upgraded
        node1.stop();
        node2.stop();
        node3.stop();

        var startingLine = Instant.now().plusMillis(1000);

        node1.start(1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
        node2.start(2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
        node3.start(3, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);

        // Wait on the nodes to stop
        TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 3 * LOCK_TIMEOUT_SECONDS * 1000,
                () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
        );

        assertNull(node1.getError());
        assertNull(node2.getError());
        assertNull(node3.getError());
        assertEquals(true, node1.getReport().get("finished"));
        assertEquals(true, node2.getReport().get("finished"));
        assertEquals(true, node3.getReport().get("finished"));

        // Upgrade has already been performed
        none(n -> n.getReport().containsKey("wait-locked") && n.getReport().containsKey("upgrade-started")
                && n.getReport().containsKey("upgrade-success"), node1, node2, node3);
    }


    @AfterEach
    protected void afterEach() {
        if (kafka != null) {
            kafka.stop();
        }
    }


    private static class RegistryNode extends RegistryRunner {

        public void start(int nodeId, Instant startingLine, String bootstrapServers, Duration upgradeTestInitDelay,
                          boolean upgradeTestFail, Duration upgradeTestDelay, BiConsumer<String, RegistryRunner> reporter) {
            var c = new ArrayList<String>();
            c.add("-Dregistry.kafkasql.upgrade-test-mode=true");
            c.add(String.format("-Dregistry.kafkasql.upgrade-test-init-delay=%sms", upgradeTestInitDelay.toMillis()));
            c.add(String.format("-Dregistry.kafkasql.upgrade-test-fail=%s", upgradeTestFail));
            c.add(String.format("-Dregistry.kafkasql.upgrade-test-delay=%sms", upgradeTestDelay.toMillis()));
            start(nodeId, startingLine, bootstrapServers, c, reporter);
        }
    }


    public static class RegistryRunner {

        private static final String KAFKASQL_REGISTRY_JAR_WORK_PATH = "../storage/kafkasql/target";
        private static final String KAFKASQL_REGISTRY_JAR_PATH = "apicurio-registry-storage-kafkasql-%s-runner.jar";
        private static final String PROJECT_VERSION = System.getProperty("project.version");

        private volatile Thread runner;
        private volatile boolean stop;
        @Getter
        private volatile int nodeId;
        @Getter
        private volatile Exception error;
        @Getter
        private Map<String, Object> report = new HashMap<>();

        public synchronized void start(int nodeId, Instant startingLine, String bootstrapServers, List<String> args, BiConsumer<String, RegistryRunner> reporter) {
            if (!isStopped()) {
                throw new IllegalStateException("Node is not stopped.");
            }
            this.nodeId = nodeId;
            stop = false;
            error = null;
            report = new HashMap<>();
            runner = new Thread(() -> {
                ProcessBuilder builder = new ProcessBuilder();
                builder.directory(new File(KAFKASQL_REGISTRY_JAR_WORK_PATH));
                builder.environment().put("REGISTRY_LOG_LEVEL", "DEBUG");
                var c = new ArrayList<String>();

                c.add("java");
                c.add(String.format("-Dquarkus.http.port=%s", 8780 + nodeId));
                c.add(String.format("-Dregistry.kafkasql.bootstrap.servers=%s", bootstrapServers));
                c.addAll(args);

                c.add("-jar");
                c.add(String.format(KAFKASQL_REGISTRY_JAR_PATH, PROJECT_VERSION));

                builder.command(c);
                builder.redirectErrorStream(true);
                Process process = null;
                while (!stop) {
                    try {
                        if (Instant.now().isAfter(startingLine)) {
                            process = builder.start();
                            break;
                        } else {
                            Thread.sleep(Duration.between(Instant.now(), startingLine).abs().toMillis());
                        }
                    } catch (IOException ex) {
                        log.error("Could not start Registry instance.", ex);
                        error = ex;
                        stop = true;
                    } catch (InterruptedException e) {
                        // stop?
                    }
                }
                if (process != null) {
                    if (!stop) {
                        log.info("Started process: '{}' with PID {} (node {}).", String.join(" ", c), process.pid(), nodeId);
                        try (Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
                            while (!stop) {
                                if (scanner.hasNextLine()) {
                                    var line = scanner.nextLine();
                                    LoggerFactory.getLogger("node " + nodeId).info(line);
                                    reporter.accept(line, this);
                                }
                            }
                        }
                    }
                    process.destroy();
                    stop = false;
                }
            });
            runner.start();
        }

        public boolean isStopped() {
            return runner == null || !runner.isAlive();
        }

        public void stop() {
            if (runner != null) {
                stop = true;
                runner.interrupt();
            }
        }

        static boolean isSupported() {
            return Files.exists(Path.of(KAFKASQL_REGISTRY_JAR_WORK_PATH, String.format(KAFKASQL_REGISTRY_JAR_PATH, PROJECT_VERSION)));
        }
    }

    @SafeVarargs
    private static <T> T exactlyOne(Function<T, Boolean> condition, T... items) {
        List<T> selected = new ArrayList<>();
        for (T item : items) {
            if (condition.apply(item)) {
                selected.add(item);
            }
        }
        if (selected.size() == 1) {
            return selected.get(0);
        } else {
            throw new AssertionFailedError("None or more than one item fulfilled the condition: " + selected);
        }
    }


    @SafeVarargs
    private static <T> void none(Function<T, Boolean> condition, T... items) {
        List<T> selected = new ArrayList<>();
        for (T item : items) {
            if (condition.apply(item)) {
                selected.add(item);
            }
        }
        if (selected.size() != 0) {
            throw new AssertionFailedError("One or more items fulfilled the condition: " + selected);
        }
    }
}