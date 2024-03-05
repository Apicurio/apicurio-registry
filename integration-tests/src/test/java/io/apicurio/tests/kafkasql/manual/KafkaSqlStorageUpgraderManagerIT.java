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

package io.apicurio.tests.kafkasql.manual;

import io.apicurio.deployment.TestConfiguration;
import io.apicurio.deployment.manual.KafkaRunner;
import io.apicurio.deployment.manual.ProxyKafkaRunner;
import io.apicurio.deployment.manual.ProxyRegistryRunner;
import io.apicurio.deployment.manual.RegistryRunner;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;

import static io.apicurio.deployment.manual.ProxyRegistryRunner.createClusterOrJAR;
import static io.apicurio.tests.utils.AssertUtils.exactlyOne;
import static io.apicurio.tests.utils.AssertUtils.none;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This class is responsible for testing {@code io.apicurio.registry.storage.impl.kafkasql.upgrade.KafkaSqlUpgraderManager}
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.KAFKASQL_MANUAL)
public class KafkaSqlStorageUpgraderManagerIT implements TestSeparator, Constants {

    public static final int LOCK_TIMEOUT_SECONDS = 10;

    private long testTimeoutMultiplier = 1;

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


    private KafkaRunner kafka = new ProxyKafkaRunner();


    @BeforeAll
    protected void beforeAll() {
        if (TestConfiguration.isClusterTests()) {
            testTimeoutMultiplier = 3; // We need more time for Kubernetes
        }
    }


    @BeforeEach
    protected void beforeEach() {
        kafka.startAndWait();
    }


    /**
     * This is a happy path scenario with 3 nodes (without induced problems):
     * - Only one of the nodes can perform an upgrade
     * - We try to start them approx. at the same time
     * - We test that after an upgrade no node tries to upgrade again
     */
    @Test
    public void testUpgradeHappyPath() throws Exception {
        var node1 = createClusterOrJAR();
        var node2 = createClusterOrJAR();
        var node3 = createClusterOrJAR();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            // Start the nodes, no induced problems
            startExtended(node1, 1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
            startExtended(node2, 2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
            startExtended(node3, 3, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);

            // Wait on the nodes to start
            TestUtils.waitFor("Registry nodes have started", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStarted() && node2.isStarted() && node3.isStarted()
            );

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

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
            node1.stopAndWait();
            node2.stopAndWait();
            node3.stopAndWait();
        }
    }


    /**
     * In this scenario, we configure 2 of the nodes to fail during an upgrade, and one node that will start with a bit of delay:
     * - We test that the third node completes the upgrade
     * - We test that the failed upgraders release the lock
     */
    @Test
    public void testUpgradeFail() throws Exception {
        var node1 = createClusterOrJAR();
        var node2 = createClusterOrJAR();
        var node3 = createClusterOrJAR();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            // Start the nodes, first two will fail
            startExtended(node1, 1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, true, Duration.ZERO, REPORTER);
            startExtended(node2, 2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, true, Duration.ZERO, REPORTER);
            // Delay the third node
            startExtended(node3, 3, startingLine, kafka.getBootstrapServers(), Duration.ofSeconds(LOCK_TIMEOUT_SECONDS), false, Duration.ZERO, REPORTER);

            // Wait on the nodes to start
            TestUtils.waitFor("Registry nodes have started", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStarted() && node2.isStarted() && node3.isStarted()
            );

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

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
            node1.stopAndWait();
            node2.stopAndWait();
            node3.stopAndWait();
        }
    }


