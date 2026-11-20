package io.apicurio.registry.agents.inventory;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

/**
 * The same syncs, statuses and refusals on KafkaSQL storage.
 */
@QuarkusTest
@TestProfile(AgentInventoryKafkasqlTestProfile.class)
@WithKubernetesTestServer(crud = false)
public class AgentInventoryKafkaSqlTest extends AgentInventorySyncTest {
}