    /**
     * In this scenario, we configure 2 of the nodes to time out during an upgrade (i.e. the upgrader will block), and one node that will start with a bit of delay:
     * - We test that the third node completes the upgrade
     * - We test that the failed upgraders correctly handle the situation cause by the artificial delay
     */
    @Test
    public void testUpgradeDelay() throws Exception {
        var node1 = createClusterOrJAR();
        var node2 = createClusterOrJAR();
        var node3 = createClusterOrJAR();
        try {
            var startingLine = Instant.now().plusMillis(1000);

            // Start the nodes, first two will fail
            startExtended(node1, 1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ofSeconds(LOCK_TIMEOUT_SECONDS + 1), REPORTER);
            startExtended(node2, 2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ofSeconds(LOCK_TIMEOUT_SECONDS + 1), REPORTER);
            // Delay the third node
            startExtended(node3, 3, startingLine, kafka.getBootstrapServers(), Duration.ofSeconds(3 * LOCK_TIMEOUT_SECONDS), false, Duration.ZERO, REPORTER);

            // Wait on the nodes to start
            TestUtils.waitFor("Registry nodes have started", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStarted() && node2.isStarted() && node3.isStarted()
            );

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 9 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

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
            node1.stopAndWait();
            node2.stopAndWait();
            node3.stopAndWait();
        }
    }


    /**
     * In this scenario, we configure 2 of the nodes to be shut down after taking the lock, and one node that will start with a bit of delay:
     * - We test that the third node completes the upgrade
     */
    @Test
    public void testUpgradeCrash() throws Exception {
        var node1 = createClusterOrJAR();
        var node2 = createClusterOrJAR();
        var node3 = createClusterOrJAR();
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
            startExtended(node1, 1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, reporter);
            startExtended(node2, 2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, reporter);
            // Delay the third node
            startExtended(node3, 3, startingLine, kafka.getBootstrapServers(), Duration.ofSeconds(3 * LOCK_TIMEOUT_SECONDS), false, Duration.ZERO, reporter);

            // Wait on the nodes to start
            TestUtils.waitFor("Registry nodes have started", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStarted() && node2.isStarted() && node3.isStarted()
            );

            // Wait on the nodes to stop
            TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 9 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                    () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
            );

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
            node1.stopAndWait();
            node2.stopAndWait();
            node3.stopAndWait();
        }
    }


    private void verifyRestart(ProxyRegistryRunner node1, ProxyRegistryRunner node2, ProxyRegistryRunner node3) throws Exception {

        // Let's restart and make sure we upgraded
        node1.stopAndWait();
        node2.stopAndWait();
        node3.stopAndWait();

        var startingLine = Instant.now().plusMillis(1000);

        startExtended(node1, 1, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
        startExtended(node2, 2, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);
        startExtended(node3, 3, startingLine, kafka.getBootstrapServers(), Duration.ZERO, false, Duration.ZERO, REPORTER);

        // Wait on the nodes to start
        TestUtils.waitFor("Registry nodes have started", 3 * 1000, 6 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                () -> node1.isStarted() && node2.isStarted() && node3.isStarted()
        );

        // Wait on the nodes to stop
        TestUtils.waitFor("Registry nodes have stopped", 3 * 1000, 3 * LOCK_TIMEOUT_SECONDS * 1000 * testTimeoutMultiplier,
                () -> node1.isStopped() && node2.isStopped() && node3.isStopped()
        );

        assertEquals(true, node1.getReport().get("finished"));
        assertEquals(true, node2.getReport().get("finished"));
        assertEquals(true, node3.getReport().get("finished"));

        // Upgrade has already been performed
        none(n -> n.getReport().containsKey("wait-locked") && n.getReport().containsKey("upgrade-started")
                && n.getReport().containsKey("upgrade-success"), node1, node2, node3);
    }


    @AfterEach
    protected void afterEach() {
        kafka.stopAndWait();
    }


    private static void startExtended(ProxyRegistryRunner node, int nodeId, Instant startingLine, String bootstrapServers, Duration upgradeTestInitDelay, boolean upgradeTestFail, Duration upgradeTestDelay, BiConsumer<String, RegistryRunner> reporter) {
        node.start(nodeId, startingLine, null, bootstrapServers, List.of(
                "-Dregistry.kafkasql.upgrade-test-mode=true",
                String.format("-Dregistry.kafkasql.upgrade-test-init-delay=%sms", upgradeTestInitDelay.toMillis()),
                String.format("-Dregistry.kafkasql.upgrade-test-fail=%s", upgradeTestFail),
                String.format("-Dregistry.kafkasql.upgrade-test-delay=%sms", upgradeTestDelay.toMillis())
        ), reporter);
    }
}
